# Guest UI Implementation Guide

## Phạm Vi

Frontend cho **KHÁCH HÀNG (Có & Không Tài Khoản)** gọi món realtime.

---

## Screen 1: Entry

### Scenario: Khách quét QR

**Input:**
```
QR Code → https://app.fnb.local/session/123?entry=qr
                          ↓
Frontend xác định: Không có userId → KHÁCH không tài khoản
```

**Logic:**
```javascript
// URL: /session/:id?entry=qr (hoặc ?role=customer)
const isGuest = !userId || role === 'customer';

// Fetch session
GET /api/pos/sessions/{id}

// Subscribe WebSocket
/topic/tenant/{tenantId}/session/{id}

// UI show: KHÁCH GUEST (no login)
```

---

## Screen 2: Gọi Món (Session Page)

### Layout
```
┌─────────────────────────────────────┐
│ BÀNG 5 | Status: PENDING → ACTIVE    │
├─────────────────────────────────────┤
│                                      │
│  MENU (Category Tabs)                │
│  ┌──────┬──────┬──────┐              │
│  │ Nước │ Ăn   │ Tráng │             │
│  └──────┴──────┴──────┘              │
│                                      │
│  Product Grid                        │
│  ┌──────┬──────┬──────┐              │
│  │ CàPhê│ Nước │ Bánh  │             │
│  │ 35k  │ 15k  │ 25k   │             │
│  └──────┴──────┴──────┘              │
│                                      │
├─────────────────────────────────────┤
│  ĐƠN HÀNG (6 món)                    │
│  ┌────────────────────────┐          │
│  │ Cà phê sữa x2          │          │
│  │ 🕐 14:32  35k × 2 = 70k│          │
│  │ ✓ PENDING    [x]       │          │
│  ├────────────────────────┤          │
│  │ Nước cam x1            │          │
│  │ 🕐 14:33  15k × 1 = 15k│          │
│  │ ✓ SERVED               │          │
│  └────────────────────────┘          │
│  TỔNG: 85,000đ                       │
└─────────────────────────────────────┘
```

### Features

#### 1. Hiển Thị Status Session
```jsx
<div className="session-status">
  Status: 
  {session.status === 'PENDING' && (
    <Badge color="yellow">Chờ xác nhận</Badge>
  )}
  {session.status === 'ACTIVE' && (
    <Badge color="blue">Đang phục vụ</Badge>
  )}
  {session.status === 'COMPLETED' && (
    <Badge color="green">Đã thanh toán</Badge>
  )}
  {session.status === 'CANCELLED' && (
    <Badge color="red">Đã hủy</Badge>
  )}
</div>
```

#### 2. Thêm Món (Modal)
```jsx
// Click product → Modal chọn số lượng
<Modal>
  <ProductImage />
  <ProductName />
  <ProductPrice />
  
  <QuantityStepper />  // 1, 2, 3...
  <NoteInput />        // Ít đá, không đường...
  
  <Total />            // = price × qty
  
  <Button onClick={addItem}>
    Thêm vào đơn
  </Button>
</Modal>
```

#### 3. Danh Sách Món
```jsx
// Chia 2 section
<div className="items-pending">
  <header>Chờ mang ra ({pendingCount})</header>
  {pendingItems.map(item => (
    <OrderItem
      item={item}
      actions={[
        <Button onClick={deleteItem}>🗑️</Button>  // Chỉ khi PENDING
      ]}
    />
  ))}
</div>

<div className="items-served">
  <header>Đã mang ra ({servedCount})</header>
  {servedItems.map(item => (
    <OrderItem item={item} readOnly />  // Không có nút
  ))}
</div>
```

#### 4. Xóa Món
```javascript
const handleDeleteItem = async (itemId) => {
  // Validate: chỉ xóa PENDING
  const item = session.orders[0].items.find(i => i.id === itemId);
  if (item.status !== 'PENDING') {
    toast.error('Không thể xóa món đã mang ra');
    return;
  }
  
  DELETE /api/pos/sessions/{id}/items/{itemId}
  // WebSocket event ORDER_ITEM_DELETED → update
};
```

#### 5. Realtime Update
```javascript
const handleItemEvent = useCallback((event) => {
  // Backend send toàn bộ sessionData
  if (event.sessionData) {
    setSession(event.sessionData);
  }
}, []);

// WebSocket subscribe
/topic/tenant/{tenantId}/session/{sessionId}
  - ORDER_ITEM_ADDED
  - ORDER_ITEM_DELETED
  - ORDER_ITEM_SERVED
  - SESSION_UPDATED (confirm / reject / pay)
```

---

## Screen 3: Order Pending (OPTIONAL)

Khách KHÔNG TÀI KHOẢN:
- Tạo session (status PENDING)
- Thêm món
- Đợi nhân viên xác nhận

**UI:**
```
┌──────────────────────┐
│ ⏳ Chờ xác nhận      │
│                      │
│ Nhân viên đang       │
│ xem xét đơn...       │
│                      │
│ Vui lòng đợi         │
└──────────────────────┘
```

---

