# Session-Based Refactor Documentation

> **Refactor Date**: January 2026  
> **Author**: Senior Backend Engineer  
> **Version**: 2.0 (Refactored)

---

## 📋 Tổng quan

Tài liệu này mô tả quá trình refactor backend hệ thống F&B SaaS từ mô hình phức tạp sang **Session-based Model thuần túy**, tập trung vào nghiệp vụ thực tế của quán ăn nhỏ - trung bình.

---

## 🎯 Mục tiêu Refactor

### Trước đây (Legacy Model)
- ❌ Logic phức tạp: merge tables, split session, transfer session
- ❌ Hỗ trợ split bill (chia tiền) - không cần thiết cho quán nhỏ
- ❌ Order có thể liên kết trực tiếp với Table
- ❌ Nhiều điểm rối logic, khó maintain

### Sau Refactor (Session-based Model)
- ✅ **Đơn giản hóa**: Chỉ 2 hành vi cốt lõi - Attach Table & Detach Table
- ✅ **Chuẩn hóa quan hệ**: Session → Order → Items; Session → Tables
- ✅ **Loại bỏ split bill**: Một session = một hóa đơn
- ✅ **Domain rõ ràng**: Table là tài nguyên vật lý, Session là ngữ cảnh phục vụ
- ✅ **Dễ vận hành**: Phù hợp với thực tế quán F&B

---

## 🏗️ Domain Model (Sau Refactor)

### Core Entities

```
┌─────────────────────────────────────────────────────────────────┐
│                      SERVING SESSION                             │
│  (Trung tâm của hệ thống - Đại diện cho một lần phục vụ)        │
├─────────────────────────────────────────────────────────────────┤
│  Fields:                                                         │
│  - id: Long                                                      │
│  - status: PENDING | ACTIVE | COMPLETED | CANCELLED             │
│  - startedAt, endedAt: LocalDateTime                            │
│  - guestCount: Integer                                           │
│  - note: String                                                  │
│  - createdBy: Employee (NULL nếu khách tự tạo qua QR)           │
├─────────────────────────────────────────────────────────────────┤
│  Relationships:                                                  │
│  - tables: List<DiningTable>   (1→N)                            │
│  - orders: List<Order>          (1→N, thực tế chỉ 1 order)      │
│                                                                  │
│  Invariants:                                                     │
│  ✓ LUÔN có ít nhất 1 Table                                      │
│  ✓ Chỉ có 1 Order chính (không split bill)                      │
│  ✓ Sau COMPLETED/CANCELLED → tất cả Table được release          │
└─────────────────────────────────────────────────────────────────┘
           │                              │
           ▼                              ▼
┌──────────────────────┐       ┌─────────────────────────────────┐
│   DINING TABLE       │       │           ORDER                  │
├──────────────────────┤       ├─────────────────────────────────┤
│ - id: Integer        │       │ - id: Long                       │
│ - name: String       │       │ - session: ServingSession        │
│ - status: AVAILABLE  │       │ - totalAmount: BigDecimal        │
│           OCCUPIED   │       │ - status: OPEN|COMPLETED|...     │
│           RESERVED   │       │ - paymentMethod: String          │
│ - qrCodeUrl: String  │       │ - createdBy: Employee            │
│ - currentSession     │       │ - items: List<OrderItem>         │
│   (M-1 to Session)   │       │                                  │
│                      │       │ NOTE: KHÔNG có tableId           │
└──────────────────────┘       └─────────────────────────────────┘
                                           │
                                           ▼
                              ┌─────────────────────────────────┐
                              │        ORDER ITEM                │
                              ├─────────────────────────────────┤
                              │ - id: Long                       │
                              │ - order: Order                   │
                              │ - product: Product               │
                              │ - quantity: Integer              │
                              │ - price: BigDecimal              │
                              │ - note: String                   │
                              │ - originalTable: DiningTable (*)  │
                              │   (*tracking bàn gọi món)        │
                              │ - createdBy: Employee            │
                              └─────────────────────────────────┘
```

### Quan hệ chính

| Từ          | Đến            | Quan hệ | Ý nghĩa                                          |
|-------------|----------------|---------|--------------------------------------------------|
| Session     | Order          | 1-N     | Session sở hữu Order (thực tế chỉ 1)             |
| Session     | DiningTable    | 1-N     | Session quản lý nhiều Table (gộp bàn)            |
| DiningTable | Session        | M-1     | Table thuộc về Session (currentSession)          |
| Order       | OrderItem      | 1-N     | Order chứa các món                               |
| Order       | ~~Table~~      | ❌      | **ĐÃ LOẠI BỎ** - Order KHÔNG liên kết trực tiếp |
| OrderItem   | DiningTable    | M-1     | Tracking bàn nào gọi món (originalTable)         |

