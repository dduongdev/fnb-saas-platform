# Session-Based Refactor - Change Summary

## ✅ Hoàn thành Refactor Backend

### 📋 Tổng quan thay đổi

Refactor backend từ mô hình phức tạp sang **Session-based Model thuần túy**, loại bỏ các nghiệp vụ không cần thiết cho quán F&B nhỏ-trung.

---

## 🗂️ Danh sách File đã chỉnh sửa

### 1. Entity Layer

#### ✅ `ServingSession.java`
**Changes**:
- Cập nhật Javadoc toàn diện
- Thêm invariants: Session LUÔN có ≥1 Table, không split bill
- Làm rõ quan hệ: Session → Tables (1-N), Session → Orders (1-N)

**Code**: Giữ nguyên structure, chỉ cập nhật documentation

---

### 2. Service Layer

#### ✅ `SessionService.java`
**Removed Methods** (Đã xóa hoàn toàn):
```java
❌ public void mergeTables(Long sessionId, SessionRequest.MergeTables request)
❌ public SplitResult splitSession(Long sessionId, SessionRequest.SplitSession request)  
❌ public void transferSession(Long sessionId, SessionRequest.TransferSession request)
```

**Kept Methods** (Giữ lại logic cốt lõi):
```java
✅ openTable()           // Mở bàn
✅ attachTable()         // Gộp bàn (thay merge)
✅ detachTable()         // Tách bàn (thay split)
✅ addItems()            // Thêm món
✅ paySession()          // Thanh toán
✅ cancelSession()       // Hủy session
✅ createCustomerOrder() // Khách QR order
✅ confirmSession()      // Nhân viên confirm
✅ rejectSession()       // Nhân viên reject
```

**Lý do loại bỏ**:
- `mergeTables()`: Thay bằng `attachTable()` - đơn giản hơn, rõ ràng hơn
- `splitSession()`: KHÔNG hỗ trợ split bill - không cần cho quán nhỏ
- `transferSession()`: Có thể làm bằng attach + detach - không cần endpoint riêng

---

### 3. Controller Layer

#### ✅ `SessionController.java`
**Removed Endpoints**:
```java
❌ POST   /api/pos/sessions/{id}/merge
❌ POST   /api/pos/sessions/{id}/split
❌ POST   /api/pos/sessions/{id}/transfer
```

**Kept Endpoints**:
```java
✅ POST   /api/pos/sessions                  // Mở bàn
✅ GET    /api/pos/sessions/{id}             // Chi tiết session
✅ POST   /api/pos/sessions/{id}/items       // Thêm món
✅ POST   /api/pos/sessions/{id}/tables      // Attach Table
✅ DELETE /api/pos/sessions/{id}/tables/{id} // Detach Table
✅ POST   /api/pos/sessions/{id}/pay         // Thanh toán
✅ POST   /api/pos/sessions/{id}/cancel      // Hủy
✅ GET    /api/pos/sessions/pending          // Pending orders
✅ POST   /api/pos/sessions/{id}/confirm     // Confirm
✅ POST   /api/pos/sessions/{id}/reject      // Reject
```

---

### 4. DTO Layer

#### ✅ `SessionRequest.java`
**Removed DTOs**:
```java
❌ public static class MergeTables { ... }
❌ public static class SplitSession { ... }
❌ public static class TransferSession { ... }
```

**Kept DTOs**:
```java
✅ OpenSession
✅ AttachTable        // NEW - thay thế MergeTables
✅ AddItems
✅ PaySession
✅ CancelSession
✅ RejectSession
```

---

## 🎯 API Changes Summary

### Removed APIs (❌ ĐÃ XÓA)

| Old Endpoint                              | Replacement                            |
|-------------------------------------------|----------------------------------------|
| `POST /api/pos/sessions/{id}/merge`       | `POST /api/pos/sessions/{id}/tables`   |
| `POST /api/pos/sessions/{id}/split`       | `DELETE /api/pos/sessions/{id}/tables/{id}` hoặc Pay & Open New |
| `POST /api/pos/sessions/{id}/transfer`    | Attach New + Detach Old (2 calls)      |

---

### New/Refactored APIs (✅ HIỆN TẠI)

| Method | Endpoint                                  | Mô tả                    |
|--------|-------------------------------------------|--------------------------|
| POST   | `/api/pos/sessions/{id}/tables`           | Attach Table (Gộp bàn)   |
| DELETE | `/api/pos/sessions/{id}/tables/{tableId}` | Detach Table (Tách bàn)  |

