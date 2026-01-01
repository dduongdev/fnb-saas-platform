# Customer QR Ordering Flow

## Tổng quan

Tài liệu này mô tả luồng hoạt động khi khách hàng quét QR code trên bàn để đặt món.

## User Stories

### 1. Khách hàng quét QR và xem menu

```
Khách hàng đến quán → Quét QR code trên bàn → Vào trang menu công khai
                                               → Xem thông tin quán, tên bàn
                                               → Duyệt menu theo danh mục
```

**URL Format:** `/customer/table/{tableId}`

**API Calls:**
- `GET /api/pos/public/table/{tableId}/info` - Lấy thông tin bàn và tenant

### 2. Khách hàng đặt món lần đầu

```
Khách chọn món → Thêm vào giỏ → Gửi đơn hàng
                                    ↓
                          Tạo Session trạng thái PENDING
                                    ↓
                          Khách thấy màn hình "Đang chờ xác nhận"
                                    ↓
                          Frontend polling status mỗi 3 giây
```

**API Calls:**
- `POST /api/pos/public/sessions` - Tạo session mới với status PENDING

**Request Body:**
```json
{
  "tableId": 1,
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ],
  "customerNote": "Không cay"
}
```

**Response:**
```json
{
  "sessionId": 123,
  "status": "PENDING",
  "statusMessage": "Đơn hàng đang chờ nhân viên xác nhận",
  "tableId": 1,
  "tableName": "Bàn 1",
  "items": [...],
  "totalAmount": 150000
}
```

### 3. Nhân viên xử lý đơn hàng chờ

```
Nhân viên tại POS → Thấy badge "Đơn chờ xác nhận"
                    → Click xem danh sách pending
                    → Xem chi tiết đơn (bàn, món, số lượng)
                    → Xác nhận HOẶC Từ chối
```

**API Calls (Staff):**
- `GET /api/pos/sessions/pending` - Lấy danh sách sessions đang PENDING
- `POST /api/pos/sessions/{id}/confirm` - Xác nhận session
- `POST /api/pos/sessions/{id}/reject` - Từ chối session

### 4. Kết quả sau khi nhân viên xử lý

#### 4a. Nhân viên XÁC NHẬN

```
Nhân viên click "Xác nhận"
        ↓
Session: PENDING → ACTIVE
Table: AVAILABLE → OCCUPIED
        ↓
WebSocket broadcast đến customer
        ↓
Customer thấy màn hình "Order đã được xác nhận!"
        ↓
Customer có thể "Gọi thêm món"
```

#### 4b. Nhân viên TỪ CHỐI

```
Nhân viên click "Từ chối" + nhập lý do
        ↓
Session: PENDING → CANCELLED
Table: vẫn AVAILABLE
        ↓
Customer thấy màn hình "Order bị từ chối"
        ↓
Customer có thể "Thử lại"
```

### 5. Khách hàng gọi thêm món (Bàn đã có session ACTIVE)

```
Khách đang ở bàn có session ACTIVE
        ↓
Chọn thêm món → Gửi
        ↓
Thêm items vào session hiện tại (KHÔNG cần confirm lại)
        ↓
Customer thấy thành công ngay
```

**API Call:**
- `POST /api/pos/public/sessions/{sessionId}/items` - Thêm món vào session đang active

---

## Trạng thái Session (SessionStatus)

| Status | Mô tả | Bàn |
|--------|-------|-----|
| `PENDING` | Đơn hàng mới từ khách, chờ xác nhận | AVAILABLE |
| `ACTIVE` | Đã được xác nhận, đang phục vụ | OCCUPIED |
| `COMPLETED` | Đã thanh toán | AVAILABLE |
| `CANCELLED` | Đã hủy/từ chối | AVAILABLE |

---

## API Endpoints

### Public APIs (Không cần auth)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/pos/public/table/{tableId}/info` | Lấy thông tin bàn |
| GET | `/api/pos/public/menu` | Lấy menu công khai |
| POST | `/api/pos/public/sessions` | Tạo đơn hàng mới (PENDING) |
| GET | `/api/pos/public/sessions/{id}` | Kiểm tra trạng thái đơn |
| POST | `/api/pos/public/sessions/{id}/items` | Thêm món vào session ACTIVE |

### Staff APIs (Cần auth)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/pos/sessions/pending` | Lấy danh sách pending sessions |
| POST | `/api/pos/sessions/{id}/confirm` | Xác nhận session |
| POST | `/api/pos/sessions/{id}/reject` | Từ chối session |

---

## WebSocket Events

### Topic: `/topic/tenant/{tenantId}/pending-sessions`

Khi có session mới hoặc session được xử lý, server broadcast message đến topic này.

**Message Format:**
```json
{
  "type": "NEW_PENDING_SESSION",
  "sessionId": 123,
  "tableId": 1,
  "tableName": "Bàn 1",
  "itemCount": 3,
  "totalAmount": 150000
}
```

---

## Frontend Views

### CustomerMenuPage States

1. **`menu`** - Màn hình menu, chọn món
2. **`pending`** - Đang chờ xác nhận (polling status)
3. **`confirmed`** - Order đã được xác nhận, có thể gọi thêm
4. **`rejected`** - Order bị từ chối, có thể thử lại

### POSPage - Pending Panel

- Badge hiển thị số lượng pending sessions
- Panel dropdown hiển thị danh sách chờ xử lý
- Mỗi item có nút Confirm (✓) và Reject (✗)
- Polling mỗi 5 giây để cập nhật danh sách

---

## Sequence Diagrams

### Luồng đặt món mới

```
Customer          Frontend           Backend            Staff POS
   |                  |                  |                  |
   |--Scan QR-------->|                  |                  |
   |                  |--GET /info------>|                  |
   |                  |<----tableInfo----|                  |
   |--Select items--->|                  |                  |
   |--Submit--------->|                  |                  |
   |                  |--POST /sessions->|                  |
   |                  |                  |--WS: new pending->|
   |                  |<--PENDING--------|                  |
   |<--Show pending---|                  |                  |
   |                  |                  |<--GET pending----|
   |                  |                  |---pending list-->|
   |                  |                  |<--POST confirm---|
   |                  |<--WS: confirmed--|                  |
   |<--Show confirmed-|                  |                  |
```

---

## Lưu ý quan trọng

1. **Bàn không bị chiếm ngay** - Khi khách đặt món, bàn vẫn AVAILABLE cho đến khi staff confirm
2. **Polling fallback** - WebSocket là real-time, nhưng frontend cũng polling để đảm bảo không miss update
3. **Thêm món không cần confirm** - Chỉ đơn hàng đầu tiên cần staff xác nhận, các món thêm sau đi thẳng vào session
4. **Không có kitchen flow** - Hệ thống không track trạng thái chế biến món