---

## 🔄 Nghiệp vụ (Business Logic)

### 1️⃣ Mở bàn / Tạo Session

**Endpoint**: `POST /api/pos/sessions`

```java
// Request
{
  "tableId": 1,
  "guestCount": 4,
  "note": "Khách VIP"
}

// Logic
1. Kiểm tra Table có trống không (currentSession == null)
2. Tạo Session mới (status = ACTIVE)
3. Gán Table vào Session (table.currentSession = session)
4. Chuyển Table.status = OCCUPIED
5. Tạo Order mặc định cho Session
```

**Invariant**: Session phải có ít nhất 1 Table ngay khi tạo.

---

### 2️⃣ Attach Table (Gộp bàn)

**Endpoint**: `POST /api/pos/sessions/{sessionId}/tables`

```java
// Request
{
  "tableId": 3
}

// Logic
1. Kiểm tra Session đang ACTIVE
2. Kiểm tra Table đích có trống (isAvailable())
3. Gán table.currentSession = session
4. Chuyển table.status = OCCUPIED
5. Thêm table vào session.tables
```

**Use Case**: Khách cần thêm chỗ ngồi → Nhân viên gộp bàn trống vào session hiện tại.

**Lưu ý**: Đây là cách thay thế cho "Merge Tables" cũ, nhưng đơn giản hơn nhiều.

---

### 3️⃣ Detach Table (Tách bàn)

**Endpoint**: `DELETE /api/pos/sessions/{sessionId}/tables/{tableId}`

```java
// Logic
1. Kiểm tra Session đang ACTIVE
2. Kiểm tra Session còn > 1 bàn (không được tách bàn cuối cùng)
3. Gỡ table.currentSession = null
4. Chuyển table.status = AVAILABLE
5. Xóa table khỏi session.tables
```

**Use Case**: Một phần khách đã rời đi, trả bàn để phục vụ khách mới.

**Ràng buộc**: KHÔNG cho phép tách bàn cuối cùng → Session phải luôn có ít nhất 1 bàn.

---

### 4️⃣ Thanh toán (Pay Session)

**Endpoint**: `POST /api/pos/sessions/{sessionId}/pay`

```java
// Request
{
  "method": "CASH" // hoặc VNPAY, MOMO
}

// Logic
1. Đóng tất cả Order (status = COMPLETED)
2. Release TẤT CẢ Table:
   - table.currentSession = null
   - table.status = AVAILABLE
3. Đóng Session (status = COMPLETED, endedAt = now)
4. Tạo Invoice trả về
```

**Quan trọng**: Sau khi thanh toán, tất cả Table được giải phóng tự động. Đây là cách duy nhất để "tách bàn hoàn toàn".

---

### 5️⃣ Hủy Session (Cancel Session)

**Endpoint**: `POST /api/pos/sessions/{sessionId}/cancel`

```java
// Logic
1. Hủy tất cả Order (status = CANCELLED)
2. Release tất cả Table
3. Đóng Session (status = CANCELLED)
```

**Use Case**: Khách hủy đơn, hoặc order không hợp lệ.

---

## ❌ Nghiệp vụ ĐÃ LOẠI BỎ

### 1. Merge Tables (Gộp bàn)
- **Trước**: `POST /api/pos/sessions/{id}/merge` + DTO `MergeTables`
- **Sau**: Thay bằng `POST /api/pos/sessions/{id}/tables` (Attach Table)
- **Lý do**: Logic đơn giản hơn, rõ ràng hơn

### 2. Split Session (Tách session / Split Bill)
- **Trước**: `POST /api/pos/sessions/{id}/split` + DTO `SplitSession`
- **Sau**: **ĐÃ XÓA HOÀN TOÀN**
- **Lý do**: 
  - Không phù hợp với nghiệp vụ quán nhỏ-trung
  - Tăng độ phức tạp không cần thiết
  - Thay thế: Thanh toán xong → Mở session mới

### 3. Transfer Session (Chuyển bàn)
- **Trước**: `POST /api/pos/sessions/{id}/transfer` + DTO `TransferSession`
- **Sau**: **ĐÃ XÓA HOÀN TOÀN**
- **Lý do**: Có thể thay bằng sequence:
  1. `POST /api/pos/sessions/{id}/tables` (Attach bàn mới)
  2. `DELETE /api/pos/sessions/{id}/tables/{old}` (Detach bàn cũ)

---

## 📡 API Reference (Sau Refactor)

### Session Management

