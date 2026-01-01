# F&B SaaS - Role & Feature Matrix

## Định nghĩa 3 Nhóm Người Dùng

### 1. PLATFORM (Super Admin)
- Vận hành toàn hệ thống
- Quản lý tenant
- Không liên quan nghiệp vụ gọi món

### 2. KHÁCH HÀNG - KHÔNG TÀI KHOẢN
- Truy cập bằng QR của session/bàn
- Không đăng nhập
- Quyền: Thêm/Xóa/Xem món

### 3. KHÁCH HÀNG - CÓ TÀI KHOẢN
- ⚠️ **LƯU Ý: VẪN LÀ KHÁCH, KHÔNG PHẢI NHÂN VIÊN**
- Đăng nhập để lưu lịch sử
- Quyền: GIỐNG khách không tài khoản
- Khác: Gắn userId, xem lịch sử gọi món

---

## Backend Endpoint Matrix

| Endpoint | Phương thức | Mục đích | PLATFORM | KHÁCH (No Account) | KHÁCH (Có Account) | Ghi chú |
|----------|-----------|---------|---------|-------|--------|---------|
| `/api/pos/sessions` | POST | Tạo session (mở bàn) | ❌ | ✅ | ✅ | Khách gọi khi vào bàn hoặc tạo session riêng |
| `/api/pos/sessions/{id}` | GET | Lấy session | ❌ | ✅ | ✅ | Lấy state ban đầu trước WebSocket |
| `/api/pos/sessions/table/{id}` | GET | Lấy session từ tableId | ❌ | ✅ | ✅ | Quét QR → auto-create |
| `/api/pos/sessions/{id}/items` | POST | Thêm món | ❌ | ✅ | ✅ | Khách gọi món |
| `/api/pos/sessions/{id}/items/{id}` | DELETE | Xóa món PENDING | ❌ | ✅ | ✅ | Khách xóa nếu còn PENDING |
| `/api/pos/sessions/{id}/items/{id}` | PATCH | Cập nhật số lượng | ❌ | ✅ | ✅ | Khách sửa lượng |
| `/api/pos/sessions/{id}/items/{id}/serve` | POST | **SERVE** (đánh dấu mang ra) | ❌ | ❌ | ❌ | **🔴 CHỈ NHÂN VIÊN** |
| `/api/pos/sessions/{id}/tables` | POST | Gộp bàn (attach) | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |
| `/api/pos/sessions/{id}/tables/{id}` | DELETE | Tách bàn (detach) | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |
| `/api/pos/sessions/{id}/pay` | POST | **Thanh toán** | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |
| `/api/pos/sessions/{id}/cancel` | POST | **Hủy session** | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |
| `/api/pos/sessions/pending` | GET | Danh sách pending | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |
| `/api/pos/sessions/{id}/confirm` | POST | **Xác nhận order** | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |
| `/api/pos/sessions/{id}/reject` | POST | **Từ chối order** | ❌ | ❌ | ❌ | 🔴 CHỈ NHÂN VIÊN |

---

## Khác biệt Khách Có & Không Tài Khoản

### Khách KHÔNG TÀI KHOẢN
```
GET /api/pos/sessions/table/{tableId}
↓
Auto-create session (status: PENDING hoặc ACTIVE tùy config)
↓
Thêm/Xóa/Xem món realtime
↓
Sau thanh toán → lịch sử KHÔNG lưu
```

### Khách CÓ TÀI KHOẢN
```
Login → Xem userId
↓
GET /api/pos/sessions/{id}
↓
Thêm món → gắn userId vào OrderItem
↓
Xóa/Xem → giống khách không tài khoản
↓
Sau thanh toán → lịch sử lưu trữ
```

**Điểm chính: Quyền hạn + UI GIỐNG NHAU. Khác ở metadata + lịch sử.**

---

## Security & Authorization

### Backend Validation
- ✅ Validate `tenantId` từ JWT/Context
- ✅ Validate ownership session (nếu cần)
- ❌ **KHÔNG check role "KHÁCH" bởi vì:**
  - Khách không cần đăng nhập (hoặc login nhưng vẫn là khách)
  - Backend chỉ kiểm tra: "Người này có được phép thao tác session này không?"
  - Qua sessionId là đủ

### Frontend Authorization
- Khách KHÔNG TÀI KHOẢN: Hiện `/session/:id?role=customer`
- Khách CÓ TÀI KHOẢN: Hiện `/session/:id?role=customer` + lịch sử
- Nhân viên: Hiện `/pos` (POS page) + `/session/:id?role=staff`

---

## Workflow Khách Gọi Món (Không Tài Khoản)

```
1. Quét QR (chứa tableId hoặc sessionId)
   ↓
2. Frontend gọi:
   GET /api/pos/sessions/table/{tableId}
   ↓ (auto-create nếu chưa có)
   ↓
3. Nhận SessionResponse + WebSocket topic
   ↓
4. Khách thêm món:
   POST /api/pos/sessions/{id}/items
   ↓
5. Realtime event: ORDER_ITEM_ADDED
   ↓
6. UI cập nhật → khách thấy món ở trạng thái PENDING
   ↓
7. Nhân viên confirm → realtime event: SESSION_UPDATED
   ↓
8. Khách thấy status ACTIVE
   ↓
9. Nhân viên serve: ORDER_ITEM_SERVED (realtime)
   ↓
10. Khách thấy món chuyển SERVED
   ↓
11. Nhân viên thanh toán → session COMPLETED
```

---

## Workflow Khách Gọi Món (Có Tài Khoản)

Giống như trên, nhưng:
- Đăng nhập trước
- `userId` gắn vào `OrderItem`
- Sau session kết thúc → xem lịch sử

---

## TODO: Frontend Implementation

### KHÁCH HÀNG
- [ ] Page `/session/:id?role=customer`
  - [ ] Hiển thị menu
  - [ ] Nút thêm món (modal chọn số lượng)
  - [ ] Danh sách PENDING / SERVED
  - [ ] Nút xóa PENDING
  - [ ] Realtime WebSocket
  - [ ] Hiển thị status session (PENDING/ACTIVE/COMPLETED)

- [ ] Page `/customer/history` (CHỈ khách có tài khoản)
  - [ ] Danh sách session đã hoàn tất
  - [ ] Filter, search
  - [ ] Chi tiết session

### PLATFORM
- [ ] Dashboard tenant
- [ ] Danh sách tenant
- [ ] Thống kê

---

## Kiến Trúc Backend Không Cần Thay Đổi

✅ SessionService:
- Validate tenantId đã có
- Validate item ownership đã có

✅ SessionController:
- Endpoint đã đầy đủ

✅ WebSocket:
- Topics `/topic/tenant/{id}/session/{id}` đã đúng
- Events ORDER_ITEM_ADDED/DELETED/SERVED đã có

---

## Summary

| Loại Người Dùng | Ghi chú | Status |
|---|---|---|
| **PLATFORM** | Không liên quan gọi món | ❌ Chưa có UI |
| **KHÁCH No Acc** | Core feature, đơn giản nhất | ⚠️ UI chưa hoàn thiện |
| **KHÁCH Has Acc** | = KHÁCH No Acc + userId + lịch sử | ❌ Chưa có UI |

