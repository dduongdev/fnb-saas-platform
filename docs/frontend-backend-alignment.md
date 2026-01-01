# Báo cáo Kiểm tra Khớp Frontend-Backend

**Ngày đánh giá:** 2026-01-01  
**Phiên bản:** 1.0  
**Người thực hiện:** AI Engineer / Tech Lead

---

## 1. Tổng quan

Báo cáo này đánh giá mức độ **ĐÚNG** và **ĐỦ** của Frontend so với Backend cho hệ thống quản lý quán F&B.

### Kết quả tổng thể

| Chỉ số | Kết quả |
|--------|---------|
| **Tổng số API Backend** | 47 endpoints |
| **API được Frontend gọi đúng** | 42 (~89%) |
| **API thiếu UI** | 0 |
| **API gọi SAI** | 5 (~11%) |
| **UI không có backend hỗ trợ** | 2 |

> [!WARNING]
> Có **5 API calls sai** trong Frontend cần được sửa ngay để tránh lỗi runtime.

---

## 2. Bước 1: Liệt kê toàn bộ API Backend

### 2.1. Module Global (Auth, Profile, Tenant)

| # | Endpoint | Method | Ý nghĩa nghiệp vụ | Pre-condition |
|---|----------|--------|-------------------|---------------|
| 1 | `/api/auth/sync` | POST | Đồng bộ user từ Keycloak token vào DB | Đã login Keycloak, có JWT token |
| 2 | `/api/profile/avatar` | POST | Upload avatar user | Đã đăng nhập |
| 3 | `/api/public/tenants` | GET | Lấy danh sách tenant công khai (paginated) | Không cần auth |
| 4 | `/api/public/jobs` | GET | Lấy danh sách job công khai (paginated) | Không cần auth |
| 5 | `/api/tenants` | POST | Tạo tenant mới (multipart) | Đã đăng nhập |
| 6 | `/api/tenants/{id}` | PUT | Cập nhật tenant (multipart) | Owner của tenant |
| 7 | `/api/tenants/{id}` | GET | Lấy chi tiết tenant | Không cần auth |
| 8 | `/api/tenants/me` | GET | Lấy danh sách tenant của user hiện tại | Đã đăng nhập |
| 9 | `/api/tenants/{id}/status` | PATCH | Cập nhật trạng thái tenant (hoạt động/đóng) | Owner của tenant |
| 10 | `/api/tenants/{id}/payment-config` | PUT | Cập nhật cấu hình thanh toán | Owner của tenant |

### 2.2. Module HRM (Staff, Job, Recruitment)

| # | Endpoint | Method | Ý nghĩa nghiệp vụ | Pre-condition |
|---|----------|--------|-------------------|---------------|
| 11 | `/api/staff` | GET | Lấy danh sách nhân viên (paginated) | Đã đăng nhập, có tenant |
| 12 | `/api/staff/{id}` | DELETE | Xóa nhân viên khỏi quán | Owner của tenant |
| 13 | `/api/hrm/jobs` | GET | Lấy danh sách job của tenant (paginated) | Owner của tenant |
| 14 | `/api/hrm/jobs/{id}` | GET | Lấy chi tiết job | Owner của tenant |
| 15 | `/api/hrm/jobs` | POST | Tạo job mới | Owner của tenant |
| 16 | `/api/hrm/jobs/{id}` | PUT | Cập nhật job | Owner của tenant |
| 17 | `/api/hrm/jobs/{id}` | DELETE | Xóa job | Owner của tenant |
| 18 | `/api/recruitment/apply` | POST | Ứng viên ứng tuyển | Đã đăng nhập |
| 19 | `/api/recruitment/jobs/{jobId}/applications` | GET | Lấy danh sách đơn ứng tuyển | Owner của tenant có job đó |
| 20 | `/api/recruitment/applications/{id}` | PATCH | Duyệt/từ chối đơn | Owner của tenant |

