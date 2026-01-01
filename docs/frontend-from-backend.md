# Frontend Implementation Guide - F&B POS System

> **Refactored Backend Analysis**  
> **Date**: January 2026  
> **Version**: 3.0 (Session-Based Refactor)  
> **Purpose**: Tài liệu triển khai Frontend dựa trên phân tích Backend sau refactor

---

## 📋 Table of Contents

1. [Domain Model (Backend Reality)](#1-domain-model-backend-reality)
2. [User Stories by Role](#2-user-stories-by-role)
3. [API Catalog](#3-api-catalog)
4. [Screen Designs & Flows](#4-screen-designs--flows)
5. [Frontend Implementation Plan](#5-frontend-implementation-plan)
6. [Missing APIs & Recommendations](#6-missing-apis--recommendations)

---

## 1. Domain Model (Backend Reality)

### 1.1 Core Entities (Sau Refactor)

Backend đã được refactor theo **Session-Based Model thuần túy**. Đây là foundation bạn PHẢI hiểu:

```
┌──────────────────────────────────────────────────────────────┐
│                    SERVING SESSION                            │
│  (Trung tâm - Đại diện cho 1 lần phục vụ khách)              │
├──────────────────────────────────────────────────────────────┤
│  Fields:                                                      │
│  - id: Long                                                   │
│  - status: PENDING | ACTIVE | COMPLETED | CANCELLED          │
│  - startedAt, endedAt: LocalDateTime                         │
│  - guestCount: Integer                                        │
│  - note: String                                               │
│  - createdBy: Employee (null nếu khách QR tạo)               │
├──────────────────────────────────────────────────────────────┤
│  Relationships:                                               │
│  - tables: List<DiningTable>    (1→N)                        │
│  - orders: List<Order>           (1→N, thực tế 1 order)      │
│                                                               │
│  ⚠️ INVARIANTS (CRITICAL):                                   │
│  • Session LUÔN có ≥1 Table                                  │
│  • Session KHÔNG hỗ trợ split bill                           │
│  • Sau PAY/CANCEL → tất cả Table được release                │
└──────────────────────────────────────────────────────────────┘
           │                              │
           ▼                              ▼
┌──────────────────┐        ┌────────────────────────────────┐
│  DINING TABLE    │        │         ORDER                   │
├──────────────────┤        ├────────────────────────────────┤
│ - id: Integer    │        │ - id: Long                      │
│ - name: String   │        │ - session: ServingSession       │
│ - status:        │        │ - totalAmount: BigDecimal       │
│   AVAILABLE      │        │ - status: OPEN|COMPLETED|...    │
│   OCCUPIED       │        │ - paymentMethod: String         │
│   RESERVED       │        │ - items: List<OrderItem>        │
│ - qrCodeUrl      │        │                                 │
│ - currentSession │        │ ⚠️ KHÔNG CÓ tableId!           │
│   (FK→Session)   │        │    Order thuộc Session          │
└──────────────────┘        └────────────────────────────────┘
                                        │
                                        ▼
                           ┌────────────────────────────────┐
                           │      ORDER ITEM                 │
                           ├────────────────────────────────┤
                           │ - id: Long                      │
                           │ - order: Order                  │
                           │ - product: Product              │
                           │ - quantity: Integer             │
                           │ - price: BigDecimal             │
                           │ - note: String                  │
                           │ - status: PENDING|SERVED|...    │
                           │ - originalTable: DiningTable(*) │
                           │   (*tracking bàn nào gọi món)   │
                           └────────────────────────────────┘
```

### 1.2 SessionStatus Lifecycle

```
CUSTOMER QR PATH:
┌─────────┐   customer    ┌─────────┐   staff     ┌────────┐
│ (empty) │──creates────→ │ PENDING │──confirms─→ │ ACTIVE │
└─────────┘               └─────────┘             └────────┘
                               │                       │
                          staff│rejects           pay/ │cancel
                               ▼                       ▼
                          ┌──────────┐           ┌───────────┐
                          │CANCELLED │           │ COMPLETED │
                          └──────────┘           └───────────┘

STAFF DIRECT PATH:
┌─────────┐   staff opens  ┌────────┐
│ (empty) │───────────────→│ ACTIVE │─→ ... → COMPLETED/CANCELLED
└─────────┘                 └────────┘
```

**Giải thích trạng thái**:
- **PENDING**: Khách quét QR tạo order, chờ nhân viên xác nhận
- **ACTIVE**: Đang phục vụ (nhân viên confirm hoặc tạo trực tiếp)
- **COMPLETED**: Đã thanh toán xong
- **CANCELLED**: Đã hủy (do nhân viên reject hoặc cancel)

---

### 1.3 Table Operations (SAU REFACTOR)

Backend ĐÃ ĐƠN GIẢN HÓA chỉ còn 2 thao tác:

| Operation | API | Ý nghĩa thực tế |
|-----------|-----|-----------------|
| **Attach Table** | `POST /api/pos/sessions/{id}/tables` | Thêm bàn vào session (gộp bàn) |
| **Detach Table** | `DELETE /api/pos/sessions/{id}/tables/{tableId}` | Bỏ bàn khỏi session (chỉ khi >1 bàn) |

**❌ ĐÃ LOẠI BỎ**:
- `/merge` - Thay bằng Attach
- `/split` - KHÔNG hỗ trợ split bill
- `/transfer` - Thay bằng Attach new + Detach old

---

## 2. User Stories by Role

### 2.1 👨‍🍳 Nhân viên Phục vụ (Waiter)

#### US-W01: Mở bàn khi khách vào
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Khách bước vào quán, tìm chỗ ngồi  
**Mục đích**: Bắt đầu phục vụ, theo dõi order

**Luồng thực tế**:
1. Nhân viên chọn bàn trống từ sơ đồ bàn
2. Nhập số khách (optional)
3. Ghi chú (VD: "Khách VIP", "Sinh nhật")
4. Hệ thống:
   - Tạo Session (status: ACTIVE)
   - Gán bàn vào Session (Table.status → OCCUPIED)
   - Tạo Order mặc định

**API**: 
```
POST /api/pos/sessions
Body: { tableId: 1, guestCount: 4, note: "..." }
Response: SessionResponse { sessionId, status: ACTIVE, tables: [...], orders: [...] }
```

**UI Requirements**:
- Sơ đồ bàn: bàn xanh (trống) → đỏ (bận)
- Click bàn → Modal "Mở bàn" với input số khách, ghi chú
- Sau mở → chuyển sang màn order detail

---

#### US-W02: Gọi món cho khách
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Khách đã ngồi, gọi món  
**Mục đích**: Thêm món vào order hiện tại

**Luồng thực tế**:
1. Nhân viên chọn session/bàn đang phục vụ
2. Browse menu theo danh mục
3. Click món → chọn số lượng, ghi chú (VD: "ít đường")
4. Hệ thống:
   - Thêm OrderItem vào Order
   - Cập nhật totalAmount
   - Notify bếp (qua WebSocket hoặc print)

**API**:
```
POST /api/pos/sessions/{sessionId}/items
Body: { items: [{ productId: 10, quantity: 2, note: "ít đá" }] }
```

**UI Requirements**:
- Menu grid có ảnh, tên, giá
- Filter theo category
- Shopping cart realtime update
- Tổng tiền hiển thị rõ

---

#### US-W03: Gộp bàn (Khách cần thêm chỗ ngồi)
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Khách đang ngồi bàn 1, có bạn đến → cần gộp thêm bàn 2  
**Mục đích**: Tăng chỗ ngồi mà không tách bill

**Luồng thực tế**:
1. Nhân viên vào session hiện tại (bàn 1)
2. Click "Gộp bàn"
3. Chọn bàn trống (bàn 2)
4. Hệ thống:
   - Attach bàn 2 vào Session
   - Bàn 2 chuyển OCCUPIED
   - Session.tables = [1, 2]

**API**:
```
POST /api/pos/sessions/{sessionId}/tables
Body: { tableId: 2 }
```

**UI Requirements**:
- Button "Gộp bàn" trong session detail
- Modal chọn bàn trống (chỉ show AVAILABLE)
- Sau gộp: hiển thị "Bàn 1, 2" ở header

---

#### US-W04: Tách bàn (Một phần khách rời đi)
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Session đang có bàn 1, 2, 3. Khách ở bàn 3 rời đi  
**Mục đích**: Trả bàn 3 để phục vụ khách mới

**Luồng thực tế**:
1. Nhân viên vào session (bàn 1,2,3)
2. Click nút "X" trên chip "Bàn 3"
3. Confirm "Tách bàn 3?"
4. Hệ thống:
   - Detach bàn 3
   - Bàn 3 → AVAILABLE
   - Session.tables = [1, 2]
   - Order vẫn giữ nguyên

**API**:
```
DELETE /api/pos/sessions/{sessionId}/tables/3
```

**UI Requirements**:
- Hiển thị danh sách bàn trong session (chips)
- Mỗi chip có nút "×" để tách
- Disable nút "×" nếu chỉ còn 1 bàn (không cho tách bàn cuối)

---

#### US-W05: ❌ "Chuyển bàn" - KHÔNG còn endpoint riêng
**Thực tế**: Backend đã loại bỏ `/transfer`. Thay bằng sequence:
1. Attach bàn mới (VD: bàn 4)
2. Detach bàn cũ (VD: bàn 1)

**Frontend implementation**:
```javascript
// UI: Button "Chuyển bàn"
async function transferTable(sessionId, newTableId, oldTableId) {
  await attachTable(sessionId, newTableId);
  await detachTable(sessionId, oldTableId);
  // UI update
}
```

---

### 2.2 💰 Thu ngân (Cashier)

#### US-C01: Xem danh sách bàn đang phục vụ
**Vai trò**: Thu ngân  
**Ngữ cảnh**: Chuẩn bị thanh toán, cần overview  
**Mục đích**: Biết bàn nào đang có khách, bàn nào trống

**Luồng thực tế**:
1. Vào màn "POS / Sơ đồ bàn"
2. Thấy:
   - Bàn xanh = AVAILABLE
   - Bàn đỏ = OCCUPIED
   - Bàn vàng = RESERVED (nếu có)
3. Click bàn đỏ → xem order detail

**API**:
```
GET /api/pos/tables
Response: [{ id, name, status, qrCodeUrl, sessionId }]
```

**UI Requirements**:
- Grid/List view bàn
- Color coding rõ ràng
- Click vào bàn → navigate to session detail

---

#### US-C02: Thanh toán tiền mặt
**Vai trò**: Thu ngân  
**Ngữ cảnh**: Khách gọi "Tính tiền!"  
**Mục đích**: Thu tiền, in hóa đơn, giải phóng bàn

**Luồng thực tế**:
1. Thu ngân vào session của bàn
2. Xem lại order (items, total)
3. Click "Thanh toán"
4. Chọn phương thức: CASH
5. Confirm
6. Hệ thống:
   - Order.status → COMPLETED
   - Session.status → COMPLETED
   - Release ALL tables → AVAILABLE
   - Trả về InvoiceDto (có thể in)

**API**:
```
POST /api/pos/sessions/{sessionId}/pay
Body: { method: "CASH" }
Response: InvoiceDto { sessionId, items, totalAmount, cashier, timestamp, ... }
```

**UI Requirements**:
- Modal confirm thanh toán
- Hiển thị tổng tiền to, rõ
- Button "Thanh toán tiền mặt" / "Thanh toán VNPay"
- Sau thanh toán: show "Thành công" + option in hóa đơn

---

#### US-C03: Thanh toán online (VNPay)
**Vai trò**: Thu ngân  
**Ngữ cảnh**: Khách muốn quét QR thanh toán  
**Mục đích**: Tạo payment link VNPay

**Luồng thực tế**:
1. Thu ngân click "Thanh toán VNPay"
2. Hệ thống:
   - Call API pay với method: VNPAY
   - Backend tạo payment URL
   - Frontend hiển thị QR code (hoặc redirect)
3. Khách quét QR, thanh toán
4. VNPay callback → Backend tự động complete session

**API**:
```
POST /api/pos/sessions/{sessionId}/pay
Body: { method: "VNPAY" }
Response: { paymentUrl: "https://vnpay.vn/..." }
```

**UI Requirements**:
- Modal hiển thị QR code (dùng lib qrcode.react)
- Hoặc redirect sang VNPay
- Polling để check session status → auto close khi COMPLETED

---

#### US-C04: Hủy session (Khách cancel order)
**Vai trò**: Thu ngân / Nhân viên  
**Ngữ cảnh**: Khách đột ngột cancel, không order nữa  
**Mục đích**: Hủy order, giải phóng bàn

**Luồng thực tế**:
1. Nhân viên click "Hủy đơn"
2. Nhập lý do (optional): "Khách không order nữa"
3. Confirm
4. Hệ thống:
   - Order.status → CANCELLED
   - Session.status → CANCELLED
   - Release all tables

**API**:
```
POST /api/pos/sessions/{sessionId}/cancel
Body: { reason: "Khách hủy" }
```

**UI Requirements**:
- Button "Hủy đơn" (màu đỏ, nguy hiểm)
- Confirm modal với input lý do
- Sau hủy: bàn trở lại trống

---

### 2.3 👔 Chủ quán / Quản lý (Owner/Manager)

#### US-O01: Xem báo cáo doanh thu
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Cuối ngày/tuần/tháng  
**Mục đích**: Đánh giá hiệu quả kinh doanh

**API**:
```
GET /api/reports/revenue?from=2026-01-01&to=2026-01-31
Response: [{ date: "2026-01-01", orderCount: 50, totalRevenue: 5000000 }]
```

**UI Requirements**:
- Date range picker
- Chart (line/bar) hiển thị revenue theo ngày
- Summary cards: Tổng doanh thu, Số đơn, Đơn TB

---

#### US-O02: Xem món bán chạy
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Muốn biết món nào hot  
**Mục đích**: Điều chỉnh menu, tồn kho

**API**:
```
GET /api/reports/top-products?from=...&to=...&limit=10
Response: [{ productId, productName, quantitySold, revenue }]
```

**UI Requirements**:
- Table/Chart top 10 món
- Sort by quantitySold hoặc revenue
- Filter theo date range

---

#### US-O03: Xem khung giờ cao điểm
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Sắp xếp nhân sự  
**Mục đích**: Biết giờ nào đông khách

**API**:
```
GET /api/reports/peak-hours?from=...&to=...
Response: [{ hour: 12, orderCount: 30 }, { hour: 18, orderCount: 45 }]
```

**UI Requirements**:
- Heatmap 24 giờ
- Color intensity = số đơn
- Filter theo date range

---

#### US-O04: Quản lý danh mục món
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Cập nhật menu  
**Mục đích**: Tổ chức menu logic

**API**:
```
GET /api/categories
POST /api/categories?name=...&order=1
DELETE /api/categories/{id}
```

**UI Requirements**:
- List categories
- CRUD operations (Add, Delete)
- Drag & drop để sort (optional)

---

#### US-O05: Quản lý sản phẩm
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Thêm món mới, cập nhật giá  
**Mục đích**: Cập nhật menu kịp thời

**API**:
```
GET /api/products?categoryId=...&status=...&page=0&size=50
POST /api/products (multipart: categoryId, name, price, description, images[])
PUT /api/products/{id}?name=...&price=...&status=...
DELETE /api/products/{id}
```

**UI Requirements**:
- Product list với filter (category, status)
- Form add/edit với upload ảnh
- Preview ảnh
- Status toggle (AVAILABLE/OUT_OF_STOCK)

---

#### US-O06: Quản lý bàn
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Thêm bàn mới khi mở rộng  
**Mục đích**: Cấu hình sơ đồ bàn

**API**:
```
GET /api/pos/tables
POST /api/pos/tables?name=...
DELETE /api/pos/tables/{id}
```

**UI Requirements**:
- List tables
- Add table (auto generate QR)
- Delete (chỉ khi trống)
- Show QR code cho mỗi bàn (in QR sticker)

---

#### US-O07: Xem danh sách nhân viên
**Vai trò**: Chủ quán  
**Ngữ cảnh**: Quản lý team  
**Mục đích**: Theo dõi nhân viên

**API**:
```
GET /api/staff?page=0&size=10
Response: Page<EmployeeResponse>
```

**UI Requirements**:
- Table: Tên, Email, Role, Ngày vào làm
- Pagination
- Delete staff (thu hồi quyền)

---

### 2.4 📱 Khách hàng (Customer - QR Order)

#### US-CU01: Xem menu qua QR
**Vai trò**: Khách hàng  
**Ngữ cảnh**: Quét QR trên bàn  
**Mục đích**: Xem menu, tự gọi món

**Luồng thực tế**:
1. Khách quét QR code trên bàn
2. QR redirect → `/customer/{tableId}`
3. Frontend gọi API lấy thông tin bàn + menu
4. Hiển thị menu theo categories

**API**:
```
GET /api/pos/public/info/{tableId}
Response: { tableId, tableName, tenantName, tenantLogo, hasActiveSession, sessionId }

GET /api/pos/public/menu
Response: [{ categoryId, categoryName, products: [...] }]
```

**UI Requirements**:
- Public page (no auth)
- Header: Logo quán, tên bàn
- Menu grid theo category
- Add to cart

---

#### US-CU02: Gọi món lần đầu (tạo pending order)
**Vai trò**: Khách hàng  
**Ngữ cảnh**: Lần đầu gọi món qua QR  
**Mục đích**: Gửi order đến nhân viên

**Luồng thực tế**:
1. Khách chọn món, add to cart
2. Click "Gửi gọi món"
3. Hệ thống:
   - Tạo Session (status: PENDING)
   - Gắn Table vào Session (nhưng Table vẫn AVAILABLE)
   - Tạo Order + Items
4. Frontend:
   - Chuyển sang màn "Đang chờ xác nhận"
   - Polling API check status mỗi 3s

**API**:
```
POST /api/pos/public/sessions
Body: { tableId: 5, items: [{productId, quantity}], customerNote: "..." }
Response: CustomerOrderResponse { sessionId, status: PENDING, tableName, items, totalAmount, statusMessage }
```

**UI Requirements**:
- Shopping cart modal
- Button "Gửi gọi món"
- Loading state: "Đang gửi..."
- Success: Chuyển màn chờ xác nhận

---

#### US-CU03: Kiểm tra trạng thái order
**Vai trò**: Khách hàng  
**Ngữ cảnh**: Đã gửi order, chờ nhân viên  
**Mục đích**: Biết order đã được xác nhận chưa

**Luồng thực tế**:
1. Frontend polling API mỗi 3s
2. Check sessionStatus:
   - PENDING → Hiển thị "Đang chờ..."
   - ACTIVE → "Đã xác nhận! Món đang được chuẩn bị"
   - CANCELLED → "Bị từ chối" + lý do
3. Nếu ACTIVE → cho phép gọi thêm món

**API**:
```
GET /api/pos/public/sessions/{sessionId}
Response: CustomerOrderResponse { status, statusMessage, rejectReason?, ... }
```

**UI Requirements**:
- Màn chờ với animation
- Status indicator: PENDING (yellow), ACTIVE (green), CANCELLED (red)
- Nếu ACTIVE → button "Gọi thêm món"
- Nếu CANCELLED → button "Thử lại"

---

#### US-CU04: Gọi thêm món (session đã active)
**Vai trò**: Khách hàng  
**Ngữ cảnh**: Order đã được confirm, muốn gọi thêm  
**Mục đích**: Bổ sung order

**Luồng thực tế**:
1. Từ màn "Đã xác nhận", click "Gọi thêm món"
2. Quay lại menu
3. Chọn món mới, click "Gửi"
4. Hệ thống:
   - Thêm món vào order hiện tại (KHÔNG tạo pending mới)
   - Update total

**API**:
```
POST /api/pos/public/sessions/{sessionId}/items
Body: { tableId: 5, items: [...] }
```

**UI Requirements**:
- Button "Gọi thêm món" rõ ràng
- Add to cart → gửi trực tiếp (không cần confirm lại)

---

### 2.5 👨‍💼 Nhân viên xử lý Pending Orders

#### US-S01: Xem danh sách order chờ xác nhận
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Khách quét QR gửi order  
**Mục đích**: Xử lý kịp thời

**Luồng thực tế**:
1. POSPage có badge "Đơn chờ xác nhận (3)"
2. Click → mở panel pending orders
3. Hiển thị list: Bàn, Món, Số lượng, Tổng tiền
4. Mỗi item có button Confirm / Reject

**API**:
```
GET /api/pos/sessions/pending
Response: [SessionResponse { sessionId, status: PENDING, tables, orders }]
```

**UI Requirements**:
- Notification badge realtime (WebSocket hoặc polling)
- Panel/Modal list pending
- Mỗi item: Card với info + actions
- Click Confirm → call API confirm
- Click Reject → modal nhập lý do

---

#### US-S02: Xác nhận order
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Pending order hợp lệ  
**Mục đích**: Chuyển sang ACTIVE, bắt đầu phục vụ

**API**:
```
POST /api/pos/sessions/{sessionId}/confirm
Response: SessionResponse { status: ACTIVE, ... }
```

**Flow**:
- Session PENDING → ACTIVE
- Table → OCCUPIED
- Khách nhận được thông báo (qua polling)

**UI Requirements**:
- Button "Xác nhận" (màu xanh)
- Sau confirm: item biến mất khỏi pending list
- Table status update ngay lập tức

---

#### US-S03: Từ chối order
**Vai trò**: Nhân viên  
**Ngữ cảnh**: Order không hợp lệ (VD: món hết)  
**Mục đích**: Thông báo khách

**API**:
```
POST /api/pos/sessions/{sessionId}/reject
Body: { reason: "Món đã hết" }
```

**Flow**:
- Session PENDING → CANCELLED
- Table vẫn AVAILABLE
- Khách nhận được reject message

**UI Requirements**:
- Button "Từ chối" (màu đỏ)
- Modal input lý do
- Sau reject: item biến mất

---

## 3. API Catalog

### 3.1 Session Management (Core POS)

| Method | Endpoint | Auth | Mô tả | Request | Response |
|--------|----------|------|-------|---------|----------|
| POST | `/api/pos/sessions` | ✅ Staff | Mở bàn/Tạo session | `{ tableId, guestCount?, note? }` | `SessionResponse` |
| GET | `/api/pos/sessions/{id}` | ✅ Staff | Lấy session | - | `SessionResponse` |
| GET | `/api/pos/sessions/table/{tableId}` | ✅ Staff | Lấy/tạo session theo bàn | - | `SessionResponse` |
| POST | `/api/pos/sessions/{id}/items` | ✅ Staff | Thêm món | `{ items: [{productId, quantity, note?}] }` | `"Đã thêm món"` |
| POST | `/api/pos/sessions/{id}/tables` | ✅ Staff | Attach table (gộp bàn) | `{ tableId }` | `"Đã thêm bàn"` |
| DELETE | `/api/pos/sessions/{id}/tables/{tableId}` | ✅ Staff | Detach table (tách bàn) | - | `"Đã tách bàn"` |
| POST | `/api/pos/sessions/{id}/pay` | ✅ Staff | Thanh toán | `{ method: CASH/VNPAY }` | `InvoiceDto` |
| POST | `/api/pos/sessions/{id}/cancel` | ✅ Staff | Hủy session | `{ reason? }` | `"Đã hủy"` |

### 3.2 Pending Session Management

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/pos/sessions/pending` | ✅ Staff | Lấy danh sách pending |
| POST | `/api/pos/sessions/{id}/confirm` | ✅ Staff | Xác nhận (PENDING→ACTIVE) |
| POST | `/api/pos/sessions/{id}/reject` | ✅ Staff | Từ chối (PENDING→CANCELLED) |

### 3.3 Customer QR APIs (Public)

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/pos/public/menu` | 🔓 Public | Lấy menu công khai |
| GET | `/api/pos/public/info/{tableId}` | 🔓 Public | Thông tin bàn + quán |
| POST | `/api/pos/public/sessions` | 🔓 Public | Khách tạo order (→PENDING) |
| GET | `/api/pos/public/sessions/{id}` | 🔓 Public | Check status order |
| POST | `/api/pos/public/sessions/{id}/items` | 🔓 Public | Gọi thêm món (session ACTIVE) |

### 3.4 Table Management

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/pos/tables` | ✅ Staff | Danh sách bàn |
| POST | `/api/pos/tables?name=...` | ✅ Owner | Tạo bàn (auto QR) |
| DELETE | `/api/pos/tables/{id}` | ✅ Owner | Xóa bàn |

### 3.5 Menu Management

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/categories` | ✅ Staff | Danh sách categories |
| POST | `/api/categories?name=...&order=1` | ✅ Owner | Tạo category |
| DELETE | `/api/categories/{id}` | ✅ Owner | Xóa category |
| GET | `/api/products?categoryId=...&status=...&page=0&size=50` | ✅ Staff | Danh sách products |
| GET | `/api/products/{id}` | ✅ Staff | Chi tiết product |
| POST | `/api/products` (multipart) | ✅ Owner | Tạo product + upload ảnh |
| PUT | `/api/products/{id}?name=...&price=...` | ✅ Owner | Cập nhật info |
| POST | `/api/products/{id}/images` (multipart) | ✅ Owner | Thêm ảnh |
| DELETE | `/api/products/images/{imageId}` | ✅ Owner | Xóa ảnh |
| DELETE | `/api/products/{id}` | ✅ Owner | Xóa product |

### 3.6 Reporting

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/reports/revenue?from=...&to=...` | ✅ Owner | Doanh thu |
| GET | `/api/reports/top-products?from=...&to=...&limit=10` | ✅ Owner | Top món |
| GET | `/api/reports/peak-hours?from=...&to=...` | ✅ Owner | Khung giờ cao điểm |

### 3.7 HRM

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/staff?page=0&size=10` | ✅ Owner | Danh sách nhân viên |
| DELETE | `/api/staff/{id}` | ✅ Owner | Xóa nhân viên |

---

## 4. Screen Designs & Flows

### 4.1 POSPage - Màn hình chính

**Layout**:
```
┌─────────────────────────────────────────────────────────────┐
│ [Logo] POS - Bán hàng            [Pending Badge (3)] [User] │
├────────────────┬────────────────────────────────────────────┤
│  SƠ ĐỒ BÀN    │         CHI TIẾT SESSION                   │
│                │                                            │
│  ┌─────────┐  │  Session: #123 - Bàn 1, 2                  │
│  │🟢 B1    │  │  Khách: 4 người                            │
│  │ (Empty) │  │  ─────────────────────────────────────     │
│  └─────────┘  │  🍜 Phở bò        x2     60,000đ           │
│  ┌─────────┐  │  ☕ Cà phê sữa    x1     25,000đ           │
│  │🔴 B2    │  │  🧋 Trà sữa       x1     30,000đ           │
│  │ (Busy)  │  │  ─────────────────────────────────────     │
│  └─────────┘  │  TỔNG: 115,000đ                            │
│  ┌─────────┐  │                                            │
│  │🟢 B3    │  │  [+ Thêm món]  [Gộp bàn]  [Thanh toán]    │
│  └─────────┘  │                                            │
│                │                                            │
│  [+ Thêm bàn] │                                            │
└────────────────┴────────────────────────────────────────────┘
```

**Components**:
- **TableGrid** (Left): Hiển thị sơ đồ bàn
  - Color: Green (AVAILABLE), Red (OCCUPIED), Yellow (RESERVED)
  - Click bàn → load session vào right panel
- **SessionPanel** (Right): Chi tiết session
  - Header: Session ID, Bàn, Số khách
  - Body: List OrderItems
  - Footer: Total + Actions
- **PendingNotification** (Top-right): Badge "3 đơn chờ"
  - Click → Mở PendingPanel

**State Management**:
```javascript
const [tables, setTables] = useState([]);
const [selectedSession, setSelectedSession] = useState(null);
const [pendingSessions, setPendingSessions] = useState([]);
```

**APIs Used**:
- `GET /api/pos/tables` → Load tables
- `GET /api/pos/sessions/{id}` → Load session detail
- `GET /api/pos/sessions/pending` → Load pending (polling)

---

### 4.2 CustomerMenuPage - Khách quét QR

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ [Logo Quán] Nhà hàng ABC                        │
│ 📍 Bàn 5                                        │
├─────────────────────────────────────────────────┤
│ [Đồ uống] [Món chính] [Tráng miệng] [Khác]     │
├─────────────────────────────────────────────────┤
│  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │ 🍜     │  │ ☕     │  │ 🧋     │            │
│  │Phở bò  │  │Cà phê  │  │Trà sữa │            │
│  │30,000đ │  │25,000đ │  │30,000đ │            │
│  │[+]     │  │[+]     │  │[+]     │            │
│  └────────┘  └────────┘  └────────┘            │
│                                                 │
├─────────────────────────────────────────────────┤
│ [🛒 Giỏ hàng (3 món) - 85,000đ]                │
└─────────────────────────────────────────────────┘
```

**States**:
- `orderView`: 'menu' | 'pending' | 'confirmed' | 'rejected'
- `cart`: [{ productId, quantity }]
- `currentOrder`: { sessionId, status, items, totalAmount }

**Flow**:
1. Load menu → `GET /api/pos/public/menu`
2. Add to cart (local state)
3. Submit → `POST /api/pos/public/sessions`
4. Switch to pending view
5. Polling → `GET /api/pos/public/sessions/{id}` (every 3s)
6. If status=ACTIVE → show confirmed view
7. If status=CANCELLED → show rejected view

---

### 4.3 PendingPanel - Nhân viên xử lý pending

**Layout**:
```
┌────────────────────────────────────────────┐
│ Đơn hàng chờ xác nhận                  [×] │
├────────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐   │
│ │ ⏰ Bàn 5                              │   │
│ │ 3 món - 85,000đ                       │   │
│ │ • Phở bò x2                           │   │
│ │ • Cà phê x1                           │   │
│ │                                       │   │
│ │ [✅ Xác nhận]  [❌ Từ chối]          │   │
│ └──────────────────────────────────────┘   │
│                                            │
│ ┌──────────────────────────────────────┐   │
│ │ ⏰ Bàn 3                              │   │
│ │ 2 món - 55,000đ                       │   │
│ │ [✅ Xác nhận]  [❌ Từ chối]          │   │
│ └──────────────────────────────────────┘   │
└────────────────────────────────────────────┘
```

**APIs**:
- `GET /api/pos/sessions/pending` (polling every 5s)
- `POST /api/pos/sessions/{id}/confirm`
- `POST /api/pos/sessions/{id}/reject` + reason

---

## 5. Frontend Implementation Plan

### 5.1 Tech Stack

- **Framework**: React 18 (Vite)
- **Routing**: React Router v6
- **State**: Context API + useState/useReducer
- **HTTP**: Axios
- **WebSocket**: SockJS + Stomp (realtime updates)
- **UI**: Custom CSS (Flat Design) + Lucide Icons
- **Forms**: React Hook Form
- **Charts**: Recharts
- **QR**: qrcode.react

### 5.2 Folder Structure

```
src/
├── api/
│   ├── client.js          # Axios instance + interceptors
│   ├── session.js         # Session APIs
│   ├── table.js           # Table APIs
│   ├── menu.js            # Menu APIs
│   ├── reports.js         # Reporting APIs
│   └── hrm.js             # HRM APIs
├── components/
│   ├── common/
│   │   ├── Button.jsx
│   │   ├── Card.jsx
│   │   ├── Modal.jsx
│   │   ├── Loading.jsx
│   │   ├── Input.jsx
│   │   └── StatusBadge.jsx
│   └── layout/
│       ├── Header.jsx
│       ├── Sidebar.jsx
│       └── PageLayout.jsx
├── pages/
│   ├── pos/
│   │   ├── POSPage.jsx
│   │   ├── TableGridPage.jsx
│   │   └── components/
│   │       ├── TableGrid.jsx
│   │       ├── SessionPanel.jsx
│   │       ├── PendingPanel.jsx
│   │       └── MenuModal.jsx
│   ├── customer/
│   │   └── CustomerMenuPage.jsx
│   ├── menu/
│   │   ├── CategoryListPage.jsx
│   │   └── ProductListPage.jsx
│   ├── reports/
│   │   └── ReportsPage.jsx
│   └── hrm/
│       ├── StaffListPage.jsx
│       └── JobListPage.jsx
├── context/
│   ├── AuthContext.jsx
│   ├── TenantContext.jsx
│   └── ToastContext.jsx
├── utils/
│   ├── constants.js
│   └── format.js
└── styles/
    ├── global.css
    └── variables.css
```

### 5.3 Implementation Phases

**Phase 1: Foundation (Week 1)**
- Setup project structure
- API client + Auth
- Common components
- Layout components

**Phase 2: POS Core (Week 2)**
- TableGrid component
- SessionPanel component
- Attach/Detach table logic
- Add items to session

**Phase 3: Payment & Pending (Week 3)**
- Payment flow (Cash/VNPay)
- PendingPanel component
- Confirm/Reject logic
- WebSocket integration

**Phase 4: Customer QR (Week 4)**
- CustomerMenuPage
- Shopping cart
- Order status polling
- Public API integration

**Phase 5: Management (Week 5)**
- Menu management (Categories, Products)
- Reports (Revenue, Top products, Peak hours)
- Staff management
- Settings

**Phase 6: Polish & Testing (Week 6)**
- UI polish
- Error handling
- Loading states
- E2E testing

---

## 6. Missing APIs & Recommendations

### 6.1 ✅ APIs đầy đủ

Backend SAU REFACTOR đã cung cấp đủ APIs cho tất cả user stories. KHÔNG cần bổ sung thêm endpoint mới.

**Lý do**:
- Session-based model đơn giản, rõ ràng
- Attach/Detach thay thế merge/split/transfer
- Customer QR flow hoàn chỉnh (public APIs)
- Reporting APIs đầy đủ

### 6.2 🔄 WebSocket Topics (Already Implemented)

```
/topic/tenant/{tenantId}/tables           # Table status updates
/topic/tenant/{tenantId}/pending-sessions # New pending orders
/topic/tenant/{tenantId}/table/{tableId}  # Session updates
```

**Frontend Integration**:
```javascript
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
  stompClient.subscribe(`/topic/tenant/${tenantId}/tables`, (message) => {
    const tables = JSON.parse(message.body);
    setTables(tables); // Realtime update
  });
});
```

### 6.3 ⚠️ Potential Enhancements (Optional)

#### 1. Batch Operations API
**Use Case**: Nhân viên muốn confirm nhiều pending orders cùng lúc

**Proposal**:
```
POST /api/pos/sessions/batch-confirm
Body: { sessionIds: [1, 2, 3] }
```

**Current Workaround**: Frontend call confirm API tuần tự

---

#### 2. Session Transfer API (Simplified)
**Use Case**: Chuyển bàn nhanh (1 API call thay vì 2)

**Proposal**:
```
POST /api/pos/sessions/{id}/transfer-tables
Body: { newTableIds: [4, 5], removeTableIds: [1, 2] }
```

**Current Workaround**: 
```javascript
await attachTable(sessionId, 4);
await attachTable(sessionId, 5);
await detachTable(sessionId, 1);
await detachTable(sessionId, 2);
```

**Recommendation**: KHÔNG cần implement ngay. Dùng workaround trước, nếu performance issue thì mới thêm.

---

#### 3. Remove OrderItem API
**Use Case**: Khách gọi nhầm món, muốn xóa

**Current**: Chưa có API `DELETE /api/pos/orders/items/{itemId}`

**Impact**: Nhỏ - nhân viên có thể cancel toàn bộ session rồi tạo lại

**Recommendation**: Bổ sung sau nếu cần:
```
DELETE /api/pos/orders/items/{itemId}
```

---

## 7. Key Takeaways

### 7.1 Backend Strengths

✅ **Session-Based Model thuần túy**  
✅ **Đơn giản hóa operations** (chỉ Attach/Detach)  
✅ **Customer QR flow hoàn chỉnh**  
✅ **Pending confirmation workflow**  
✅ **Multi-tenant support**  
✅ **WebSocket realtime updates**

### 7.2 Frontend Focus

🎯 **Ưu tiên tính thực tế**  
- UI đơn giản, ít click
- Thao tác nhanh cho nhân viên
- Feedback rõ ràng (loading, success, error)

🎯 **Realtime Updates**  
- WebSocket cho table status
- Polling cho pending orders
- Optimistic UI updates

🎯 **Error Handling**  
- Validate inputs trước khi gửi API
- Show error messages rõ ràng
- Retry mechanism cho network errors

### 7.3 Development Principles

1. **Backend Leads Frontend** - Không tự ý thêm feature không có API
2. **User Story Drives Design** - Mỗi màn hình phải phục vụ user story rõ ràng
3. **Simplicity Over Complexity** - Ưu tiên đơn giản, dễ dùng
4. **Production Ready** - Code như sẽ deploy ngay

---

**END OF DOCUMENT**

> Tài liệu này là foundation để triển khai frontend production-ready.  
> Mọi quyết định UI/UX phải dựa trên backend thực tế và user stories đã phân tích.

