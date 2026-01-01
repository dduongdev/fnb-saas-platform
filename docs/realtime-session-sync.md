# Realtime Session Sync - Đồng Bộ Phiên Thời Gian Thực

## Tổng Quan

Hệ thống F&B multi-tenant sử dụng **SESSION làm trung tâm** cho gọi món, với cơ chế đồng bộ realtime hai chiều giữa:
- **Giao diện khách (Client)**: Khách hàng quét QR và gọi món
- **Giao diện nhân viên (/pos)**: Nhân viên quản lý order và xác nhận món

> **Nguyên Tắc Cốt Lõi**: Bất kỳ thay đổi nào trên order/session ở một phía thì phía còn lại PHẢI THẤY NGAY LẬP TỨC.

---

## Kiến Trúc WebSocket

### Backend: Spring WebSocket + STOMP

#### Topics
```
/topic/tenant/{tenantId}/session/{sessionId}  - Session-specific updates
/topic/tenant/{tenantId}/table/{tableId}      - Table-specific updates (legacy)
/topic/tenant/{tenantId}/tables               - All tables status
/topic/tenant/{tenantId}/notifications        - Staff notifications
/topic/tenant/{tenantId}/pending-sessions     - Pending order queue
```

#### Event Types
| Event Type | Trigger | Payload |
|-----------|---------|---------|
| `ORDER_ITEM_ADDED` | Thêm món mới | `SessionEvent` with item details |
| `ORDER_ITEM_DELETED` | Xóa món PENDING | `SessionEvent` with itemId |
| `ORDER_ITEM_SERVED` | Đánh dấu món đã mang ra | `SessionEvent` with updated item |
| `ORDER_ITEM_UPDATED` | Cập nhật số lượng | `SessionEvent` with updated item |
| `SESSION_UPDATED` | Session state change | Full `SessionResponse` |

#### SessionEvent Structure
```java
{
    "type": "ORDER_ITEM_ADDED",
    "sessionId": 123,
    "item": {
        "id": 456,
        "productId": 789,
        "productName": "Cà phê sữa đá",
        "quantity": 2,
        "price": 25000,
        "status": "PENDING",
        "total": 50000
    },
    "newTotalAmount": 150000,
    "sessionData": { /* Full SessionResponse for fallback */ },
    "timestamp": "2026-01-01T10:30:00"
}
```

### Frontend: Custom STOMP Client

**File**: `frontend/src/hooks/useWebSocket.js`

#### Hook: `useSessionByIdWebSocket`
```javascript
useSessionByIdWebSocket(
    sessionId,           // Session ID to subscribe
    onSessionUpdate,     // Callback for full session updates
    onItemEvent,         // Callback for granular item events
    tenantIdOverride     // Optional: for public access
)
```

**Usage Pattern**:
```javascript
const handleSessionUpdate = useCallback((data) => {
    setSession(data);
}, []);

const handleItemEvent = useCallback((event) => {
    // Update state incrementally based on event type
    setSession(prev => {
        const order = prev.orders[0];
        let newItems = [...order.items];
        
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
        
        return {
            ...prev,
            totalAmount: event.newTotalAmount,
            orders: [{ ...order, items: newItems }]
        };
    });
}, []);

useSessionByIdWebSocket(sessionId, handleSessionUpdate, handleItemEvent);
```

---

## Luồng Đồng Bộ Realtime

### 1. KHÁCH → /POS

#### Khách thêm món
```
[Client] POST /api/pos/public/sessions/{sessionId}/items
    ↓
[Backend] SessionService.addCustomerItems()
    ↓
[Backend] notifyItemEvent("ORDER_ITEM_ADDED", session, item)
    ↓
[WebSocket] → /topic/tenant/{tenantId}/session/{sessionId}
    ↓
[/POS Frontend] handleItemEvent() → Update state
    ↓
[/POS UI] Hiển thị món mới NGAY LẬP TỨC
```

#### Khách xóa món PENDING
```
[Client] DELETE /api/pos/public/sessions/{sessionId}/items/{itemId}
    ↓
[Backend] SessionService.removeItem()
    ↓
[Backend] notifyDeleteEvent(session, itemId, newTotal)
    ↓
[WebSocket] → /topic/tenant/{tenantId}/session/{sessionId}
    ↓
[/POS Frontend] handleItemEvent() → Remove item from state
    ↓
[/POS UI] Món biến mất NGAY LẬP TỨC
```

### 2. /POS → KHÁCH

#### Nhân viên xác nhận món SERVED
```
[/POS] POST /api/pos/sessions/{sessionId}/items/{itemId}/serve
    ↓
[Backend] SessionService.serveItem()
    ↓
[Backend] notifyItemEvent("ORDER_ITEM_SERVED", session, item)
    ↓
[WebSocket] → /topic/tenant/{tenantId}/session/{sessionId}
    ↓
[Client Frontend] handleItemEvent() → Update item status
    ↓
[Client UI] Món chuyển sang "Đã mang ra" NGAY LẬP TỨC
```

#### Nhân viên cập nhật số lượng
```
[/POS] PATCH /api/pos/sessions/{sessionId}/items/{itemId}
    ↓
[Backend] SessionService.updateItemQuantity()
    ↓
[Backend] notifyItemEvent("ORDER_ITEM_UPDATED", session, item)
    ↓
[WebSocket] → /topic/tenant/{tenantId}/session/{sessionId}
    ↓
[Client Frontend] handleItemEvent() → Update quantity
    ↓
[Client UI] Số lượng thay đổi NGAY LẬP TỨC
```