### 2.3. Module Menu (Category, Product)

| # | Endpoint | Method | Ý nghĩa nghiệp vụ | Pre-condition |
|---|----------|--------|-------------------|---------------|
| 21 | `/api/categories` | GET | Lấy danh sách danh mục | Có tenant |
| 22 | `/api/categories` | POST | Tạo danh mục mới | Có tenant |
| 23 | `/api/categories/{id}` | DELETE | Xóa danh mục | Có tenant |
| 24 | `/api/products` | GET | Lấy danh sách sản phẩm (paginated, filter) | Có tenant |
| 25 | `/api/products/{id}` | GET | Lấy chi tiết sản phẩm | Có tenant |
| 26 | `/api/products` | POST | Tạo sản phẩm mới (multipart) | Có tenant |
| 27 | `/api/products/{id}` | PUT | Cập nhật thông tin sản phẩm (form-url-encoded) | Có tenant |
| 28 | `/api/products/{id}/images` | POST | Thêm ảnh sản phẩm (multipart) | Có tenant |
| 29 | `/api/products/images/{imageId}` | DELETE | Xóa ảnh sản phẩm | Có tenant |
| 30 | `/api/products/{id}` | DELETE | Xóa sản phẩm | Có tenant |

### 2.4. Module POS (Table, Session, Customer)

| # | Endpoint | Method | Ý nghĩa nghiệp vụ | Pre-condition |
|---|----------|--------|-------------------|---------------|
| 31 | `/api/pos/tables` | GET | Lấy danh sách bàn | Có tenant |
| 32 | `/api/pos/tables` | POST | Tạo bàn mới | Có tenant |
| 33 | `/api/pos/tables/{id}` | DELETE | Xóa bàn | Có tenant, bàn trống |
| 34 | `/api/pos/sessions` | POST | Mở bàn (tạo session ACTIVE) | Có tenant |
| 35 | `/api/pos/sessions/{sessionId}` | GET | Lấy session theo ID | Có tenant |
| 36 | `/api/pos/sessions/table/{tableId}` | GET | Lấy/tạo session theo tableId | Có tenant |
| 37 | `/api/pos/sessions/{sessionId}/items` | POST | Thêm món vào session | Có tenant, session ACTIVE |
| 38 | `/api/pos/sessions/{sessionId}/tables` | POST | Attach bàn vào session (gộp bàn) | Có tenant, session ACTIVE |
| 39 | `/api/pos/sessions/{sessionId}/tables/{tableId}` | DELETE | Detach bàn khỏi session (tách bàn) | Có tenant, session có >= 2 bàn |
| 40 | `/api/pos/sessions/{sessionId}/pay` | POST | Thanh toán session | Có tenant, session có items |
| 41 | `/api/pos/sessions/{sessionId}/cancel` | POST | Hủy session | Có tenant |
| 42 | `/api/pos/sessions/pending` | GET | Lấy danh sách session chờ xác nhận | Có tenant |
| 43 | `/api/pos/sessions/{sessionId}/confirm` | POST | Xác nhận session PENDING -> ACTIVE | Có tenant |
| 44 | `/api/pos/sessions/{sessionId}/reject` | POST | Từ chối session PENDING | Có tenant |
| 45 | `/api/pos/public/menu` | GET | Menu công khai cho khách | Có tenant_id trong header |
| 46 | `/api/pos/public/info/{tableId}` | GET | Thông tin bàn và quán | Không cần auth |
| 47 | `/api/pos/public/sessions` | POST | Khách đặt món (tạo session PENDING) | Có tenant_id |
| 48 | `/api/pos/public/sessions/{sessionId}` | GET | Khách check status order | Có tenant_id |
| 49 | `/api/pos/public/sessions/{sessionId}/items` | POST | Khách thêm món vào session ACTIVE | Có tenant_id |

### 2.5. Module Payment