| Method | Endpoint                                 | Mô tả                                | Request Body                     |
|--------|------------------------------------------|--------------------------------------|----------------------------------|
| POST   | `/api/pos/sessions`                      | Mở bàn / Tạo session                 | `{ tableId, guestCount?, note? }`|
| GET    | `/api/pos/sessions/{id}`                 | Lấy chi tiết session                 | -                                |
| GET    | `/api/pos/sessions/table/{tableId}`      | Lấy/tạo session theo table           | -                                |
| POST   | `/api/pos/sessions/{id}/items`           | Thêm món vào session                 | `{ items: [{productId, qty}] }`  |
| POST   | `/api/pos/sessions/{id}/pay`             | Thanh toán và đóng session           | `{ method: CASH/VNPAY }`         |
| POST   | `/api/pos/sessions/{id}/cancel`          | Hủy session                          | `{ reason? }`                    |

### Table Management (Attach/Detach)

| Method | Endpoint                                       | Mô tả                   | Request Body        |
|--------|------------------------------------------------|-------------------------|---------------------|
| POST   | `/api/pos/sessions/{id}/tables`                | ✅ Attach Table (Gộp bàn)| `{ tableId }`       |
| DELETE | `/api/pos/sessions/{id}/tables/{tableId}`      | ✅ Detach Table (Tách bàn)| -                  |

### Pending Order (Customer QR)

| Method | Endpoint                                 | Mô tả                                |
|--------|------------------------------------------|--------------------------------------|
| GET    | `/api/pos/sessions/pending`              | Lấy danh sách pending sessions       |
| POST   | `/api/pos/sessions/{id}/confirm`         | Xác nhận session (PENDING→ACTIVE)    |
| POST   | `/api/pos/sessions/{id}/reject`          | Từ chối session (PENDING→CANCELLED)  |

### ❌ Removed Endpoints

| Endpoint (OLD)                          | Status      | Replacement                          |
|-----------------------------------------|-------------|--------------------------------------|
| `POST /api/pos/sessions/{id}/merge`     | ❌ REMOVED  | `POST /api/pos/sessions/{id}/tables` |
| `POST /api/pos/sessions/{id}/split`     | ❌ REMOVED  | Use Detach or Pay & Open New         |
| `POST /api/pos/sessions/{id}/transfer`  | ❌ REMOVED  | Attach New + Detach Old              |

---

## 🗂️ Code Changes Summary

### 1. Entity Changes

#### `ServingSession.java`
- ✅ Cập nhật Javadoc: Nhấn mạnh Session-based model, loại bỏ mention "split bill"
- ✅ Giữ nguyên relationships (Session → Orders, Session → Tables)
- ✅ Thêm invariants vào documentation

#### `Order.java`
- ✅ **ĐÃ ĐÚNG**: Không có tableId, chỉ có sessionId
- ✅ No changes needed

#### `DiningTable.java`
- ✅ **ĐÃ ĐÚNG**: Có currentSession (M-1 to Session)
- ✅ No changes needed

#### `OrderItem.java`
- ✅ Giữ `originalTable` để tracking món được gọi từ bàn nào
- ✅ Hữu ích khi có nhiều bàn gộp lại

---

### 2. Service Changes

#### `SessionService.java`

**✅ Kept (Core Logic)**:
- `openTable()` - Mở bàn
- `getSession()` - Lấy session
- `addItems()` - Thêm món
- `attachTable()` - ✅ **Attach Table (Gộp bàn)**
- `detachTable()` - ✅ **Detach Table (Tách bàn)**
- `paySession()` - Thanh toán
- `cancelSession()` - Hủy session
- `createCustomerOrder()` - Khách tạo pending order
- `confirmSession()`, `rejectSession()` - Nhân viên duyệt order

**❌ Removed**:
```java
@Deprecated // Removed completely
public void mergeTables(Long sessionId, SessionRequest.MergeTables request)

@Deprecated // Removed completely
public SplitResult splitSession(Long sessionId, SessionRequest.SplitSession request)

@Deprecated // Removed completely
public void transferSession(Long sessionId, SessionRequest.TransferSession request)
```

**Lý do xóa**:
- `mergeTables()`: Thay bằng `attachTable()` - đơn giản hơn
- `splitSession()`: Không hỗ trợ split bill - không cần thiết
- `transferSession()`: Có thể làm bằng attach + detach - không cần endpoint riêng

---

### 3. Controller Changes

#### `SessionController.java`

