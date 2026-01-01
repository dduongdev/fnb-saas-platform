# Client-POS Parity - Đồng Nhất Giao Diện Khách & Nhân Viên

## Mục Tiêu

Đảm bảo giao diện **KHÁCH** (CustomerMenuPage) và giao diện **/POS** (OrderSessionPage) là **HAI VIEW KHÁC NHAU** nhưng **PHẢN ÁNH CÙNG MỘT TRẠNG THÁI SESSION** theo thời gian thực.

---

## So Sánh Chức Năng

| Chức năng | Client (Khách) | /POS (Nhân viên) | Realtime? |
|-----------|----------------|------------------|-----------|
| **Xem menu** | ✅ Full menu | ✅ Full menu | N/A |
| **Thêm món** | ✅ Chọn số lượng | ✅ Chọn số lượng + ghi chú | ✅ Đồng bộ 2 chiều |
| **Xóa món PENDING** | ✅ Chỉ xóa món mình gọi | ✅ Xóa bất kỳ món nào | ✅ Đồng bộ 2 chiều |
| **Xem món PENDING** | ✅ Hiển thị danh sách | ✅ Hiển thị danh sách | ✅ Realtime update |
| **Xem món SERVED** | ✅ Hiển thị danh sách | ✅ Hiển thị danh sách | ✅ Realtime update |
| **Serve món** | ❌ Không có quyền | ✅ Đánh dấu đã mang ra | ✅ Client thấy ngay |
| **Cập nhật số lượng** | ❌ Không hỗ trợ | ✅ Sửa số lượng món PENDING | ✅ Client thấy ngay |
| **Thanh toán** | ❌ Không có quyền | ✅ Pay & close session | ❌ (Session kết thúc) |
| **Xem tổng tiền** | ✅ Hiển thị realtime | ✅ Hiển thị realtime | ✅ Đồng bộ 2 chiều |

---

## Định Nghĩa Nghiệp Vụ

### Session Status

| Status | Ý nghĩa | Client View | /POS View |
|--------|---------|-------------|-----------|
| `PENDING` | Chờ nhân viên xác nhận | "Đang chờ xác nhận" | "Order mới cần xác nhận" |
| `ACTIVE` | Đang phục vụ | "Order đã xác nhận" | "Đang phục vụ" |
| `COMPLETED` | Đã thanh toán | (Hidden) | "Đã hoàn thành" |
| `CANCELLED` | Bị hủy | "Order bị từ chối" | "Đã hủy" |

### Order Item Status

| Status | Ý nghĩa | Client Label | /POS Label | Có thể xóa? |
|--------|---------|--------------|------------|-------------|
| `PENDING` | Đã gọi, chưa mang ra | "🕐 Đang chuẩn bị" | "⏳ Chờ mang ra" | ✅ |
| `SERVED` | Đã mang ra | "✅ Đã mang ra" | "✅ Đã mang" | ❌ |

**Lưu ý**: 
- KHÔNG có status `PREPARING`
- KHÔNG có status `CANCELLED` (chỉ xóa item)
- KHÔNG hỗ trợ split bill

---

## UI Component Mapping

### 1. Order Items List

#### Client (CustomerMenuPage.jsx)
```jsx
{/* Pending Items */}
<div className="items-section">
    <div className="section-header pending">
        <Clock size={16} />
        <span>Đang chuẩn bị ({pendingItems.length})</span>
    </div>
    {pendingItems.map(item => (
        <div className="order-item">
            <div className="item-info">
                <strong>{item.productName}</strong>
                <span>x{item.quantity} - {formatPrice(item.total)}</span>
            </div>
            <button onClick={() => handleRemoveItem(item)}>
                <X />
            </button>
        </div>
    ))}
</div>

{/* Served Items */}
<div className="items-section">
    <div className="section-header served">
        <CheckCircle size={16} />
        <span>Đã mang ra ({servedItems.length})</span>
    </div>
    {servedItems.map(item => (
        <div className="order-item served">
            <div className="item-info">
                <strong>{item.productName}</strong>
                <span>x{item.quantity} - {formatPrice(item.total)}</span>
            </div>
            <CheckCircle className="served-icon" />
        </div>
    ))}
</div>
```