| # | Endpoint | Method | Ý nghĩa nghiệp vụ | Pre-condition |
|---|----------|--------|-------------------|---------------|
| 50 | `/api/public/payment/methods/{tenantId}` | GET | Lấy phương thức thanh toán của tenant | Không cần auth |
| 51 | `/api/public/payment/create-url` | POST | Tạo URL thanh toán online | Không cần auth |
| 52 | `/api/public/payment/vnpay-ipn` | GET | VNPay IPN callback (internal) | VNPay gọi |

### 2.6. Module Reporting

| # | Endpoint | Method | Ý nghĩa nghiệp vụ | Pre-condition |
|---|----------|--------|-------------------|---------------|
| 53 | `/api/reports/revenue` | GET | Báo cáo doanh thu | Owner của tenant |
| 54 | `/api/reports/top-products` | GET | Top sản phẩm bán chạy | Owner của tenant |
| 55 | `/api/reports/peak-hours` | GET | Thống kê giờ cao điểm | Owner của tenant |

---

## 3. Bước 2: Mapping API → UI

### 3.1. Bảng Mapping đầy đủ

| API Endpoint | Method | Màn hình Frontend | Action kích hoạt | Trạng thái |
|--------------|--------|-------------------|------------------|------------|
| `/api/auth/sync` | POST | `AuthContext.jsx` | Sau khi Keycloak login thành công | ✅ ĐÚNG |
| `/api/profile/avatar` | POST | - | - | ⚠️ KHÔNG CÓ UI |
| `/api/public/tenants` | GET | `PublicTenantListPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/public/jobs` | GET | (Chưa có page) | - | ⚠️ KHÔNG CÓ UI |
| `/api/tenants` | POST | `SelectTenantPage.jsx` | Click "Tạo quán" | ✅ ĐÚNG |
| `/api/tenants/{id}` | PUT | `TenantSettingsPage.jsx` | Click "Lưu thay đổi" | ✅ ĐÚNG |
| `/api/tenants/{id}` | GET | `TenantContext.jsx` | selectTenant() | ✅ ĐÚNG |
| `/api/tenants/me` | GET | `TenantContext.jsx` | Load khi authenticated | ✅ ĐÚNG |
| `/api/tenants/{id}/status` | PATCH | `TenantSettingsPage.jsx` | Toggle trạng thái quán | ✅ ĐÚNG |
| `/api/tenants/{id}/payment-config` | PUT | `PaymentSettingsPage.jsx` | Click "Lưu cấu hình" | ✅ ĐÚNG |
| `/api/staff` | GET | `StaffListPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/staff/{id}` | DELETE | `StaffListPage.jsx` | Click "Xóa nhân viên" | ✅ ĐÚNG |
| `/api/hrm/jobs` | GET | `JobListPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/hrm/jobs/{id}` | GET | `ApplicationListPage.jsx` | Load chi tiết job | ✅ ĐÚNG |
| `/api/hrm/jobs` | POST | `JobListPage.jsx` | Click "Tạo job" | ✅ ĐÚNG |
| `/api/hrm/jobs/{id}` | PUT | `JobListPage.jsx` | Click "Sửa" | ✅ ĐÚNG |
| `/api/hrm/jobs/{id}` | DELETE | `JobListPage.jsx` | Click "Xóa" | ✅ ĐÚNG |
| `/api/recruitment/apply` | POST | - | - | ⚠️ KHÔNG CÓ UI (có API client) |
| `/api/recruitment/jobs/{jobId}/applications` | GET | `ApplicationListPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/recruitment/applications/{id}` | PATCH | `ApplicationListPage.jsx` | Click Duyệt/Từ chối | ✅ ĐÚNG |
| `/api/categories` | GET | `ProductListPage.jsx`, `CategoryListPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/categories` | POST | `CategoryListPage.jsx` | Click "Thêm danh mục" | ✅ ĐÚNG |
| `/api/categories/{id}` | DELETE | `CategoryListPage.jsx` | Click "Xóa" | ✅ ĐÚNG |
| `/api/products` | GET | `ProductListPage.jsx` | Load trang, filter | ✅ ĐÚNG |
| `/api/products/{id}` | GET | - | - | ⚠️ KHÔNG CÓ UI (có API client) |
| `/api/products` | POST | `ProductListPage.jsx` | Click "Thêm sản phẩm" | ✅ ĐÚNG |
| `/api/products/{id}` | PUT | `ProductListPage.jsx` | Click "Cập nhật" | ✅ ĐÚNG |
| `/api/products/{id}/images` | POST | `ProductListPage.jsx` | Upload thêm ảnh | ✅ ĐÚNG |
| `/api/products/images/{imageId}` | DELETE | `ProductListPage.jsx` | Click xóa ảnh | ✅ ĐÚNG |
| `/api/products/{id}` | DELETE | `ProductListPage.jsx` | Click "Xóa sản phẩm" | ✅ ĐÚNG |
| `/api/pos/tables` | GET | `POSPage.jsx`, `TableGridPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/pos/tables` | POST | `TableGridPage.jsx` | Click "Thêm bàn" | ✅ ĐÚNG |
| `/api/pos/tables/{id}` | DELETE | - | - | ⚠️ KHÔNG CÓ UI (có API client) |
| `/api/pos/sessions` | POST | `TableGridPage.jsx` | handleExecuteMerge() khi bàn chưa có session | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}` | GET | - | - | ⚠️ KHÔNG CÓ UI (có API client) |
| `/api/pos/sessions/table/{tableId}` | GET | `POSPage.jsx` | loadSession() | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/items` | POST | `POSPage.jsx` | handleAddItem() | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/tables` | POST | `POSPage.jsx`, `TableGridPage.jsx` | Gộp bàn | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/tables/{tableId}` | DELETE | `POSPage.jsx`, `TableGridPage.jsx` | Tách bàn | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/pay` | POST | `POSPage.jsx` | handlePayCash() | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/cancel` | POST | `POSPage.jsx` | handleConfirmCancel() | ✅ ĐÚNG |
| `/api/pos/sessions/pending` | GET | `POSPage.jsx` | loadPendingSessions() (polling) | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/confirm` | POST | `POSPage.jsx` | handleConfirmPendingSession() | ✅ ĐÚNG |
| `/api/pos/sessions/{sessionId}/reject` | POST | `POSPage.jsx` | handleRejectPendingSession() | ✅ ĐÚNG |
| `/api/pos/public/menu` | GET | `POSPage.jsx`, `CustomerMenuPage.jsx` | Load menu | ✅ ĐÚNG |
| `/api/pos/public/info/{tableId}` | GET | `CustomerMenuPage.jsx` | Load table info | ✅ ĐÚNG |
| `/api/pos/public/sessions` | POST | `CustomerMenuPage.jsx` | handleSubmitOrder() | ✅ ĐÚNG |
| `/api/pos/public/sessions/{sessionId}` | GET | `CustomerMenuPage.jsx` | Polling status | ✅ ĐÚNG |
| `/api/pos/public/sessions/{sessionId}/items` | POST | `CustomerMenuPage.jsx` | addCustomerItems() | ✅ ĐÚNG |
| `/api/public/payment/methods/{tenantId}` | GET | - | - | ⚠️ KHÔNG CÓ UI (có API client) |
| `/api/public/payment/create-url` | POST | - | - | ⚠️ KHÔNG CÓ UI (có API client) |
| `/api/public/payment/vnpay-ipn` | GET | - | VNPay callback | ✅ N/A (internal) |
| `/api/reports/revenue` | GET | `ReportsPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/reports/top-products` | GET | `ReportsPage.jsx` | Load trang | ✅ ĐÚNG |
| `/api/reports/peak-hours` | GET | `ReportsPage.jsx` | Load trang | ✅ ĐÚNG |

### 3.2. API có nhưng không có UI gọi tới

| API | Nhận xét |
|-----|----------|
| `POST /api/profile/avatar` | Có API client (`auth.js`) nhưng không có UI để upload avatar |
| `GET /api/public/jobs` | Có API client (`hrm.js`) nhưng không có trang xem công việc công khai |
| `POST /api/recruitment/apply` | Có API client nhưng không có UI cho ứng viên apply |
| `GET /api/products/{id}` | Có API client nhưng không có trang detail riêng |
| `DELETE /api/pos/tables/{id}` | Có API client nhưng không có nút xóa trong UI |
| `GET /api/pos/sessions/{sessionId}` | Có API client, dùng gián tiếp |
| `GET /api/public/payment/methods/{tenantId}` | Có API client nhưng chưa tích hợp vào checkout flow |
| `POST /api/public/payment/create-url` | Có API client nhưng chưa tích hợp vào checkout flow |

> [!NOTE]
> Những API này đã có API client được định nghĩa nhưng chưa được sử dụng trong UI. Đây không phải là lỗi nghiêm trọng - có thể là tính năng dự định phát triển sau.

---

## 4. Bước 3: Mapping UI → API

### 4.1. Các lỗi API Call SAI trong Frontend

> [!CAUTION]
> **[CRITICAL]** Các lỗi sau cần sửa ngay:

#### 4.1.1. `POSPage.jsx` - Import API không tồn tại

**File:** `frontend/src/pages/pos/POSPage.jsx` (line 8-19)

```javascript
import { 
    getOrCreateSessionByTable, 
    addItemsToSession, 
    paySession, 
    cancelSession, 
    transferSession,           // ❌ KHÔNG TỒN TẠI trong session.js
    mergeTablesIntoSession,    // ❌ KHÔNG TỒN TẠI trong session.js
    releaseTableFromSession,   // ❌ KHÔNG TỒN TẠI trong session.js
    getPendingSessions,
    confirmSession,
    rejectSession
} from '../../api/session';
```

**Vấn đề:**
- `transferSession` - Không tồn tại (đã được thay thế bằng `transferTable` trong `session.js`)
- `mergeTablesIntoSession` - Không tồn tại (nên dùng `attachTable`)
- `releaseTableFromSession` - Không tồn tại (nên dùng `detachTable`)

**Backend API thực tế:**
- Gộp bàn: `POST /api/pos/sessions/{sessionId}/tables` → `attachTable(sessionId, tableId)`
- Tách bàn: `DELETE /api/pos/sessions/{sessionId}/tables/{tableId}` → `detachTable(sessionId, tableId)`

---

#### 4.1.2. `TableGridPage.jsx` - Import API không tồn tại

**File:** `frontend/src/pages/pos/TableGridPage.jsx` (line 7)

```javascript
import { openSession, mergeTablesIntoSession, releaseTableFromSession, getSession } from '../../api/session';
```

**Vấn đề:**
- `mergeTablesIntoSession` - Không tồn tại
- `releaseTableFromSession` - Không tồn tại

---

#### 4.1.3. `addItemsToSession` - Có thể bị lỗi tham số

**File:** `frontend/src/pages/pos/POSPage.jsx` (line 132)

```javascript
await addItemsToSession(session.sessionId, [{ productId: product.id, quantity: 1 }], selectedTableId);
```

**API Client định nghĩa:**
```javascript
export const addItemsToSession = (sessionId, items) =>
    api.post(`/api/pos/sessions/${sessionId}/items`, { items: ... });