**✅ Kept**:
```java
POST   /api/pos/sessions                     // Mở bàn
GET    /api/pos/sessions/{id}                // Lấy session
POST   /api/pos/sessions/{id}/items          // Thêm món
POST   /api/pos/sessions/{id}/tables         // ✅ Attach Table
DELETE /api/pos/sessions/{id}/tables/{id}    // ✅ Detach Table
POST   /api/pos/sessions/{id}/pay            // Thanh toán
POST   /api/pos/sessions/{id}/cancel         // Hủy
GET    /api/pos/sessions/pending             // Lấy pending orders
POST   /api/pos/sessions/{id}/confirm        // Confirm pending
POST   /api/pos/sessions/{id}/reject         // Reject pending
```

**❌ Removed**:
```java
@Deprecated @PostMapping("/{id}/merge")      // ❌ REMOVED
@Deprecated @PostMapping("/{id}/split")      // ❌ REMOVED
@Deprecated @PostMapping("/{id}/transfer")   // ❌ REMOVED
```

---

### 4. DTO Changes

#### `SessionRequest.java`

**✅ Kept**:
```java
public static class OpenSession { ... }
public static class AttachTable { ... }        // ✅ NEW - Thay thế MergeTables
public static class AddItems { ... }
public static class PaySession { ... }
public static class CancelSession { ... }
public static class RejectSession { ... }
```

**❌ Removed**:
```java
@Deprecated // REMOVED
public static class MergeTables { ... }

@Deprecated // REMOVED
public static class SplitSession { ... }

@Deprecated // REMOVED
public static class TransferSession { ... }
```

---

## 🔐 Invariants & Business Rules

### Session Invariants
1. ✅ Session LUÔN có ít nhất 1 Table
2. ✅ Table chỉ thuộc tối đa 1 Session ACTIVE
3. ✅ Sau COMPLETED/CANCELLED → tất cả Table được release
4. ✅ Không hỗ trợ split bill → 1 Session = 1 Hóa đơn

### Table Rules
1. ✅ Table trống: `currentSession == null && status == AVAILABLE`
2. ✅ Table đang bận: `currentSession != null && status == OCCUPIED`
3. ✅ Không thể attach Table đang có session khác
4. ✅ Không thể detach Table cuối cùng của session

### Order Rules
1. ✅ Order LUÔN thuộc về Session (KHÔNG trực tiếp vào Table)
2. ✅ OrderItem có thể track `originalTable` (bàn nào gọi món)
3. ✅ Tất cả Order trong Session được đóng cùng lúc khi Pay/Cancel

---

## 🎨 Use Cases (Minh họa)

### UC-1: Khách vào, mở bàn đơn
```
1. POST /api/pos/sessions { tableId: 1 }
   → Session (tables: [1], status: ACTIVE)
   → Table 1 (status: OCCUPIED)

2. POST /api/pos/sessions/{id}/items { items: [...] }
   → Thêm món

3. POST /api/pos/sessions/{id}/pay { method: CASH }
   → Session COMPLETED
   → Table 1 trở lại AVAILABLE
```

---

### UC-2: Gộp bàn
```
1. POST /api/pos/sessions { tableId: 1 }
   → Session (tables: [1])

2. POST /api/pos/sessions/{id}/tables { tableId: 2 }
   → Session (tables: [1, 2])  ✅ Attach
   → Table 2 (status: OCCUPIED)

3. POST /api/pos/sessions/{id}/tables { tableId: 3 }
   → Session (tables: [1, 2, 3])
```

---

### UC-3: Tách bàn (Một phần khách rời đi)
```
1. Session hiện tại: tables: [1, 2, 3]

2. DELETE /api/pos/sessions/{id}/tables/3
   → Session (tables: [1, 2])
   → Table 3 trở lại AVAILABLE  ✅ Detach

3. Khách còn lại tiếp tục dùng Session với bàn 1, 2
```

---

### UC-4: Khách quét QR gọi món
```
1. POST /api/pos/public/sessions { tableId: 5, items: [...] }
   → Session (status: PENDING)  // Chờ nhân viên confirm

2. Nhân viên xem: GET /api/pos/sessions/pending
   → Thấy order mới

3. POST /api/pos/sessions/{id}/confirm
   → Session (status: ACTIVE)
   → Table 5 (status: OCCUPIED)
```

---

## 🚫 Anti-Patterns (Tránh làm)

### ❌ KHÔNG làm: Split Bill
```
// BEFORE (Legacy - ĐÃ XÓA)
POST /api/pos/sessions/{id}/split
{
  "type": "SPLIT_SESSION",
  "tableIds": [2],
  "itemIds": [101, 102]  // Tách món 101, 102 sang session mới
}

// WHY REMOVED:
// - Phức tạp, khó maintain
// - Không phù hợp quán nhỏ-trung
// - Thay thế: Thanh toán xong → Mở session mới cho phần còn lại
```

