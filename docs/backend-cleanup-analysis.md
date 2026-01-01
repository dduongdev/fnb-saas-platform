# Backend Cleanup Analysis Report

## 📋 Executive Summary

| Category | Count | Action |
|----------|-------|--------|
| **CORE** | 12 files | Keep - Không thay đổi |
| **LEGACY/DEAD** | 8 items | Deprecate/Remove sau khi frontend migrate hoàn toàn |
| **MISPLACED** | 5 items | Refactor - Di chuyển logic |
| **DUPLICATE** | 3 items | Consolidate - Gộp/Xóa code trùng |

**Session-Based Architecture Status**: 60% Complete
- ✅ `SessionService`, `SessionController`, `ServingSession` entity
- ⚠️ `OrderService`, `TableService` vẫn còn sử dụng pattern cũ (masterTable)
- ⚠️ `OrderController` expose API legacy song song với `SessionController`

---

## 1. CORE CODE (GIỮ NGUYÊN)

### 1.1 Session Module (MỚI - Reference Implementation)

| File | Lines | Purpose | Đánh giá |
|------|-------|---------|----------|
| `SessionService.java` | 450 | Business logic session-based | ✅ Clean, idiomatic |
| `SessionController.java` | 110 | REST API endpoints | ✅ RESTful design |
| `SessionRepository.java` | 48 | Data access layer | ✅ JPQL fetch queries |
| `ServingSession.java` | 117 | Entity + helpers | ✅ Single responsibility |

**Reasoning**: Đây là implementation mẫu cho session-based architecture. Code clean, tách bạch, có documentation tốt.

### 1.2 Payment Module

| File | Lines | Purpose | Đánh giá |
|------|-------|---------|----------|
| `PaymentService.java` | 70 | Create payment URL | ✅ Strategy pattern |
| `PaymentStrategyFactory.java` | - | Factory pattern | ✅ SOLID |
| `VnPayStrategy.java` | - | VNPay implementation | ✅ Isolated |
| `PaymentCallbackController.java` | 167 | IPN callback | ✅ Updated to `getPrimaryTable()` |

### 1.3 Supporting Modules

| File | Purpose |
|------|---------|
| `MenuService.java` | Product/Category CRUD |
| `TenantService.java` | Tenant management |
| `UserService.java` | User management |
| `ReportingService.java` | Report generation |

---

## 2. LEGACY/DEAD CODE (CẦN XÓA)

### 2.1 Deprecated Fields trong Entity

| Entity | Field | Status | Impact |
|--------|-------|--------|--------|
| `Order.java` | `table` | `@Deprecated` | Medium - Backend còn dùng |
| `DiningTable.java` | `masterTable` | `@Deprecated` | High - TableService dùng nhiều |
| `DiningTable.java` | `Status.SERVING` | Implicit deprecated | Low - Nên dùng OCCUPIED |

```java
// Order.java - Line 42
@Deprecated
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "table_id")
private DiningTable table;

// DiningTable.java - Line 32
@Deprecated
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "master_table_id")
private DiningTable masterTable;
```

**Action**: Giữ tạm cho backward compatibility. Schedule removal sau Sprint 3.

### 2.2 Legacy Methods trong OrderService

| Method | Lines | Vấn đề | Thay thế |
|--------|-------|--------|----------|
| `createOrGetSession()` | 50-82 | Dùng `getMasterTable()` pattern | `SessionService.openTable()` |
| `addItems()` | 85-120 | Trùng logic với SessionService | `SessionService.addItems()` |
| `cancelOrder()` | 152-175 | Gọi `tableService.splitTable()` | `SessionService.cancelSession()` |
| `transferOrder()` | 222-280 | Dùng `order.setTable()` deprecated | `SessionService.transferSession()` |

