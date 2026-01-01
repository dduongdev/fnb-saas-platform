# WebSocket Testing Guide

## Kiểm Tra Kết Nối WebSocket

### 1. Kiểm Tra Backend

#### A. Xem logs WebSocket stats
```powershell
docker-compose logs -f backend | Select-String "WebSocketSession"
```

**Expected output mỗi 60s**:
```
WebSocketSession[X current WS(X)-HttpStream(0)-HttpPoll(0), ...
```
- `X current WS(X)`: Phải > 0 khi có client connect

#### B. Test SockJS endpoint
```powershell
curl http://localhost:8080/ws/info
```

**Expected**: JSON response với enabled transports
```json
{
  "entropy": ...,
  "origins": [...],
  "cookie_needed": true,
  "websocket": true
}
```

### 2. Kiểm Tra Frontend

#### A. Browser Console Logs

Mở **DevTools Console** (F12), phải thấy:

```
[STOMP Debug] Opening Web Socket...
[STOMP Debug] Web Socket Opened...
[STOMP Debug] >>> CONNECT
[STOMP Debug] <<< CONNECTED
[STOMP] Connected: [object Object]
[STOMP] Subscribed to: /topic/tenant/{tenantId}/session/{sessionId}
[WS ByID] Session subscription active for session: {sessionId}
```

#### B. Network Tab

1. Mở **DevTools Network** tab
2. Filter: `ws` hoặc `sockjs`
3. Phải thấy:
   - URL: `ws://localhost:8080/ws/xxx/websocket` (status 101 Switching Protocols)
   - Hoặc `http://localhost:8080/ws/xxx/xhr_streaming` (nếu WebSocket không khả dụng)

#### C. Manual Test với Browser Console

```javascript
// Tạo client test
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const socket = new SockJS('http://localhost:8080/ws');
const client = new Client({
    webSocketFactory: () => socket,
    debug: console.log,
    onConnect: () => {
        console.log('CONNECTED!');
        client.subscribe('/topic/tenant/demo-tenant/notifications', (msg) => {
            console.log('Received:', JSON.parse(msg.body));
        });
    }
});
client.activate();
```

### 3. Test End-to-End Realtime

#### Scenario 1: Khách gọi món → /pos thấy ngay

**Setup**:
1. Tab A: Client view `/customer/1/demo-tenant`
2. Tab B: POS view `/pos/session/{sessionId}?role=staff`

**Steps**:
1. Tab A: Thêm món "Cà phê" x2
2. **Check Tab A Console**:
   ```
   [WS ByID] Received message: { type: "ORDER_ITEM_ADDED", ... }
   ```
3. **Check Tab B Console**:
   ```
   [WS] Received message: { type: "ORDER_ITEM_ADDED", ... }
   ```
4. **Check Tab B UI**: Món xuất hiện trong "Chờ mang ra"

**Success criteria**: 
- ✅ Latency < 500ms
- ✅ Không cần refresh
- ✅ State đồng bộ

#### Scenario 2: Staff serve món → khách thấy ngay

**Steps**:
1. Tab B (/pos): Click nút "✓" (Serve) trên món
2. **Check Tab B Console**:
   ```
   [WS] Received message: { type: "ORDER_ITEM_SERVED", item: {...} }
   ```
3. **Check Tab A Console**:
   ```
   [WS ByID] Received message: { type: "ORDER_ITEM_SERVED", item: {...} }
   ```
4. **Check Tab A UI**: Món chuyển từ "Đang chuẩn bị" → "Đã mang ra"

**Success criteria**: 
- ✅ Latency < 500ms
- ✅ Status update đúng
- ✅ Cả 2 UI đồng bộ

### 4. Troubleshooting

#### Problem: "WebSocket connection failed"

**Check 1**: Backend logs
```powershell
docker-compose logs backend | Select-String "error" -Context 5
```

**Check 2**: Security config
```java
// SecurityConfig.java - phải có:
.requestMatchers("/ws/**").permitAll()
```

**Check 3**: CORS config
```java
// WebSocketConfig.java - phải có:
.setAllowedOriginPatterns("*")
```

#### Problem: "Connected but no messages"

**Check 1**: Topic subscription
```javascript
// Console log phải thấy:
[STOMP] Subscribed to: /topic/tenant/{tenantId}/session/{sessionId}
```

**Check 2**: Backend emit events
```java
// SessionService.java - sau mỗi operation phải có:
notifyItemEvent("ORDER_ITEM_ADDED", session, item);
```

**Check 3**: TenantId mismatch
```javascript
// Frontend và backend phải dùng CÙNG tenantId
console.log('Frontend tenantId:', activeTenantId);
// Backend logs:
// TenantContext.getTenantId() = "demo-tenant"
```

#### Problem: "Messages received but state not updating"

**Check**: Event handler logic
```javascript
const handleItemEvent = useCallback((event) => {
    console.log('[DEBUG] Event type:', event.type);
    console.log('[DEBUG] Current items:', session?.orders[0]?.items);
    // Phải thấy state update sau mỗi event
}, []);
```

### 5. Performance Monitoring

#### Measure Latency

```javascript
const handleItemEvent = useCallback((event) => {
    if (event.timestamp) {
        const latency = Date.now() - new Date(event.timestamp).getTime();
        console.log(`[Latency] ${latency}ms`);
    }
    // ...
}, []);
```

**Target**: < 500ms

#### Connection Health

```javascript
client.onWebSocketClose = () => {
    console.warn('[WS] Connection closed, will reconnect...');
};

client.onWebSocketError = (error) => {
    console.error('[WS] Connection error:', error);
};
```

### 6. Production Checklist

- [ ] WebSocket endpoint `/ws/**` allowed in security config
- [ ] CORS configured for production domain
- [ ] SockJS fallback enabled
- [ ] Heartbeat configured (10s/10s)
- [ ] Auto-reconnect enabled
- [ ] Error logging implemented
- [ ] Performance monitoring active
- [ ] Load testing completed

### 7. Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| `403 Forbidden on /ws` | Add `/ws/**` to permitAll in SecurityConfig |
| `404 Not Found on /ws/info` | Check WebSocketConfig registered |
| `Connection timeout` | Check firewall, reverse proxy settings |
| `Messages not received` | Verify topic subscription matches backend emit |
| `State out of sync` | Check event handler logic, ensure immutable updates |
| `High latency` | Check network, consider using in-memory broker instead of SimpleBroker |

---

## Quick Verification Script

```javascript
// Run in Browser Console (DevTools)
const testWebSocket = () => {
    const socket = new SockJS('http://localhost:8080/ws');
    let connected = false;
    
    socket.onopen = () => {
        console.log('✅ SockJS connected');
        connected = true;
    };
    
    socket.onerror = (error) => {
        console.error('❌ SockJS error:', error);
    };
    
    socket.onclose = () => {
        console.log(connected ? '✅ SockJS closed gracefully' : '❌ SockJS failed to connect');
    };
    
    setTimeout(() => {
        socket.close();
    }, 2000);
};

testWebSocket();
```

**Expected**:
```
✅ SockJS connected
✅ SockJS closed gracefully
```
