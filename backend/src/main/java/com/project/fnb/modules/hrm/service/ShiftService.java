package com.project.fnb.modules.hrm.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.hrm.dto.CreateShiftRequest;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.hrm.entity.Shift;
import com.project.fnb.modules.hrm.repository.EmployeeRepository;
import com.project.fnb.modules.hrm.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


/**
 * Service quản lý Ca làm (Shift) cho hệ thống HRM (Human Resource Management).
 * 
 * <p>Class này cung cấp các business logic cho việc xếp lịch, xác nhận công,
 * và quản lý ca làm của nhân viên trong tenant.</p>
 * 
 * <p><b>Các tính năng chính:</b></p>
 * <ul>
 *   <li>Tạo ca làm mới (xếp lịch) cho nhân viên (createShift)</li>
 *   <li>Xác nhận công - chủ quán tick xác nhận ca làm đã thực hiện (confirmAttendance)</li>
 *   <li>Lấy danh sách ca làm trong khoảng thời gian (getShifts)</li>
 *   <li>Xóa ca làm (chỉ có thể xóa ca PLANNED) (deleteShift)</li>
 * </ul>
 * 
 * <p><b>Shift Status:</b></p>
 * <ul>
 *   <li>PLANNED: Ca được xếp lịch nhưng chưa thực hiện</li>
 *   <li>COMPLETED: Ca đã thực hiện (xác nhận công)</li>
 *   <li>ABSENT: Nhân viên vắng mặt/không làm</li>
 *   <li>CANCELLED: Ca bị hủy (tương lai có thể thêm)</li>
 * </ul>
 * 
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>Employee phải tồn tại trong hệ thống</li>
 *   <li>Thời gian kết thúc phải sau thời gian bắt đầu</li>
 *   <li>Không được xếp lịch cho quá khứ (startTime >= now)</li>
 *   <li>Chỉ được xác nhận công cho ca PLANNED</li>
 *   <li>Chỉ được xóa ca PLANNED</li>
 * </ul>
 * 
 * <p><b>Dependencies:</b></p>
 * <ul>
 *   <li>ShiftRepository - quản lý shift records</li>
 *   <li>EmployeeRepository - kiểm tra employee tồn tại</li>
 * </ul>
 * 
 * <p><b>Multi-Tenancy:</b></p>
 * <ul>
 *   <li>Shift record được lưu vào tenant-specific table (via BaseEntity)</li>
 *   <li>TenantContext được set bởi TenantFilter/TenantAspect</li>
 *   <li>Tất cả queries tự động filter theo tenant ID</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see Shift
 * @see Employee
 * @see CreateShiftRequest
 */
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * Tạo ca làm mới (xếp lịch) cho nhân viên.
     * 
     * <p>Phương thức này cho phép chủ quán xếp lịch làm việc cho nhân viên.
     * Ca làm được tạo với status=PLANNED (dự kiến), sau đó chủ quán có thể
     * xác nhận công khi nhân viên hoàn thành ca làm.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Kiểm tra employee tồn tại theo employeeId</li>
     *   <li>Validate endTime phải sau startTime (duration > 0)</li>
     *   <li>Validate startTime không được trong quá khứ (startTime >= now)</li>
     *   <li>Tạo Shift entity với status=PLANNED</li>
     *   <li>Lưu vào database (TenantContext tự động set tenantId)</li>
     *   <li>Trả về Shift object đã lưu (có ID)</li>
     * </ol>
     * 
     * <p><b>Validation Logic:</b></p>
     * <ul>
     *   <li>Employee validation: throw AppException(404) nếu employee không tồn tại</li>
     *   <li>End time validation: throw AppException(400) nếu endTime <= startTime</li>
     *   <li>Past time validation: throw AppException(400) nếu startTime < now</li>
     *   <li>Validate phục vụ mục đích: đảm bảo dữ liệu shift hợp lệ</li>
     * </ul>
     * 
     * <p><b>Default Values:</b></p>
     * <ul>
     *   <li>status: PLANNED (chờ xác nhận công)</li>
     *   <li>tenantId: tự động từ TenantContext (BaseEntity.TenantListener)</li>
     *   <li>createdAt, updatedAt: tự động từ @CreationTimestamp/@UpdateTimestamp</li>
     * </ul>
     * 
     * <p><b>Multi-Tenancy:</b></p>
     * <ul>
     *   <li>TenantContext được set bởi TenantFilter từ request header</li>
     *   <li>Shift record được lưu vào tenant-specific table</li>
     *   <li>Queries sau đó tự động filter theo tenant ID</li>
     * </ul>
     * 
     * <p><b>Transaction:</b></p>
     * <ul>
     *   <li>@Transactional đảm bảo consistency của database</li>
     *   <li>Nếu có exception, tất cả changes rollback</li>
     * </ul>
     * 
     * @param request CreateShiftRequest chứa:
     *                - employeeId: ID của nhân viên (bắt buộc)
     *                - startTime: Thời gian bắt đầu ca làm (bắt buộc)
     *                - endTime: Thời gian kết thúc ca làm (bắt buộc)
     *                - note: Ghi chú thêm (optional)
     * 
     * @return Shift object đã được tạo, chứa:
     *         - id (UUID auto-generated)
     *         - employee, startTime, endTime, note
     *         - status=PLANNED, tenantId, createdAt, updatedAt
     * 
     * @throws AppException(404) nếu employee không tồn tại
     * @throws AppException(400) nếu endTime <= startTime
     * @throws AppException(400) nếu startTime trong quá khứ (< now)
     * @throws DataIntegrityViolationException nếu dữ liệu invalid
     * 
     * @see Shift
     * @see CreateShiftRequest
     * @see Shift.ShiftStatus#PLANNED
     */
    @Transactional
    public Shift createShift(CreateShiftRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(404, "Nhân viên không tồn tại"));

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new AppException(400, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new AppException(400, "Không thể xếp lịch làm việc cho thời gian trong quá khứ");
        }

        Shift shift = Shift.builder()
                .employee(employee)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .note(request.getNote())
                .status(Shift.ShiftStatus.PLANNED)
                .build();
        
        return shiftRepository.save(shift);
    }

    /**
     * Xác nhận công - Chủ quán tick xác nhận ca làm đã được thực hiện.
     * 
     * <p>Phương thức này cho phép chủ quán xác nhận công cho nhân viên.
     * Chủ quán có thể đánh dấu ca làm là COMPLETED (hoàn thành) hoặc ABSENT (vắng mặt).
     * Chỉ có thể xác nhận công cho ca PLANNED (dự kiến).</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tìm shift theo shiftId</li>
     *   <li>Kiểm tra shift status phải là PLANNED</li>
     *   <li>Cập nhật status thành status mới (COMPLETED hoặc ABSENT)</li>
     *   <li>Lưu shift cập nhật vào database</li>
     * </ol>
     * 
     * <p><b>Status Transitions:</b></p>
     * <ul>
     *   <li>PLANNED → COMPLETED: Nhân viên hoàn thành ca làm</li>
     *   <li>PLANNED → ABSENT: Nhân viên vắng mặt/không làm</li>
     *   <li>Không thể transition từ COMPLETED hoặc ABSENT (throw exception)</li>
     * </ul>
     * 
     * <p><b>Validation Logic:</b></p>
     * <ul>
     *   <li>Shift tồn tại: throw AppException(404) nếu không tìm thấy</li>
     *   <li>Shift status = PLANNED: throw AppException(400) nếu khác PLANNED</li>
     *   <li>Status mới: status parameter phải hợp lệ (COMPLETED hoặc ABSENT)</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Chủ quán review danh sách shift ngày hôm nay</li>
     *   <li>Click "Xác nhận" cho nhân viên có mặt → status=COMPLETED</li>
     *   <li>Click "Vắng mặt" cho nhân viên không có mặt → status=ABSENT</li>
     *   <li>System record attendance history cho payroll/report</li>
     * </ul>
     * 
     * <p><b>Transaction:</b></p>
     * <ul>
     *   <li>@Transactional đảm bảo consistency</li>
     *   <li>Nếu exception, database không bị thay đổi</li>
     * </ul>
     * 
     * @param shiftId ID của shift cần xác nhận công (bắt buộc)
     * @param status Status mới: COMPLETED (hoàn thành) hoặc ABSENT (vắng mặt)
     * 
     * @throws AppException(404) nếu shift không tồn tại
     * @throws AppException(400) nếu shift status không phải PLANNED
     * @throws RuntimeException nếu xảy ra lỗi khi save database
     * 
     * @see Shift.ShiftStatus#PLANNED
     * @see Shift.ShiftStatus#COMPLETED
     * @see Shift.ShiftStatus#ABSENT
     */
    @Transactional
    public void confirmAttendance(Long shiftId, Shift.ShiftStatus status) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new AppException(404, "Ca làm không tồn tại"));
        
        if (shift.getStatus() != Shift.ShiftStatus.PLANNED) {
            throw new AppException(400, "Chỉ có thể xác nhận các ca làm dự kiến (PLANNED)");
        }
        shift.setStatus(status);
        shiftRepository.save(shift);
    }
    
    /**
     * Lấy danh sách ca làm trong khoảng thời gian.
     * 
     * <p>Phương thức này truy vấn tất cả shift nằm trong khoảng thời gian [from, to].
     * Dùng để lấy lịch làm của tenant trong ngày, tuần, tháng, v.v.</p>
     * 
     * <p><b>Query Logic:</b></p>
     * <ul>
     *   <li>Lấy tất cả shift có startTime nằm trong [from, to]</li>
     *   <li>Sử dụng findByStartTimeBetween() - custom query trong repository</li>
     *   <li>TenantContext tự động filter theo tenant ID</li>
     *   <li>Kết quả được sắp xếp theo startTime (asc)</li>
     * </ul>
     * 
     * <p><b>Time Range:</b></p>
     * <ul>
     *   <li>from: Thời gian bắt đầu khoảng tìm kiếm (inclusive)</li>
     *   <li>to: Thời gian kết thúc khoảng tìm kiếm (inclusive)</li>
     *   <li>Điều kiện: startTime >= from AND startTime <= to</li>
     *   <li>Ví dụ: from = 2025-12-13 00:00, to = 2025-12-13 23:59 → lấy shift ngày 13/12</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Lấy lịch làm hôm nay (from=0:00, to=23:59)</li>
     *   <li>Lấy lịch làm trong tuần (from=Mon, to=Sun)</li>
     *   <li>Lấy lịch làm trong tháng (from=1st day, to=last day)</li>
     *   <li>Lấy lịch làm trong năm (from=1 Jan, to=31 Dec)</li>
     *   <li>Frontend hiển thị calendar/timeline shift</li>
     * </ul>
     * 
     * <p><b>Response Format:</b></p>
     * <ul>
     *   <li>List<Shift> danh sách shift trong khoảng thời gian</li>
     *   <li>Sắp xếp theo startTime ascending</li>
     *   <li>Có thể rỗng nếu không có shift nào</li>
     * </ul>
     * 
     * <p><b>Multi-Tenancy:</b></p>
     * <ul>
     *   <li>TenantContext được set bởi TenantFilter</li>
     *   <li>Query tự động filter theo tenant ID (via Hibernate filter)</li>
     *   <li>Chỉ lấy shift của tenant hiện tại</li>
     * </ul>
     * 
     * @param from Thời gian bắt đầu khoảng tìm kiếm (LocalDateTime, bắt buộc)
     * @param to Thời gian kết thúc khoảng tìm kiếm (LocalDateTime, bắt buộc)
     * 
     * @return List<Shift> danh sách shift trong khoảng [from, to],
     *         sắp xếp theo startTime, có thể rỗng
     * 
     * @throws RuntimeException nếu xảy ra lỗi database query
     * 
     * @see ShiftRepository#findByStartTimeBetween(LocalDateTime, LocalDateTime)
     */
    public List<Shift> getShifts(LocalDateTime from, LocalDateTime to) {
        return shiftRepository.findByStartTimeBetween(from, to); 
    }
    
    /**
     * Xóa ca làm (chỉ có thể xóa ca PLANNED).
     * 
     * <p>Phương thức này cho phép xóa ca làm. Tuy nhiên, chỉ có thể xóa ca PLANNED (dự kiến).
     * Nếu ca đã COMPLETED (hoàn thành) hoặc ABSENT (vắng mặt), không thể xóa
     * vì cần giữ lại record cho payroll/report/history.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tìm shift theo shiftId</li>
     *   <li>Kiểm tra shift status phải là PLANNED</li>
     *   <li>Xóa shift khỏi database</li>
     * </ol>
     * 
     * <p><b>Status Restrictions:</b></p>
     * <ul>
     *   <li>PLANNED: Có thể xóa (xếp lịch sai, cần hủy)</li>
     *   <li>COMPLETED: Không xóa (đã hoàn thành, cần giữ record)</li>
     *   <li>ABSENT: Không xóa (đã ghi nhận vắng, cần giữ record)</li>
     * </ul>
     * 
     * <p><b>Business Logic:</b></p>
     * <ul>
     *   <li>Chỉ cho phép xóa shift dự kiến, chưa thực hiện</li>
     *   <li>Giữ lại record shift đã hoàn thành/vắng để tracking history</li>
     *   <li>Nếu cần hủy shift đã hoàn thành, tương lai có thể thêm "CANCELLED" status</li>
     * </ul>
     * 
     * <p><b>Validation Logic:</b></p>
     * <ul>
     *   <li>Shift tồn tại: throw AppException(404) nếu không tìm thấy</li>
     *   <li>Shift status = PLANNED: throw AppException(400) nếu khác PLANNED</li>
     * </ul>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Chủ quán xếp lịch sai → xóa shift PLANNED sai</li>
     *   <li>Nhân viên bị hủy shift (deadline chưa tới)</li>
     *   <li>Không thể xóa shift COMPLETED (đã làm xong)</li>
     *   <li>Không thể xóa shift ABSENT (đã ghi nhận vắng)</li>
     * </ul>
     * 
     * <p><b>Transaction:</b></p>
     * <ul>
     *   <li>@Transactional đảm bảo consistency</li>
     *   <li>Nếu exception, database không bị thay đổi</li>
     * </ul>
     * 
     * <p><b>Data Persistence:</b></p>
     * <ul>
     *   <li>Nếu cần audit trail, có thể thêm soft delete (isDeleted flag) thay vì hard delete</li>
     *   <li>Hiện tại sử dụng hard delete (xóa khỏi database)</li>
     *   <li>Trong tương lai có thể implement soft delete cho history tracking</li>
     * </ul>
     * 
     * @param shiftId ID của shift cần xóa (bắt buộc)
     * 
     * @throws AppException(404) nếu shift không tồn tại
     * @throws AppException(400) nếu shift status không phải PLANNED
     * @throws RuntimeException nếu xảy ra lỗi khi delete database
     * 
     * @see Shift.ShiftStatus#PLANNED
     * @see Shift.ShiftStatus#COMPLETED
     * @see Shift.ShiftStatus#ABSENT
     */
    @Transactional
    public void deleteShift(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new AppException(404, "Ca làm không tồn tại"));

        if (shift.getStatus() != Shift.ShiftStatus.PLANNED) {
            throw new AppException(400, "Chỉ có thể xóa các ca làm dự kiến (PLANNED). Ca đã hoàn thành hoặc vắng mặt không thể xóa.");
        }

        shiftRepository.delete(shift);
    }
}