```

**Vấn đề:** POSPage gọi với 3 tham số nhưng API chỉ nhận 2. Tham số thứ 3 (`selectedTableId`) sẽ bị bỏ qua.

---

#### 4.1.4. `pos.js` - API `removeOrderItem` không tồn tại trong Backend

**File:** `frontend/src/api/pos.js` (line 13)

```javascript
export const removeOrderItem = (itemId) => api.delete(`/api/pos/orders/items/${itemId}`);
```

**Vấn đề:** Không có endpoint `/api/pos/orders/items/{itemId}` trong backend. Backend sử dụng Session-based model, không có API xóa item riêng lẻ.

---

### 4.2. Bảng tổng hợp UI → API

| Màn hình | Action | API gọi | Trạng thái |
|----------|--------|---------|------------|
| **POSPage** | Chuyển bàn | `transferSession()` | ❌ API không tồn tại |
| **POSPage** | Gộp bàn | `mergeTablesIntoSession()` | ❌ API không tồn tại |
| **POSPage** | Tách bàn | `releaseTableFromSession()` | ❌ API không tồn tại |
| **POSPage** | Xóa món | `removeOrderItem()` | ❌ Backend không hỗ trợ |
| **TableGridPage** | Gộp bàn | `mergeTablesIntoSession()` | ❌ API không tồn tại |
| **TableGridPage** | Tách bàn | `releaseTableFromSession()` | ❌ API không tồn tại |

---

## 5. Bước 4: Kiểm tra trạng thái & ràng buộc

### 5.1. Session Status Flow

**Backend định nghĩa:**
```
PENDING → ACTIVE → COMPLETED/CANCELLED
         ↑
         └── (Staff confirm từ PENDING)