**Detailed Analysis - createOrGetSession():**
```java
// OrderService.java - Line 50-82
@Transactional
public Order createOrGetSession(Integer tableId) {
    DiningTable currentTable = tableRepository.findById(tableId)...;
    
    // ❌ LEGACY: Dùng masterTable pattern
    DiningTable masterTable = currentTable.getMasterTable() != null 
        ? currentTable.getMasterTable() 
        : currentTable;

    return orderRepository.findByTableIdAndStatusWithDetails(masterTable.getId(), Order.OrderStatus.OPEN)
            .orElseGet(() -> {
                // ❌ LEGACY: Set table trực tiếp, không qua Session
                Order newOrder = Order.builder()
                        .table(masterTable)  // Deprecated field
                        .status(Order.OrderStatus.OPEN)
                        ...
                        .build();
                
                // ❌ LEGACY: Dùng Status.SERVING (nên dùng OCCUPIED)
                updateTableStatus(masterTable, DiningTable.Status.SERVING);
                ...
            });
}
```

### 2.3 Legacy Methods trong TableService

| Method | Lines | Vấn đề | Thay thế |
|--------|-------|--------|----------|
| `mergeTables()` | 105-165 | Dùng `masterTable` pattern | `SessionService.mergeTables()` |
| `splitTable()` | 185-215 | Dùng `masterTable` + `Status.SERVING` | `SessionService.splitSession()` |
| `releaseTable()` | 220-250 | Dùng `masterTable` pattern | `SessionService.paySession()` / `.cancelSession()` |

**Detailed Analysis - mergeTables():**
```java
// TableService.java - Line 105-165
@Transactional
public void mergeTables(MergeTableRequest request) {
    DiningTable target = getTable(targetId);
    
    // ❌ LEGACY: finalMaster pattern - Session-based không cần
    DiningTable finalMaster = target.getMasterTable() != null 
        ? target.getMasterTable() 
        : target;

    // ❌ LEGACY: Dùng Status.SERVING
    boolean isAnyServing = finalMaster.getStatus() == DiningTable.Status.SERVING || ...;

    // ❌ LEGACY: Set masterTable relationship
    for (DiningTable t : allTablesToMove) {
        t.setMasterTable(finalMaster);  // Deprecated operation
        t.setStatus(newStatus);
    }
}
```

### 2.4 OrderRepository Legacy Queries

```java
// OrderRepository.java - Line 15-22
// ❌ LEGACY: Query theo table.id - Session-based dùng session.id
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.table " +  // Deprecated field
       "LEFT JOIN FETCH o.items i " +
       "WHERE o.table.id = :tableId AND o.status = :status")
Optional<Order> findByTableIdAndStatusWithDetails(@Param("tableId") Integer tableId, ...);
```

**Action**: Thêm method mới `findBySessionIdAndStatusWithDetails()` hoặc chuyển sang dùng `SessionRepository`.

### 2.5 TableRepository Legacy Queries

```java
// TableRepository.java - Line 14
// ❌ LEGACY: findByMasterTableId - Session-based dùng currentSession
List<DiningTable> findByMasterTableId(Integer masterId);
```

---

## 3. MISPLACED CODE (CẦN REFACTOR)

### 3.1 Table Status Management trong OrderService

**Vấn đề**: `OrderService` đang quản lý table status - vi phạm Single Responsibility.

```java
// OrderService.java - Line 310-315
private void updateTableStatus(DiningTable table, DiningTable.Status status) {
    if (table.getStatus() != status) {
        table.setStatus(status);
        tableRepository.save(table);  // ❌ OrderService không nên persist Table
    }
}
```

**Giải pháp**: 
- Di chuyển sang `TableService.updateStatus(tableId, status)` 
- Hoặc xóa hoàn toàn vì `SessionService` đã handle

### 3.2 Invoice Generation Logic

**Vấn đề**: Invoice logic trùng lặp ở cả `OrderService` và `SessionService`.

| File | Method | Lines |
|------|--------|-------|
| `OrderService.java` | `createInvoice()` | 282-308 |
| `SessionService.java` | `createInvoice()` | 425-460 |

**Giải pháp**: Tạo `InvoiceService` hoặc `InvoiceFactory` riêng.