---

## Quy Tắc Đồng Bộ

### ✅ DO (Nên Làm)

1. **Load Initial State bằng REST**
   ```javascript
   const session = await getSession(sessionId);
   setSession(session);
   ```

2. **Sau đó CHỈ cập nhật bằng WebSocket events**
   ```javascript
   useSessionByIdWebSocket(sessionId, handleSessionUpdate, handleItemEvent);
   ```

3. **Update state trực tiếp từ event**
   - KHÔNG gọi lại API sau khi emit event
   - Tin tưởng event payload từ backend

4. **Xử lý race condition bằng `sessionData` trong event**
   - Nếu event bị mất, fallback vào `event.sessionData`

### ❌ DON'T (Không Nên)

1. **KHÔNG polling API định kỳ**
   ```javascript
   // ❌ BAD
   setInterval(() => getSession(sessionId), 3000);
   ```

2. **KHÔNG reload toàn bộ session sau mỗi thao tác**
   ```javascript
   // ❌ BAD
   await addItems(...);
   const updated = await getSession(sessionId); // Duplicate!
   ```

3. **KHÔNG tự suy luận trạng thái**
   ```javascript
   // ❌ BAD
   setSession(prev => ({
       ...prev,
       totalAmount: prev.totalAmount + item.price // Sai nếu có người khác cũng thêm món!
   }));
   
   // ✅ GOOD
   setSession(prev => ({
       ...prev,
       totalAmount: event.newTotalAmount // Backend tính sẵn
   }));
   ```

4. **KHÔNG cache state cũ khi có conflict**
   - Luôn ưu tiên state mới nhất từ backend

---

## Troubleshooting

### Vấn đề: Client không thấy thay đổi từ /pos

**Nguyên nhân**:
- WebSocket chưa connect
- Subscribe sai topic
- Event handler không update state

**Kiểm tra**:
```javascript
// Check WebSocket connection
console.log('[WS] Session subscription active'); // Phải thấy log này

// Check event callback
const handleItemEvent = useCallback((event) => {
    console.log('[WS] Item event:', event); // Phải thấy log khi có thay đổi
    // ...
}, []);
```

### Vấn đề: /pos không thấy thay đổi từ client

**Nguyên nhân**:
- Backend không emit event sau thao tác
- Frontend không subscribe đúng sessionId

**Kiểm tra Backend**:
```java
// SessionService.java - Sau mỗi operation phải có:
notifyItemEvent("ORDER_ITEM_ADDED", session, item);
```

### Vấn đề: State bị lệch (stale data)

**Nguyên nhân**:
- Event bị mất do network
- State update không immutable

**Giải pháp**:
```javascript
// Fallback: reload từ API khi phát hiện lỗi
try {
    await removeItem(...);
} catch (error) {
    console.error('Event may be lost, reloading...');
    const fresh = await getSession(sessionId);
    setSession(fresh);
}
```

---

## Testing Checklist

### Kiểm tra đồng bộ Client → /POS

- [ ] Khách thêm món → /POS thấy ngay
- [ ] Khách xóa món PENDING → /POS thấy món biến mất
- [ ] Khách gọi nhiều món cùng lúc → /POS thấy đầy đủ

### Kiểm tra đồng bộ /POS → Client

- [ ] Nhân viên serve món → Client thấy status chuyển SERVED
- [ ] Nhân viên cập nhật số lượng → Client thấy quantity thay đổi
- [ ] Nhân viên xóa món → Client thấy món biến mất

### Kiểm tra nhiều client cùng session

- [ ] 2 khách cùng gọi món → Cả 2 thấy đủ món của nhau
- [ ] Khách A xóa món → Khách B thấy món biến mất
- [ ] /POS serve món → Tất cả khách đều thấy status update

### Kiểm tra edge cases

- [ ] WebSocket disconnect → Auto-reconnect → State đồng bộ
- [ ] Thao tác nhanh liên tiếp → Không bị race condition
- [ ] Reload trang → Load lại state đúng từ API

---

## Performance

### Bandwidth Optimization

**Event-based** (hiện tại):
- Chỉ gửi item thay đổi (~200 bytes/event)
- Giảm 80% traffic so với full session update

**Full Session Update** (legacy):
- Gửi toàn bộ session (~2KB) mỗi lần
- Sử dụng khi cần đồng bộ toàn diện (confirm session, pay session)

### Scalability

- **Connection per session**: 1 WebSocket cho mỗi client/pos
- **Max concurrent sessions**: Giới hạn bởi backend thread pool (default 200)
- **Reconnection strategy**: Exponential backoff (3s, 6s, 12s...)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-01 | Initial implementation với event-based sync |
| 1.1 | 2026-01-01 | Thêm `removeCustomerItem` cho phép khách xóa món |

---

## References

- Backend: `SessionService.java` - Event emitters
- Frontend: `useWebSocket.js` - WebSocket hooks
- Frontend: `OrderSessionPage.jsx` - Reference implementation (staff)
- Frontend: `CustomerMenuPage.jsx` - Reference implementation (client)