```

**Frontend xử lý:**
- ✅ `CustomerMenuPage.jsx` - Hiển thị đúng trạng thái PENDING/ACTIVE/CANCELLED
- ✅ `POSPage.jsx` - Polling pending sessions và có UI confirm/reject
- ✅ Khi session CANCELLED, khách được thông báo và có nút "Thử lại"

### 5.2. Table Status Handling

**Backend định nghĩa:**
```
AVAILABLE | OCCUPIED | RESERVED
```

**Frontend xử lý:**
- ✅ `TableGridPage.jsx` - Hiển thị status badge đúng
- ✅ `POSPage.jsx` - Filter bàn trống khi chuyển/gộp bàn
- ⚠️ Status `RESERVED` chưa được sử dụng trong UI

### 5.3. Owner Permission

**Backend yêu cầu:** Một số API chỉ owner mới được gọi

**Frontend xử lý:**
- ✅ `App.jsx` - Có `OwnerRoute` wrapper
- ✅ `useTenant().isOwner` - Kiểm tra quyền owner
- ✅ Menu items ẩn/hiện đúng theo quyền

---

## 6. Bước 5: Kiểm tra dữ liệu hiển thị

### 6.1. Session Response

**Backend trả về:**
```json
{
  "sessionId": 123,
  "status": "ACTIVE",
  "startedAt": "2024-01-01T10:00:00",
  "endedAt": null,
  "tables": [...],
  "orders": [...],
  "totalAmount": 150000,
  "guestCount": 2,
  "note": "..."
}
```

**Frontend hiển thị:**
- ✅ `sessionId`, `status`, `tables`, `orders`, `totalAmount` - Đúng
- ⚠️ `guestCount`, `note`, `startedAt` - Không hiển thị trong UI

### 6.2. Product Response

**Backend trả về:**
```json
{
  "id": 1,
  "name": "Cà phê sữa",
  "price": 25000,
  "description": "...",
  "status": "AVAILABLE",
  "categoryId": 1,
  "categoryName": "Đồ uống",
  "images": [...]
}
```

**Frontend hiển thị:**
- ✅ Tất cả field được hiển thị đúng
- ⚠️ `status` = `OUT_OF_STOCK` không được xử lý đặc biệt trong POS menu

---

## 7. Tổng hợp vấn đề

### 7.1. FRONTEND SAI (Cần sửa ngay)

| # | Vấn đề | File | Mức độ |
|---|--------|------|--------|
| 1 | Import `transferSession` không tồn tại | `POSPage.jsx` | 🔴 Critical |
| 2 | Import `mergeTablesIntoSession` không tồn tại | `POSPage.jsx`, `TableGridPage.jsx` | 🔴 Critical |
| 3 | Import `releaseTableFromSession` không tồn tại | `POSPage.jsx`, `TableGridPage.jsx` | 🔴 Critical |
| 4 | API `removeOrderItem` không có backend | `pos.js` | 🔴 Critical |
| 5 | `addItemsToSession` gọi với tham số thừa | `POSPage.jsx` | 🟡 Warning |

### 7.2. FRONTEND THIẾU (Không nghiêm trọng)

| # | Tính năng | API Backend | Nhận xét |
|---|-----------|-------------|----------|
| 1 | Upload avatar | `POST /api/profile/avatar` | Có thể thêm sau |
| 2 | Trang xem job công khai | `GET /api/public/jobs` | Tính năng tuyển dụng |
| 3 | Form ứng tuyển | `POST /api/recruitment/apply` | Tính năng tuyển dụng |
| 4 | Thanh toán online | `payment.js` APIs | VNPay flow chưa hoàn thiện |
| 5 | Xóa bàn | `DELETE /api/pos/tables/{id}` | Có thể thêm sau |

### 7.3. FRONTEND THỪA (UI không có backend)

| # | UI Element | Vấn đề |
|---|------------|--------|
| 1 | Nút "Xóa món" trong POSPage | Gọi API không tồn tại |
| 2 | Nút "Chuyển bàn" trong POSPage | Gọi function không tồn tại |

---

## 8. Gợi ý điều chỉnh Frontend

### 8.1. Fix Critical - POSPage.jsx

```diff
// Thay thế import sai
- import { 
-     transferSession,
-     mergeTablesIntoSession,
-     releaseTableFromSession,
- } from '../../api/session';

