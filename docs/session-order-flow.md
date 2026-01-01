# Session Order - Nghiệp vụ Gọi món

## Tổng quan

Hệ thống quản lý quán F&B sử dụng mô hình **Session-based** cho việc gọi món. Một Session đại diện cho một lượt phục vụ - từ khi khách ngồi xuống đến khi thanh toán.

## Khái niệm chính

### Session (Phiên phục vụ)
- Có thể gắn nhiều bàn (gộp bàn)
- Khách và nhân viên cùng truy cập chung (qua QR code hoặc POS)
- Mọi thao tác được đồng bộ realtime

### Order & OrderItem
- Mỗi Session có 1 Order chính (không hỗ trợ split bill)
- Mỗi lần "thêm món" tạo 1 **OrderItem MỚI** (không cộng dồn)
- Kể cả cùng món, cùng trạng thái → vẫn là dòng riêng

## Trạng thái món (OrderItem.ItemStatus)

Chỉ có **2 trạng thái**:

| Status | Mô tả | Cho phép |
|--------|-------|----------|
| `PENDING` | Món đã gọi, chưa mang ra | Sửa số lượng, Xóa |
| `SERVED` | Món đã mang ra | Chỉ đọc |

**Không có trạng thái khác** (không có preparing, confirmed, cancelled cho item).

## Quy tắc nghiệp vụ

### 1. Thêm món
- Người dùng PHẢI chọn số lượng ngay khi thêm
- Luôn tạo OrderItem MỚI (không cộng dồn)
- Status mặc định: `PENDING`

### 2. Sửa số lượng
- Chỉ với món `PENDING`
- Số lượng mới >= 1
- Nếu muốn xóa hoàn toàn → dùng API xóa

### 3. Xóa món
- Chỉ với món `PENDING`
- Hard delete (xóa thật khỏi DB)
- Trả về 409 nếu món đã `SERVED`

### 4. Đánh dấu đã mang ra (Serve)
- Chỉ với món `PENDING`
- Chuyển sang `SERVED`
- Sau khi serve → không thể sửa/xóa

### 5. Thanh toán
- KHÔNG tự động thay đổi trạng thái OrderItem
- Chỉ đóng Session

## API Endpoints

### Session APIs

```
POST   /api/pos/sessions                              - Mở bàn (tạo session)
GET    /api/pos/sessions/{sessionId}                  - Lấy session theo ID
GET    /api/pos/sessions/table/{tableId}              - Lấy session theo bàn
GET    /api/pos/sessions/active                       - Danh sách session đang active
GET    /api/pos/sessions/pending                      - Danh sách session chờ xác nhận
POST   /api/pos/sessions/{sessionId}/tables           - Gộp bàn (attach table)
DELETE /api/pos/sessions/{sessionId}/tables/{tableId} - Tách bàn (detach table)
```

### Table Management Rules

| Thao tác | Session ACTIVE | Session COMPLETED | Điều kiện |
|----------|---------------|-------------------|-----------|
| Attach   | ✅ Cho phép   | ❌ Không cho      | - |
| Detach   | ✅ Cho phép   | ✅ Cho phép       | Session phải còn >= 1 bàn |

### Order Item APIs

```
POST   /api/pos/sessions/{sessionId}/items                    - Thêm món
DELETE /api/pos/sessions/{sessionId}/items/{itemId}           - Xóa món (chỉ PENDING)
PATCH  /api/pos/sessions/{sessionId}/items/{itemId}           - Cập nhật số lượng (chỉ PENDING)
POST   /api/pos/sessions/{sessionId}/items/{itemId}/serve     - Đánh dấu đã mang ra (chỉ PENDING)
```

### Request/Response

#### Thêm món
```json
// POST /api/pos/sessions/{sessionId}/items
{
  "items": [
    {
      "productId": 123,
      "quantity": 2,
      "note": "Ít đá"
    }
  ]
}
```

#### Cập nhật số lượng
```json
// PATCH /api/pos/sessions/{sessionId}/items/{itemId}
{
  "quantity": 3
}
```

#### Response Error (409 Conflict)
```json
{
  "status": 409,
  "message": "Chỉ có thể xóa món chưa được ra. Món này đã được ra hoặc đã hủy."
}
```

## WebSocket / Realtime