## Screen 4: Order History (KHÁCH CÓ TÀI KHOẢN ONLY)

URL: `/customer/history`

### List View
```
┌─────────────────────────────────────┐
│ Lịch Sử Gọi Món                     │
├─────────────────────────────────────┤
│ 01/01/2026 14:30                    │
│ Bàng 5 • 6 món • 250,000đ           │
│ ✓ COMPLETED                         │
├─────────────────────────────────────┤
│ 01/01/2026 13:00                    │
│ Bàng 2 • 3 món • 125,000đ           │
│ ✓ COMPLETED                         │
└─────────────────────────────────────┘
```

### Detail View
```
┌─────────────────────────────────────┐
│ Order #123 - 01/01/2026 14:30       │
├─────────────────────────────────────┤
│ Bàng: 5                             │
│ Trạng thái: COMPLETED               │
├─────────────────────────────────────┤
│ Cà phê sữa x2 ... 70,000đ            │
│ Nước cam x3 ...  45,000đ             │
│ Bánh mì x1 ...   35,000đ             │
├─────────────────────────────────────┤
│ TỔNG: 250,000đ                      │
│ Thanh toán: CASH                    │
│ Ngày: 01/01/2026 14:45              │
└─────────────────────────────────────┘
```

---

## Security Rules

### Khách KHÔNG TÀI KHOẢN
- ❌ Không xác thực
- ✅ Validate `sessionId` từ URL
- ✅ Chỉ thao tác với session này

### Khách CÓ TÀI KHOẢN
- ✅ Login → JWT token
- ✅ `userId` từ token
- ✅ Lịch sử filter by `userId` (backend)
- ⚠️ **LƯU Ý:** Vẫn KHÔNG thể serve / thanh toán!

---

## API Calls (From Guest Perspective)

| Action | Endpoint | Method | Khi nào |
|---|---|---|---|
| Load session | `/api/pos/sessions/{id}` | GET | Vào page |
| Thêm món | `/api/pos/sessions/{id}/items` | POST | Click "Thêm vào đơn" |
| Xóa món | `/api/pos/sessions/{id}/items/{id}` | DELETE | Click 🗑️ (PENDING only) |
| Cập nhật lượng | `/api/pos/sessions/{id}/items/{id}` | PATCH | Sửa số lượng (optional) |
| --- | --- | --- | --- |
| Xem lịch sử | `/api/customer/orders` | GET | Khách có tài khoản |

---

## Frontend Routes (Guest)

```
/session/:id                  # Trang gọi món (entry=qr)
?role=customer                # Hint: khách
?entry=qr                     # Hint: quét QR

/customer/login               # Login (khách có tài khoản)
/customer/history             # Lịch sử (khách đã login)
/customer/history/:id         # Chi tiết order
```

---

## Realtime Flow

```
Frontend (Guest)                    Backend                 WebSocket
    │                                │                           │
    ├─ GET /session/{id} ──────────>│                           │
    │<────── SessionResponse ────────┤                           │
    │                                │                           │
    ├─ Subscribe ───────────────────────────────────────────────>│
    │<─────── CONNECTED ─────────────────────────────────────────┤
    │                                │                           │
    ├─ POST /items (Add Món) ──────>│                           │
    │                                ├─ ORDER_ITEM_ADDED ──────>│
    │<────── Success ────────────────┤                           │
    │<─────── event (full data) ─────────────────────────────────┤
    │ (UI update)                    │                           │
    │                                │                           │
    │                    [Nhân viên serve...]                    │
    │                                │                           │
    │<─────── ORDER_ITEM_SERVED ────────────────────────────────┤
    │ (UI update PENDING→SERVED)     │                           │
    │                                │                           │
```

---

## Missing Endpoints (If Needed)

### 1. Get Menu by Tenant
```
GET /api/pos/menu (public)
```
Status: ✅ Có, nhưng check `/api/public/menu`

### 2. Get Guest Order History
```
GET /api/customer/orders
Authorization: Bearer {token}
Response: List<OrderSummary>
```
Status: ❌ Chưa có → cần triển khai

### 3. Get Guest Order Detail
```
GET /api/customer/orders/{id}
Authorization: Bearer {token}
Response: OrderDetailDto
```
Status: ❌ Chưa có → cần triển khai

---

## Implementation Checklist

### Phase 1: Khách KHÔNG TÀI KHOẢN (MVP)
- [ ] Page `/session/:id`
  - [ ] Fetch session
  - [ ] Subscribe WebSocket
  - [ ] Display menu
  - [ ] Modal thêm món
  - [ ] Hiển thị list items (PENDING / SERVED)
  - [ ] Delete PENDING item
  - [ ] Realtime update

### Phase 2: Khách CÓ TÀI KHOẢN
- [ ] `/customer/login`
- [ ] `/customer/history`
- [ ] `/customer/orders/:id`
- [ ] Backend endpoint `GET /api/customer/orders`
- [ ] Backend endpoint `GET /api/customer/orders/{id}`

### Phase 3: UX Improvements
- [ ] Loading states
- [ ] Error handling
- [ ] Toast notifications
- [ ] Offline detection