### 3.3 Notification Logic

**Vấn đề**: Notification logic trùng lặp ở nhiều service.

| File | Methods |
|------|---------|
| `OrderService.java` | `notifyTableUpdate()`, `sendNotification()`, `notifyPaymentSuccess()` |
| `SessionService.java` | `notifySessionUpdate()`, `notifyTableUpdate()`, `sendNotification()` |

**Giải pháp**: Tạo `NotificationService` với các method:
```java
public interface NotificationService {
    void notifyTableListUpdate(String tenantId);
    void notifySessionUpdate(ServingSession session);
    void notifyOrderUpdate(Order order);
    void sendStaffNotification(String type, String content, DiningTable table);
}
```

### 3.4 Employee Lookup Logic

**Vấn đề**: `getCurrentStaff()` duplicate ở cả 2 service.

```java
// Duplicate code in OrderService.java (Line 355-370) và SessionService.java (Line 540-555)
private Employee getCurrentStaff() {
    try {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // ... same logic
    }
}
```

**Giải pháp**: Di chuyển sang `SecurityUtils.getCurrentEmployee()` hoặc inject qua `@AuthenticationPrincipal`.

---

## 4. DUPLICATE CODE (CẦN CONSOLIDATE)

### 4.1 OrderController vs SessionController

| Operation | OrderController | SessionController | Recommend |
|-----------|-----------------|-------------------|-----------|
| Get/Create Session | `GET /orders/table/{id}` | `GET /sessions/table/{id}` | Keep SessionController |
| Add Items | `POST /orders/table/{id}/items` | `POST /sessions/{id}/items` | Keep SessionController |
| Cancel | `POST /orders/{id}/cancel` | `POST /sessions/{id}/cancel` | Keep SessionController |
| Pay Cash | `POST /orders/{id}/pay/cash` | `POST /sessions/{id}/pay` | Keep SessionController |
| Transfer | `POST /orders/{id}/transfer` | `POST /sessions/{id}/transfer` | Keep SessionController |

**Action Plan**:
1. **Phase 1**: Mark `OrderController` methods as `@Deprecated`
2. **Phase 2**: Update frontend to use `SessionController` APIs (đã làm một phần)
3. **Phase 3**: Remove `OrderController` (sau khi verify frontend)

### 4.2 MergeTableRequest Redundancy

**Vấn đề**: `MergeTableRequest` được dùng cho cả:
- `TableService.mergeTables()` (legacy masterTable pattern)
- `SessionService.mergeTables()` (session-based pattern)

```java
// MergeTableRequest.java - Dùng chung cho cả 2 approach
public class MergeTableRequest {
    private Integer targetTableId;   // Legacy: for masterTable
    private List<Integer> sourceTableIds;
}

// SessionRequest.MergeTables - Session-based
public static class MergeTables {
    private List<Integer> tableIds;  // Tables to merge INTO session
}
```

**Action**: Chỉ giữ `SessionRequest.MergeTables`, xóa `MergeTableRequest` sau khi deprecate `TableService.mergeTables()`.

---

## 5. IMPACT ANALYSIS

### 5.1 Dependencies Graph