#### /POS (OrderSessionPage.jsx)
```jsx
{/* Pending Items */}
<div className="items-group">
    <div className="group-header pending">
        <Clock size={16} />
        <span>Chờ mang ra ({pendingItems.length})</span>
    </div>
    {pendingItems.map(item => (
        <OrderItemRow
            item={item}
            isStaff={true}
            onUpdateQuantity={(delta) => handleUpdateQuantity(item, delta)}
            onRemove={() => handleRemoveItem(item)}
            onServe={() => handleServeItem(item)}
        />
    ))}
</div>

{/* Served Items */}
<div className="items-group">
    <div className="group-header served">
        <Check size={16} />
        <span>Đã mang ra ({servedItems.length})</span>
    </div>
    {servedItems.map(item => (
        <OrderItemRow
            item={item}
            isStaff={true}
            readOnly
        />
    ))}
</div>
```

**Điểm khác biệt**:
- Client: Nút xóa đơn giản
- /POS: Có nút +/- số lượng, nút serve, nút xóa

### 2. WebSocket Integration

#### Cả 2 đều dùng CÙNG hook và logic:

```javascript
const handleSessionUpdate = useCallback((data) => {
    setSession(data);
}, []);

const handleItemEvent = useCallback((event) => {
    setSession(prev => {
        // ... IDENTICAL logic ...
        switch (event.type) {
            case 'ORDER_ITEM_ADDED':
                newItems.push(event.item);
                break;
            case 'ORDER_ITEM_DELETED':
                newItems = newItems.filter(i => i.id !== event.item.id);
                break;
            case 'ORDER_ITEM_SERVED':
            case 'ORDER_ITEM_UPDATED':
                newItems = newItems.map(i => 
                    i.id === event.item.id ? { ...i, ...event.item } : i
                );
                break;
        }
        return { ...prev, totalAmount: event.newTotalAmount, orders: [{ ...order, items: newItems }] };
    });
}, []);

useSessionByIdWebSocket(sessionId, handleSessionUpdate, handleItemEvent);
```

**Nguyên tắc**: 
- ✅ Logic xử lý event PHẢI GIỐNG NHAU
- ✅ Cùng subscribe topic `/topic/tenant/{tenantId}/session/{sessionId}`
- ✅ Cùng update state pattern

---

## API Endpoint Mapping

| Action | Client Endpoint | /POS Endpoint | Note |
|--------|----------------|---------------|------|
| Thêm món | `POST /api/pos/public/sessions/{id}/items` | `POST /api/pos/sessions/{id}/items` | Cùng logic backend |
| Xóa món | `DELETE /api/pos/public/sessions/{id}/items/{itemId}` | `DELETE /api/pos/sessions/{id}/items/{itemId}` | Cùng logic backend |
| Xem session | `GET /api/pos/public/sessions/{id}` | `GET /api/pos/sessions/{id}` | DTO khác nhau |
| Serve món | ❌ Không có quyền | `POST /api/pos/sessions/{id}/items/{itemId}/serve` | Staff only |
| Update quantity | ❌ Không có quyền | `PATCH /api/pos/sessions/{id}/items/{itemId}` | Staff only |

**Backend Security**: 
- Public endpoints (`/public/*`) không cần authentication
- Staff endpoints yêu cầu Keycloak JWT token

---

## State Structure

Cả Client và /POS đều dùng **CÙNG data structure** từ `SessionResponse`:

```javascript
{
    "sessionId": 123,
    "status": "ACTIVE",
    "tables": [
        { "id": 1, "name": "Bàn 1" }
    ],
    "orders": [
        {
            "id": 456,
            "items": [
                {
                    "id": 789,
                    "productId": 101,
                    "productName": "Cà phê sữa đá",
                    "quantity": 2,
                    "price": 25000,
                    "status": "PENDING",
                    "total": 50000
                },
                {
                    "id": 790,
                    "productId": 102,
                    "productName": "Trà đào",
                    "quantity": 1,
                    "price": 30000,
                    "status": "SERVED",
                    "total": 30000
                }
            ]
        }
    ],
    "totalAmount": 80000
}
```

---

## Luồng Nghiệp Vụ Điển Hình

### Scenario 1: Khách gọi món → Nhân viên serve

```
[Client] Khách chọn "Cà phê sữa đá" x2, click "Gửi gọi món"
    ↓
[Client] POST /api/pos/public/sessions (tạo PENDING session)
    ↓
[Backend] Tạo session với items, status=PENDING
    ↓
[Backend] Emit ORDER_ITEM_ADDED events
    ↓
[/POS] WebSocket nhận event → Hiển thị order mới trong queue
    ↓
[/POS] Nhân viên click "Xác nhận"
    ↓
[Backend] Session.status = ACTIVE
    ↓
[Client] WebSocket nhận SESSION_UPDATED → Chuyển view "Order đã xác nhận"
    ↓
[/POS] Nhân viên pha xong, click "Serve" trên item
    ↓
[Backend] Item.status = SERVED
    ↓
[Backend] Emit ORDER_ITEM_SERVED event
    ↓
[Client] WebSocket nhận event → Item chuyển từ "Đang chuẩn bị" → "Đã mang ra"
```

### Scenario 2: Khách và nhân viên cùng gọi món

```
[Client] Khách gọi "Trà đào" x1
    ↓
[Backend] Emit ORDER_ITEM_ADDED
    ↓
[/POS] Thấy item mới ngay lập tức
    ↓
[/POS] Nhân viên gọi thêm "Bánh mì" x2 (khách yêu cầu bằng lời)
    ↓
[Backend] Emit ORDER_ITEM_ADDED
    ↓
[Client] Thấy "Bánh mì" xuất hiện ngay lập tức
    ↓
[Client + /POS] Cùng thấy totalAmount = 30000 + 40000 = 70000
```

### Scenario 3: Khách xóa món → /POS thấy ngay

```
[Client] Khách click nút X trên "Trà đào"
    ↓
[Client] DELETE /api/pos/public/sessions/{id}/items/{itemId}
    ↓
[Backend] Xóa item, emit ORDER_ITEM_DELETED
    ↓
[/POS] WebSocket nhận event → Item biến mất, totalAmount giảm
    ↓
[Client] WebSocket nhận event → Confirm item đã xóa
```

---

## Testing Strategy

### Unit Tests

#### Client (CustomerMenuPage)
- [ ] handleItemEvent() cập nhật state đúng cho mỗi event type
- [ ] handleRemoveItem() gọi API đúng endpoint
- [ ] UI hiển thị đúng số lượng PENDING vs SERVED

#### /POS (OrderSessionPage)
- [ ] handleItemEvent() cập nhật state đúng (GIỐNG client)
- [ ] handleServeItem() chỉ work với PENDING items
- [ ] handleUpdateQuantity() gọi API và update local state

### Integration Tests

#### Realtime Sync
- [ ] Client thêm món → /POS thấy trong <1s
- [ ] /POS serve món → Client thấy status update trong <1s
- [ ] Khách xóa món → /POS thấy item biến mất trong <1s

#### Multi-Client
- [ ] 2 khách cùng session → Cả 2 thấy món của nhau
- [ ] 1 khách + 1 staff → Thao tác của nhau đồng bộ

### E2E Tests (Manual)

