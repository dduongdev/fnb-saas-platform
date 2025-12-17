package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.MergeTableRequest;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TableService {

    /**
     * Service responsible for managing dining tables within a tenant scope.
     *
     * <p>Responsibilities:
     * - Create and persist dining tables.
     * - Provide read operations for frontend list views.
     * - Merge and split tables (grouping behaviour for multi-seat tables).
     * - Broadcast table state changes via WebSocket topics scoped by tenant id.
     *
     * <p>Notes:
     * - Methods that mutate state are transactional to ensure DB consistency across related updates.
     * - Concurrency conflicts (e.g. simultaneous merges) should be handled at a higher layer
     *   or by optimistic locking on entities if needed.
     */

    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Create a new dining table record.
     *
     * @param name human-readable table name shown on UI and printed on QR if enabled
     * @return a `TableDto` representing the newly created table
     * @apiNote persisted table default status is {@link com.project.fnb.modules.pos.entity.DiningTable.Status#AVAILABLE}
     * @transactional This operation is transactional and will commit the new table to the database.
     * @implNote this method triggers an async WebSocket notification by calling {@link #notifyTableUpdate()}.
     */
    @Transactional
    public TableDto createTable(String name) {
        DiningTable table = DiningTable.builder()
                .name(name)
                .status(DiningTable.Status.AVAILABLE)
                .build();
        
        DiningTable savedTable = tableRepository.save(table);
        // QR generation may be implemented in a later phase; current flow persists and notifies clients
        notifyTableUpdate();
        
        return mapToDto(savedTable);
    }

    // 2. Lấy danh sách bàn (Hiển thị UI)
    public List<TableDto> getTables() {
        return tableRepository.findAll(Sort.by("name")).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Merge multiple source tables into a single target/master table.
     *
     * <p>Behavior summary:
     * - `targetTableId` becomes the master (final owner) of the merged group.
     * - Each source table and any of its slave tables (if the source itself is a master)
     *   will be reparented to the final master.
     * - The resulting table group status is {@code SERVING} if any table in the
     *   group was {@code SERVING}; otherwise {@code AVAILABLE}.
     *
     * @param request contains `targetTableId` and a list of `sourceTableIds` to be merged
     * @throws AppException with status 400 when the request is invalid (for example when source list contains the target)
     * @transactional All updates are applied in a single transaction to maintain group integrity.
     * @implNote Duplicated source IDs are deduplicated via a `Set` before persisting. The method
     *           intentionally skips merging when a source equals the final master to avoid cycles.
     */
    @Transactional
    public void mergeTables(MergeTableRequest request) {
        Integer targetId = request.getTargetTableId();
        List<Integer> sourceIds = request.getSourceTableIds();

        // Basic validations
        if (sourceIds.contains(targetId)) {
            throw new AppException(400, "Danh sách bàn gộp không được chứa bàn đích.");
        }

        DiningTable target = getTable(targetId);
        
        // Resolve final master table for the target (if target is already a slave, use its master)
        DiningTable finalMaster = target.getMasterTable() != null ? target.getMasterTable() : target;

        // Collect all tables to move (sources + their slaves).
        // Use a Set to avoid duplicates if frontend sends duplicate IDs.
        Set<DiningTable> allTablesToMove = new HashSet<>();

        // Iterate over each source id and collect its table and possible slaves
        for (Integer sourceId : sourceIds) {
            DiningTable source = getTable(sourceId);

            // Validate: Không được gộp Master vào chính Slave của nó
            if (finalMaster.getId().equals(source.getId())) {
                continue; // Hoặc throw error tùy nghiệp vụ, ở đây ta skip
            }
            
            // If source is a slave of another table (and not the final master), it will be reparented
            // to the final master below.
            allTablesToMove.add(source);

            // [FLATTEN] Nếu source đang là Master của nhóm khác -> Kéo cả nhóm đó theo
            List<DiningTable> sourceSlaves = tableRepository.findByMasterTableId(source.getId());
            allTablesToMove.addAll(sourceSlaves);
        }

        // Determine the resulting group status. If any table is currently SERVING,
        // the whole group remains SERVING; otherwise it becomes AVAILABLE.
        boolean isAnyServing = finalMaster.getStatus() == DiningTable.Status.SERVING ||
                               allTablesToMove.stream().anyMatch(t -> t.getStatus() == DiningTable.Status.SERVING);

        DiningTable.Status newStatus = isAnyServing ? DiningTable.Status.SERVING : DiningTable.Status.AVAILABLE;

        // Apply status & parent updates
        finalMaster.setStatus(newStatus);
        
        for (DiningTable t : allTablesToMove) {
            t.setMasterTable(finalMaster);
            t.setStatus(newStatus);
        }

        // Persist master and moved tables in batch
        tableRepository.save(finalMaster);
        tableRepository.saveAll(allTablesToMove);

        notifyTableUpdate();
    }

    /**
     * Split a table out of a group or dissolve a master table group.
     *
     * <p>Two cases are supported:
     * <ol>
     *   <li>If the target table is a master (no parent) — dissolve the group and set all slaves to AVAILABLE.</li>
     *   <li>If the target table is a slave — detach it from its master and keep the master as-is.</li>
     * </ol>
     *
     * @param tableId id of the table to split
     * @throws AppException with status 400 when the table is currently {@code SERVING};
     *                      this prevents mid-service splits in the MVP implementation.
     * @transactional All updates are applied in a single transaction.
     */
    @Transactional
    public void splitTable(Integer tableId) {
        DiningTable table = getTable(tableId);

        if (table.getStatus() == DiningTable.Status.SERVING) {
            // For MVP we prevent splitting while serving to avoid inconsistent orders across tables.
            throw new AppException(400, "Bàn đang phục vụ. Vui lòng thanh toán trước khi tách bàn.");
        }

        if (table.getMasterTable() == null) {
            // Splitting a master — dissolve the group and free all slaves
            List<DiningTable> slaves = tableRepository.findByMasterTableId(tableId);
            for (DiningTable slave : slaves) {
                slave.setMasterTable(null);
                slave.setStatus(DiningTable.Status.AVAILABLE);
            }
            tableRepository.saveAll(slaves);
        } else {
            // Splitting a slave — detach from its master
            table.setMasterTable(null);
        }

        table.setStatus(DiningTable.Status.AVAILABLE);
        tableRepository.save(table);

        notifyTableUpdate();
    }

    @Transactional
    public void releaseTable(Integer tableId) {
        DiningTable table = getTable(tableId);

        // Logic: Reset trạng thái về AVAILABLE
        // Đồng thời tách nhóm (Split) để trả bàn về vị trí cũ đón khách mới
        
        // 1. Tìm Master (nếu là slave) hoặc chính nó
        DiningTable master = table.getMasterTable() != null ? table.getMasterTable() : table;
        
        // 2. Tìm tất cả đệ tử
        List<DiningTable> slaves = tableRepository.findByMasterTableId(master.getId());
        
        // 3. Reset toàn bộ
        master.setStatus(DiningTable.Status.AVAILABLE);
        // master.setMasterTable(null); // Bản thân nó đã null
        
        for (DiningTable slave : slaves) {
            slave.setStatus(DiningTable.Status.AVAILABLE);
            slave.setMasterTable(null); // Tách ra
        }

        tableRepository.save(master);
        tableRepository.saveAll(slaves);
        
        notifyTableUpdate();
    }

    private DiningTable getTable(Integer id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));
    }

    private TableDto mapToDto(DiningTable t) {
        return TableDto.builder()
                .id(t.getId())
                .name(t.getName())
                .status(t.getStatus())
                .masterId(t.getMasterTable() != null ? t.getMasterTable().getId() : null)
                .qrCodeUrl(t.getQrCodeUrl())
                .build();
    }

    private void notifyTableUpdate() {
        // Broadcast table list updates over WebSocket to a tenant-scoped topic.
        // Recommended topic pattern: `/topic/tenant/{tenantId}/tables` so frontends can
        // subscribe per-tenant. The frontend may further filter or group messages as needed.
        String tenantId = TenantContext.getTenantId();
        try {
            String topic = "/topic/tenant/" + tenantId + "/tables";
            List<TableDto> tables = getTables();
            messagingTemplate.convertAndSend(topic, tables);
        } catch (Exception e) {
            // Log lỗi nhưng không throw exception để transaction DB vẫn commit thành công
            log.error("Lỗi gửi socket: " + e.getMessage());
        }
    }
}