---

### ❌ KHÔNG làm: Transfer phức tạp
```
// BEFORE (Legacy - ĐÃ XÓA)
POST /api/pos/sessions/{id}/transfer
{
  "newTableIds": [4, 5]  // Chuyển toàn bộ sang bàn 4, 5
}

// WHY REMOVED:
// - Có thể thay bằng: Attach new + Detach old
// - Không cần endpoint riêng
```

---

### ✅ ĐÚNG: Dùng Attach/Detach
```
// Chuyển từ bàn [1, 2] sang bàn [4, 5]:

// Step 1: Attach bàn mới
POST /api/pos/sessions/{id}/tables { tableId: 4 }
POST /api/pos/sessions/{id}/tables { tableId: 5 }

// Step 2: Detach bàn cũ
DELETE /api/pos/sessions/{id}/tables/1
DELETE /api/pos/sessions/{id}/tables/2

// Kết quả: Session (tables: [4, 5])
```

---

## 📊 Migration Guide (Frontend)

### Cần thay đổi trong Frontend

#### 1. Gộp bàn (Merge Tables)
**OLD**:
```javascript
await mergeTablesIntoSession(sessionId, [tableId1, tableId2]);
```

**NEW**:
```javascript
await attachTable(sessionId, tableId1);
await attachTable(sessionId, tableId2);
```

---

#### 2. Tách bàn (Split/Release Table)
**OLD**:
```javascript
await splitSession(sessionId, { 
  type: 'RELEASE_TABLE', 
  tableId: tableId 
});
```

**NEW**:
```javascript
await detachTable(sessionId, tableId);
```

---

#### 3. Chuyển bàn (Transfer)
**OLD**:
```javascript
await transferSession(sessionId, { newTableIds: [4, 5] });
```

**NEW**:
```javascript
// Attach new tables
await attachTable(sessionId, 4);
await attachTable(sessionId, 5);

// Detach old tables
await detachTable(sessionId, 1);
await detachTable(sessionId, 2);
```

---

#### 4. Xóa Split Bill UI
- ❌ Xóa toàn bộ UI "Tách món"
- ❌ Xóa logic chia order theo món
- ✅ Giữ lại: Thanh toán → Mở session mới

---

## 📝 Testing Checklist

### Unit Tests
- [x] `attachTable()` - Gộp bàn thành công
- [x] `attachTable()` - Bàn đang bận → Exception
- [x] `detachTable()` - Tách bàn thành công
- [x] `detachTable()` - Tách bàn cuối cùng → Exception
- [x] `paySession()` - Release all tables
- [x] `cancelSession()` - Release all tables

### Integration Tests
- [x] Flow: Mở bàn → Gộp bàn → Tách bàn → Thanh toán
- [x] Flow: QR Order → Pending → Confirm → Add Items → Pay
- [x] Edge case: Attach table đã có session → Fail
- [x] Edge case: Detach khi session chỉ có 1 bàn → Fail

---

## 🎓 Nguyên tắc thiết kế

### 1. Đơn giản hóa (Simplicity)
- ✅ 2 hành vi cốt lõi: Attach & Detach
- ✅ Loại bỏ logic không cần thiết
- ✅ Domain model rõ ràng

### 2. Thực tế nghiệp vụ (Business Reality)
- ✅ Phù hợp quán F&B nhỏ-trung
- ✅ Không cần split bill
- ✅ Thanh toán xong → Mở session mới

### 3. Không phá vỡ invariants
- ✅ Session LUÔN có ≥1 Table
- ✅ Table chỉ thuộc ≤1 Session ACTIVE
- ✅ Order KHÔNG trực tiếp vào Table

### 4. Backward Compatibility (Tùy chọn)
- Frontend cần migrate API
- Hoặc giữ lại deprecated endpoints (không khuyến khích)

---

## 📌 Kết luận

### Đạt được
- ✅ Domain model đơn giản, rõ ràng
- ✅ API dễ hiểu, dễ sử dụng
- ✅ Loại bỏ logic phức tạp không cần thiết
- ✅ Phù hợp nghiệp vụ thực tế

### Loại bỏ
- ❌ Split Bill (Tách món, chia tiền)
- ❌ Merge Tables endpoint (thay bằng Attach)
- ❌ Split Session endpoint
- ❌ Transfer Session endpoint

### Frontend cần làm
- 🔄 Migrate API calls (merge → attach, split → detach)
- 🔄 Xóa UI split bill
- 🔄 Update logic chuyển bàn

---

**END OF DOCUMENT**