### Topics

```
/topic/tenant/{tenantId}/table/{tableId}      - Updates cho 1 bàn
/topic/tenant/{tenantId}/session/{sessionId}  - Updates cho 1 session
/topic/tenant/{tenantId}/pending-sessions     - Danh sách pending sessions
/topic/tenant/{tenantId}/notifications        - Thông báo chung
```

### Event Types

| Event | Mô tả | Payload |
|-------|-------|---------|
| `ORDER_ITEM_ADDED` | Món mới được thêm | SessionEvent |
| `ORDER_ITEM_DELETED` | Món bị xóa | SessionEvent (chỉ có itemId) |
| `ORDER_ITEM_SERVED` | Món đã mang ra | SessionEvent |
| `ORDER_ITEM_UPDATED` | Số lượng thay đổi | SessionEvent |

### SessionEvent Payload

```json
{
  "type": "ORDER_ITEM_ADDED",
  "sessionId": 123,
  "item": {
    "id": 456,
    "productId": 789,
    "productName": "Cà phê sữa",
    "productImage": "https://...",
    "quantity": 2,
    "price": 35000,
    "note": "Ít đá",
    "status": "PENDING",
    "total": 70000
  },
  "newTotalAmount": 150000,
  "timestamp": "2025-01-01T10:30:00"
}
```

## Frontend Flow

### 1. Khách gọi món (QR)
```
Quét QR → GET /session/{sessionId}?role=customer
       → Subscribe WebSocket /topic/tenant/.../session/{sessionId}
       → Chọn món → POST /items với quantity
       → Nhận realtime event ORDER_ITEM_ADDED
```

### 2. Nhân viên phục vụ
```
Vào /session/{sessionId}?role=staff
→ Subscribe WebSocket
→ Thấy món PENDING → Click "Đã mang ra" → POST /items/{id}/serve
→ Realtime update: item chuyển sang SERVED
```

### 3. Xử lý lỗi 409
```javascript
try {
  await removeSessionItem(sessionId, itemId);
} catch (error) {
  if (error.status === 409) {
    toast.error('Món đã được mang ra, không thể xóa');
    await loadSession(); // Reload để sync state
  }
}
```

## Giao diện

### OrderSessionPage
- URL: `/session/:sessionId?role=staff|customer`
- Dùng chung cho khách và nhân viên
- Flat design, responsive (tablet/mobile)

### Layout
```
┌─────────────────────────────────────────────────────┐
│  Header: Tên bàn | Status | Tổng tiền              │
├────────────────────────┬────────────────────────────┤
│                        │                            │
│    MENU                │    ĐƠN HÀNG               │
│    (Category tabs)     │    ┌──────────────────┐   │
│    ┌────┐ ┌────┐       │    │ Chờ mang ra (3)  │   │
│    │món │ │món │       │    │ - Item 1  [+-][x]│   │
│    └────┘ └────┘       │    │ - Item 2  [+-][x]│   │
│    ┌────┐ ┌────┐       │    ├──────────────────┤   │
│    │món │ │món │       │    │ Đã mang ra (2)   │   │
│    └────┘ └────┘       │    │ - Item 3  ✓      │   │
│                        │    │ - Item 4  ✓      │   │
│                        │    └──────────────────┘   │
│                        │    ┌──────────────────┐   │
│                        │    │ TỔNG: 150,000đ   │   │
│                        │    │ [Thanh toán]     │   │
│                        │    └──────────────────┘   │
└────────────────────────┴────────────────────────────┘
```

### Interactions
- Click món → Modal chọn số lượng → Thêm vào đơn
- Món PENDING: Hiển thị nút +/- và xóa
- Món SERVED: Read-only với badge "Đã mang"
- Staff only: Nút "Đánh dấu đã mang ra" cho từng món PENDING

## Files

### Backend
- `SessionService.java` - Logic nghiệp vụ chính
- `SessionController.java` - REST endpoints
- `SessionEvent.java` - WebSocket event DTO
- `OrderItem.java` - Entity với ItemStatus enum

### Frontend
- `OrderSessionPage.jsx` - Giao diện gọi món chung
- `OrderSessionPage.css` - Styles
- `useWebSocket.js` - WebSocket hooks với event handling
- `api/session.js` - API calls