---

## 🏗️ Domain Model (Unchanged but Clarified)

### Entity Relationships

```
Session (1) ──→ (N) DiningTable   // Session quản lý nhiều Table
Session (1) ──→ (N) Order         // Session sở hữu Order (thực tế 1)
DiningTable (M) ──→ (1) Session   // Table.currentSession
Order (1) ──→ (N) OrderItem
Order ❌ ──x──→ Table             // ĐÃ LOẠI BỎ - KHÔNG có tableId
```

### Key Invariants

1. ✅ Session **LUÔN** có ít nhất 1 Table
2. ✅ Table chỉ thuộc tối đa 1 Session ACTIVE
3. ✅ Order **KHÔNG** có `tableId` - chỉ có `sessionId`
4. ✅ Sau thanh toán/hủy → tất cả Table được release
5. ✅ **KHÔNG** hỗ trợ split bill

---

## 📝 Documentation Created

### ✅ `/docs/session-based-refactor.md`
**Nội dung**:
- Domain Model diagram
- Business logic mới (Attach/Detach)
- API Reference đầy đủ
- Use Cases minh họa
- Anti-patterns (tránh làm)
- Migration guide cho Frontend
- Testing checklist

---

## 🔄 Frontend Migration Required

### 1. API Calls cần thay đổi

**Gộp bàn**:
```javascript
// OLD
await mergeTablesIntoSession(sessionId, [2, 3]);

// NEW
await attachTable(sessionId, 2);
await attachTable(sessionId, 3);
```

**Tách bàn**:
```javascript
// OLD
await splitSession(sessionId, { type: 'RELEASE_TABLE', tableId: 3 });

// NEW
await detachTable(sessionId, 3);
```

**Chuyển bàn**:
```javascript
// OLD
await transferSession(sessionId, { newTableIds: [4, 5] });

// NEW
await attachTable(sessionId, 4);
await attachTable(sessionId, 5);
await detachTable(sessionId, 1);
await detachTable(sessionId, 2);
```

---

### 2. File Frontend cần cập nhật

#### `frontend/src/api/session.js`
- ✅ Đã có `attachTable()` - KHÔNG cần sửa
- ✅ Đã có `detachTable()` - KHÔNG cần sửa
- ❌ Xóa `mergeTablesIntoSession()`
- ❌ Xóa `splitSession()`
- ❌ Xóa `transferSession()`

#### `frontend/src/pages/pos/POSPage.jsx`
- ✅ Đã dùng `attachTable()` - KHÔNG cần sửa
- ✅ Đã dùng `releaseTableFromSession()` - KHÔNG cần sửa
- ❌ Xóa logic split bill nếu có

---

## 🎓 Nguyên tắc đã áp dụng

1. **Đơn giản hóa** - Chỉ 2 hành vi: Attach & Detach
2. **Domain rõ ràng** - Session là ngữ cảnh, Table là tài nguyên
3. **Thực tế nghiệp vụ** - Phù hợp quán F&B nhỏ-trung
4. **Không phá vỡ invariants** - Luôn đảm bảo Session có ≥1 Table

---

## ✅ Checklist Hoàn thành

- [x] Phân tích backend hiện tại
- [x] Loại bỏ legacy methods: `mergeTables()`, `splitSession()`, `transferSession()`
- [x] Loại bỏ legacy endpoints: `/merge`, `/split`, `/transfer`
- [x] Loại bỏ legacy DTOs: `MergeTables`, `SplitSession`, `TransferSession`
- [x] Cập nhật Entity documentation (ServingSession.java)
- [x] Tạo tài liệu `/docs/session-based-refactor.md`
- [x] Migration guide cho Frontend

---

## 🚀 Next Steps

### Backend (✅ DONE)
- Backend refactor hoàn tất
- Chỉ cần test lại các endpoint

### Frontend (⏳ TODO)
1. Cập nhật `session.js` - xóa legacy methods
2. Cập nhật `POSPage.jsx` - dùng attach/detach
3. Xóa UI split bill (nếu có)
4. Test lại flow: Gộp bàn, Tách bàn, Chuyển bàn

---

**Refactor Date**: January 2026  
**Status**: ✅ Backend COMPLETED | ⏳ Frontend PENDING