| Test Case | Steps | Expected |
|-----------|-------|----------|
| **TC1: Khách gọi món** | 1. Client thêm 2 món<br>2. Check /POS | /POS thấy 2 món mới ngay |
| **TC2: Staff serve** | 1. /POS click Serve<br>2. Check Client | Client thấy món → "Đã mang ra" |
| **TC3: Khách xóa món** | 1. Client xóa món PENDING<br>2. Check /POS | /POS thấy món biến mất |
| **TC4: Tổng tiền đồng bộ** | 1. Thêm/xóa món từ bất kỳ phía<br>2. Check cả 2 UI | Tổng tiền GIỐNG NHAU |

---

## Troubleshooting Parity Issues

### Vấn đề: Client và /POS hiển thị khác nhau

**Nguyên nhân**:
- Một trong hai không subscribe WebSocket
- Logic `handleItemEvent()` khác nhau

**Kiểm tra**:
```javascript
// Client
console.log('[Client] Items:', orderItems);

// /POS
console.log('[POS] Items:', orderItems);

// So sánh: Phải GIỐNG NHAU
```

### Vấn đề: Tổng tiền lệch

**Nguyên nhân**:
- Một bên tính toán local, một bên dùng `event.newTotalAmount`

**Giải pháp**:
```javascript
// ✅ ĐÚNG: Cả 2 đều dùng giá trị từ backend
setSession(prev => ({
    ...prev,
    totalAmount: event.newTotalAmount // Backend-calculated
}));

// ❌ SAI: Tự tính
setSession(prev => ({
    ...prev,
    totalAmount: prev.totalAmount + item.price * item.quantity
}));
```

### Vấn đề: Delay cảm nhận

**Nguyên nhân**:
- WebSocket reconnection
- Backend emit chậm
- Frontend render chậm

**Debug**:
```javascript
// Đo latency
const handleItemEvent = useCallback((event) => {
    const latency = Date.now() - new Date(event.timestamp).getTime();
    console.log(`[Latency] ${latency}ms`);
    // ...
}, []);
```

**Target**: < 500ms từ action → UI update

---

## Style Guide

### Terminology

| Backend | Client Label | /POS Label |
|---------|--------------|------------|
| `PENDING` | "Đang chuẩn bị" | "Chờ mang ra" |
| `SERVED` | "Đã mang ra" | "Đã mang" |
| `totalAmount` | "Tổng cộng" | "Tổng tiền" |

### Icons

| Status | Client Icon | /POS Icon |
|--------|-------------|-----------|
| PENDING | `<Clock>` 🕐 | `<Clock>` ⏳ |
| SERVED | `<CheckCircle>` ✅ | `<Check>` ✅ |
| Delete | `<X>` ❌ | `<Trash2>` 🗑️ |

### Colors (CSS Variables)

```css
/* Pending */
.pending {
    background: #fff7ed;
    color: #c2410c;
}

/* Served */
.served {
    background: #f0fdf4;
    color: #15803d;
}
```

---

## Maintenance Checklist

Khi thêm/sửa chức năng liên quan đến order items:

- [ ] Backend: Thêm event mới trong `SessionEvent`
- [ ] Backend: Emit event sau operation trong `SessionService`
- [ ] Client: Cập nhật `handleItemEvent()` trong `CustomerMenuPage.jsx`
- [ ] /POS: Cập nhật `handleItemEvent()` trong `OrderSessionPage.jsx`
- [ ] Đảm bảo logic xử lý event GIỐNG NHAU ở cả 2 file
- [ ] Test realtime sync cả 2 chiều
- [ ] Update docs này

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-01 | Initial parity documentation |
| 1.1 | 2026-01-01 | Added `removeCustomerItem` support |

---

## References

- [Realtime Session Sync](./realtime-session-sync.md) - Technical details
- Client Implementation: `frontend/src/pages/customer/CustomerMenuPage.jsx`
- /POS Implementation: `frontend/src/pages/pos/OrderSessionPage.jsx`
- Backend Events: `backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java`