```
┌──────────────────────────────────────────────────────────────────┐
│                        CURRENT STATE                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│   PaymentCallbackController ──────┬──► OrderService               │
│          │                        │        │                      │
│          └──► TableService ◄──────┘        │                      │
│                    │                       │                      │
│                    ▼                       ▼                      │
│              [masterTable]           [table field]                │
│               (deprecated)           (deprecated)                 │
│                                                                   │
│   ═══════════════════════════════════════════════════════════    │
│                                                                   │
│   Frontend (POSPage) ──────────► SessionController                │
│          │                             │                          │
│          │                             ▼                          │
│          │                       SessionService                   │
│          │                             │                          │
│          │                             ▼                          │
│          │                    [currentSession]                    │
│          │                       (new field)                      │
│          │                                                        │
│          └──────► OrderController (LEGACY - còn dùng)             │
│                         │                                         │
│                         ▼                                         │
│                   OrderService                                    │
│                         │                                         │
│                         ▼                                         │
│                  [masterTable]                                    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 5.2 Risk Matrix

| Item | Remove Risk | Keep Risk | Recommendation |
|------|-------------|-----------|----------------|
| `Order.table` field | HIGH - Frontend có thể còn dùng | LOW - Backward compat | Keep 2 sprints |
| `DiningTable.masterTable` | MEDIUM - TableService dùng | LOW | Keep 1 sprint |
| `OrderController` | MEDIUM - Check FE calls | LOW | Deprecate ngay |
| `OrderService.createOrGetSession()` | LOW - Đã có replacement | MEDIUM - Duplicate logic | Deprecate ngay |
| `TableService.mergeTables()` | LOW - SessionService có | HIGH - Inconsistent state | Deprecate ngay |

---

## 6. RECOMMENDED ACTIONS

### 6.1 Immediate Actions (Sprint này)

1. **Add `@Deprecated` annotations** với Javadoc rõ ràng:
```java
/**
 * @deprecated Use {@link SessionService#openTable(SessionRequest.OpenSession)} instead.
 * This method will be removed in v2.0
 */
@Deprecated(since = "1.5", forRemoval = true)
public Order createOrGetSession(Integer tableId) { ... }
```

2. **Remove duplicate code**:
   - Extract `NotificationService`
   - Extract `SecurityUtils.getCurrentEmployee()`
   - Extract `InvoiceService`

3. **Update OrderRepository**:
```java
// Thêm method mới
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.session s " +
       "WHERE o.session.id = :sessionId AND o.status = :status")
Optional<Order> findBySessionIdAndStatusWithDetails(Long sessionId, OrderStatus status);
```

### 6.2 Next Sprint Actions

1. **Audit Frontend**: Đảm bảo không còn call `OrderController`
2. **Remove OrderController methods**: Sau khi FE confirm
3. **Simplify TableService**: Chỉ giữ CRUD + QR generation

### 6.3 Backlog Actions

1. **Remove deprecated fields**: `Order.table`, `DiningTable.masterTable`
2. **Remove `Status.SERVING`**: Thống nhất dùng `OCCUPIED`
3. **Database migration**: Drop `table_id` column from orders, `master_table_id` from tables

---

## 7. CODE METRICS

### Before Cleanup
| Module | Files | Lines | Complexity |
|--------|-------|-------|------------|
| POS | 15 | ~2,500 | High (duplicate logic) |
| Payment | 8 | ~600 | Medium |
| Total | 23 | ~3,100 | - |

### After Cleanup (Estimated)
| Module | Files | Lines | Complexity |
|--------|-------|-------|------------|
| POS | 12 | ~1,800 | Low |
| Payment | 8 | ~600 | Medium |
| Common | 3 | ~200 | Low (extracted services) |
| Total | 23 | ~2,600 | - |

**Reduction**: ~500 lines (-16%)

---

## 8. APPENDIX

### A. Files to Modify

| File | Action | Priority |
|------|--------|----------|
| `OrderService.java` | Add @Deprecated, extract helpers | P0 |
| `TableService.java` | Add @Deprecated, simplify | P0 |
| `OrderController.java` | Add @Deprecated | P0 |
| `OrderRepository.java` | Add session-based queries | P1 |
| `SessionService.java` | Use extracted services | P2 |

### B. New Files to Create

| File | Purpose |
|------|---------|
| `NotificationService.java` | WebSocket notifications |
| `InvoiceService.java` | Invoice generation |
| `SecurityUtils.java` | Auth helpers |

### C. Files to Delete (Later)

| File | Condition |
|------|-----------|
| `MergeTableRequest.java` | After TableService cleanup |
| `OrderController.java` | After FE migration complete |

---

*Generated: 2024*
*Author: Backend Architect Review*
*Version: 1.0*