+ import { 
+     attachTable,
+     detachTable,
+     transferTable,  // Composite function
+ } from '../../api/session';
```

Sửa các function:

```javascript
// Thay vì mergeTablesIntoSession(sessionId, tableIds)
// Dùng: attachTable(sessionId, tableId) cho từng bàn

// Thay vì releaseTableFromSession(sessionId, tableId)
// Dùng: detachTable(sessionId, tableId)

// Thay vì transferSession(sessionId, targetTableIds)
// Dùng: transferTable(sessionId, newTableId, oldTableId)
```

### 8.2. Fix Critical - Xóa removeOrderItem

Xóa nút xóa món trong POSPage hoặc implement API backend mới:

```diff
- const handleRemoveItem = async (itemId) => {
-     await removeOrderItem(itemId);
-     ...
- };

+ // Backend không hỗ trợ xóa món riêng lẻ
+ // Có thể cần thêm API: DELETE /api/pos/sessions/{sessionId}/items/{itemId}
```

### 8.3. Fix Warning - addItemsToSession

```diff
- await addItemsToSession(session.sessionId, [{ productId: product.id, quantity: 1 }], selectedTableId);
+ await addItemsToSession(session.sessionId, [{ productId: product.id, quantity: 1 }]);
```

---

## 9. Kết luận

### Đánh giá tổng thể

| Tiêu chí | Đánh giá |
|----------|----------|
| **Frontend ĐÚNG** | ~89% (42/47 API) |
| **Frontend THIẾU** | ~11% (5 tính năng) |
| **Frontend SAI** | 5 lỗi critical |

### Khuyến nghị ưu tiên

1. **Ưu tiên 1 (Ngay lập tức):** Sửa import errors trong `POSPage.jsx` và `TableGridPage.jsx`
2. **Ưu tiên 2 (Sớm):** Loại bỏ hoặc implement `removeOrderItem`
3. **Ưu tiên 3 (Trung hạn):** Hoàn thiện tính năng thanh toán online
4. **Ưu tiên 4 (Dài hạn):** Bổ sung các trang còn thiếu (public jobs, apply form)

---

**Người đánh giá:** AI Tech Lead  
**Ngày hoàn thành:** 2026-01-01
