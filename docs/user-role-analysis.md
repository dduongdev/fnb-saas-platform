# PHÂN TÍCH VAI TRÒ NGƯỜI DÙNG & BACKEND-FRONTEND MAPPING

## I. PHÂN LOẠI VAI TRÒ NGƯỜI DÙNG

### 1. PLATFORM (Super Admin)
**Vai trò:** Người vận hành toàn hệ thống multi-tenant
**Quyền hạn:**
- Xem danh sách tất cả tenant
- Kích hoạt/vô hiệu hóa tenant
- Xem thống kê tổng hệ thống
- KHÔNG liên quan đến nghiệp vụ gọi món

### 2. TENANT OWNER/ADMIN (Chủ Quán)
**Vai trò:** Người sở hữu và quản lý quán
**Quyền hạn:**
- Quản lý thông tin quán (tên, địa chỉ, logo)
- Quản lý menu (category, product)
- Quản lý nhân sự (HRM)
- Quản lý bàn, session
- POS: Xác nhận order, serve món, thanh toán
- Xem báo cáo doanh thu
- Cấu hình thanh toán

**TRẠNG THÁI:** ✅ ĐÃ HOÀN THIỆN - KHÔNG ĐƯỢC SỬA ĐỔI

### 3. KHÁCH HÀNG - KHÔNG TÀI KHOẢN (Guest)
**Vai trò:** Khách vào quán, quét QR bàn
**Quyền hạn:**
- Xem menu công khai
- Chọn món + số lượng
- Thêm món vào session
- Xóa món (nếu còn PENDING)
- Xem trạng thái món (PENDING/SERVED)
- KHÔNG CẦN đăng nhập

**Luồng:**
1. Quét QR bàn → `/table/:tableId`
2. Xem menu
3. Chọn món, nhập số lượng
4. Tạo order (tạo session PENDING)
5. Chờ nhân viên xác nhận
6. Thêm món nếu session ACTIVE
7. Xem realtime update

### 4. KHÁCH HÀNG - CÓ TÀI KHOẢN (Registered Guest)
**Vai trò:** Khách có account để tích điểm/ưu đãi
**Quyền hạn:**
- TẤT CẢ quyền của "Khách không tài khoản"
- THÊM:
  - Xem lịch sử gọi món
  - Nhận điểm/ưu đãi
  - Auto-recognition khi vào session
  - Lưu món yêu thích (tương lai)

**Khác biệt kỹ thuật:**
- Có `userId` trong OrderItem
- Có profile page
- Có order history

**LƯU Ý QUAN TRỌNG:**
- KHÁCH CÓ TÀI KHOẢN ≠ NHÂN VIÊN
- KHÔNG có quyền serve món
- KHÔNG có quyền thanh toán
- KHÔNG có quyền xác nhận order

---

## II. BACKEND API MAPPING

### A. PLATFORM (Super Admin) APIs

| Endpoint | Method | Controller | Chức năng | Backend Status | Frontend UI |
|----------|--------|------------|-----------|----------------|-------------|
| `/api/tenants` | GET | TenantController | Lấy tất cả tenant (cần thêm role check) | ⚠️ Cần filter PLATFORM | ❌ Chưa có |
| `/api/tenants/{id}/status` | PATCH | TenantController | Kích hoạt/vô hiệu hóa tenant | ✅ Có | ❌ Chưa có |
| `/api/platform/stats` | GET | - | Thống kê tổng hệ thống | ❌ Chưa có | ❌ Chưa có |

**Đề xuất bổ sung Backend:**
```java
// PlatformController.java
@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    
    // Chỉ cho PLATFORM role
    @GetMapping("/tenants")
    public ApiResponse<List<TenantAdminDto>> getAllTenants() {
        // Return all tenants with stats
    }
    
    @GetMapping("/stats")
    public ApiResponse<PlatformStatsDto> getPlatformStats() {
        // Total tenants, active/inactive, total revenue, etc.
    }
}
```

---

### B. TENANT OWNER/ADMIN APIs

**STATUS:** ✅ ĐÃ HOÀN THIỆN - KHÔNG SỬA ĐỔI

| Module | Endpoints | UI Pages | Status |
|--------|-----------|----------|--------|
| **Tenant Management** | `/api/tenants/*` | TenantSettingsPage | ✅ |
| **Menu Management** | `/api/products/*`, `/api/categories/*` | ProductListPage, CategoryListPage | ✅ |
| **HRM** | `/api/staff/*`, `/api/jobs/*` | StaffListPage, JobListPage | ✅ |
| **POS** | `/api/pos/sessions/*`, `/api/pos/tables/*` | POSPage, SessionListPage, TableGridPage | ✅ |
| **Reports** | `/api/reports/*` | ReportsPage | ✅ |
| **Payment** | `/api/payment/*` | PaymentSettingsPage | ✅ |

---

### C. KHÁCH (Có & Không Tài Khoản) APIs

#### 1. APIs Công Khai (Không cần auth)

| Endpoint | Method | Controller | Chức năng | Backend | Frontend |
|----------|--------|------------|-----------|---------|----------|
| `/api/public/tenants` | GET | PublicTenantController | Danh sách quán | ✅ | ✅ PublicTenantListPage |
| `/api/pos/public/menu` | GET | CustomerController | Lấy menu công khai | ✅ | ✅ CustomerMenuPage |
| `/api/pos/public/info/{tableId}` | GET | CustomerController | Thông tin bàn + quán | ✅ | ✅ CustomerMenuPage |
| `/api/pos/public/sessions` | POST | CustomerController | Tạo order (PENDING session) | ✅ | ✅ CustomerMenuPage |
| `/api/pos/public/sessions/{id}` | GET | CustomerController | Xem trạng thái order | ✅ | ✅ CustomerMenuPage |
| `/api/pos/public/sessions/{id}/items` | POST | CustomerController | Thêm món (khi ACTIVE) | ✅ | ✅ CustomerMenuPage |

#### 2. APIs cho Khách CÓ TÀI KHOẢN (Cần auth)

| Endpoint | Method | Controller | Chức năng | Backend | Frontend |
|----------|--------|------------|-----------|---------|----------|
| `/api/customer/profile` | GET | - | Xem profile | ❌ Chưa có | ⚠️ UserProfilePage (đang dùng chung) |
| `/api/customer/orders/history` | GET | - | Lịch sử gọi món | ❌ Chưa có | ❌ Chưa có |
| `/api/customer/points` | GET | - | Xem điểm tích lũy | ❌ Chưa có (tương lai) | ❌ Chưa có |

**Đề xuất bổ sung Backend:**
```java
// CustomerAccountController.java
@RestController
@RequestMapping("/api/customer")
public class CustomerAccountController {
    
    @GetMapping("/orders/history")
    public ApiResponse<Page<CustomerOrderHistoryDto>> getMyOrderHistory(
        @AuthenticationPrincipal Jwt jwt,
        Pageable pageable
    ) {
        String userId = jwt.getSubject();
        // Return orders where orderItem.userId = userId
        // Group by session, show: date, tenant, items, total
    }
    
    @GetMapping("/profile")
    public ApiResponse<CustomerProfileDto> getMyProfile(
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        // Return: name, email, total orders, points, etc.
    }
}
```

---

## III. FRONTEND PAGES MAPPING

### A. PLATFORM Pages - ❌ CHƯA CÓ

**Cần tạo:**
1. **PlatformDashboardPage** (`/platform/dashboard`)
   - Tổng số tenant (active/inactive)
   - Biểu đồ tăng trưởng tenant
   - Top tenant theo doanh thu
   - Thống kê tổng doanh thu hệ thống

2. **PlatformTenantListPage** (`/platform/tenants`)
   - Bảng danh sách tenant
   - Filter: active/inactive, search
   - Actions: Activate/Deactivate
   - View details (thông tin, thống kê)

---

### B. TENANT OWNER Pages - ✅ ĐÃ HOÀN THIỆN

| Page | Path | Status |
|------|------|--------|
| POSPage | `/pos` | ✅ |
| TableGridPage | `/tables` | ✅ |
| SessionListPage | `/sessions` | ✅ |
| OrderSessionPage | `/session/:id` | ✅ |
| ProductListPage | `/products` | ✅ |
| CategoryListPage | `/categories` | ✅ |
| StaffListPage | `/staff` | ✅ |
| JobListPage | `/jobs` | ✅ |
| ReportsPage | `/reports` | ✅ |
| TenantSettingsPage | `/settings` | ✅ |
| PaymentSettingsPage | `/settings/payment` | ✅ |

---

### C. KHÁCH Pages

#### 1. Khách KHÔNG TÀI KHOẢN - ✅ CÓ (cần review)

| Page | Path | Chức năng | Status |
|------|------|-----------|--------|
| **CustomerMenuPage** | `/table/:tableId` | QR entry → Menu → Order | ✅ Đã có |

**Review CustomerMenuPage:**
- ✅ QR scan entry
- ✅ Load menu
- ✅ Select items + quantity
- ✅ Create pending order
- ✅ Add items to active session
- ⚠️ **CẦN KIỂM TRA:** Xóa món PENDING
- ⚠️ **CẦN KIỂM TRA:** Realtime updates
- ⚠️ **CẦN KIỂM TRA:** UI đúng vai trò "KHÁCH" (không có nút serve/pay)

#### 2. Khách CÓ TÀI KHOẢN - ⚠️ MỘT PHẦN

| Page | Path | Chức năng | Status |
|------|------|-----------|--------|
| CustomerMenuPage | `/table/:tableId` | Giống không TK + userId | ✅ Đã có |
| **CustomerOrderHistoryPage** | `/customer/orders` | Lịch sử gọi món | ❌ Chưa có |
| **CustomerProfilePage** | `/customer/profile` | Profile + điểm | ⚠️ UserProfilePage (cần tách) |

**Hiện trạng UserProfilePage:**
- Đang dùng chung cho cả tenant owner và customer
- Cần tách thành:
  - `TenantOwnerProfilePage` - cho owner
  - `CustomerProfilePage` - cho khách có TK

---

## IV. GAPS & CONFLICTS ANALYSIS

### A. BACKEND GAPS (Endpoint thiếu)

#### 1. PLATFORM (Ưu tiên CAO)
- ❌ `GET /api/platform/tenants` - Danh sách tenant với stats
- ❌ `GET /api/platform/stats` - Thống kê tổng hệ thống
- ⚠️ `PATCH /api/tenants/{id}/status` - Cần thêm PLATFORM role check

#### 2. CUSTOMER (Ưu tiên TRUNG BÌNH)
- ❌ `GET /api/customer/orders/history` - Lịch sử gọi món
- ❌ `GET /api/customer/profile` - Profile khách hàng
- ❌ `DELETE /api/pos/public/sessions/{sessionId}/items/{itemId}` - Xóa món PENDING

**Lý do cần endpoint xóa món:**
- Backend hiện có: `DELETE /api/pos/sessions/{sessionId}/items/{itemId}`
- Nhưng endpoint này cần auth và tenant context
- Khách công khai cần endpoint tương tự nhưng không cần auth
- Hoặc: Cho phép endpoint hiện tại chấp nhận public request

---

### B. FRONTEND GAPS (UI thiếu)

#### 1. PLATFORM UI (❌ HOÀN TOÀN THIẾU)
- ❌ PlatformDashboardPage
- ❌ PlatformTenantListPage
- ❌ Route `/platform/*` chưa có

#### 2. CUSTOMER UI
- ❌ CustomerOrderHistoryPage (cho khách có TK)
- ⚠️ CustomerProfilePage (đang lẫn với owner)
- ⚠️ CustomerMenuPage - cần review chi tiết

---

### C. ROLE CONFLICTS (UI lẫn vai trò)

#### 1. UserProfilePage - ⚠️ ĐANG LẪN VAI TRÒ
**Vấn đề:**
- Hiện tại dùng chung cho cả Owner và Customer
- Cần tách thành 2 page riêng

**Giải pháp:**
- `TenantOwnerProfilePage`: Quản lý profile + tenant
- `CustomerProfilePage`: Profile + điểm + lịch sử

#### 2. CustomerMenuPage - ⚠️ CẦN REVIEW
**Cần kiểm tra:**
- Có nút "Serve món" không? → PHẢI XÓA
- Có nút "Thanh toán" không? → PHẢI XÓA  
- Có cho xóa món PENDING không? → PHẢI CÓ
- Có realtime update không? → PHẢI CÓ
- UI có rõ vai trò "KHÁCH" không? → PHẢI RÕ

---

## V. REALTIME & SESSION DESIGN

### Luồng Session cho KHÁCH

#### Trường hợp 1: Bàn trống (AVAILABLE)
```
1. Khách quét QR → GET /api/pos/public/info/{tableId}
2. Frontend: tableStatus = AVAILABLE, hasActiveSession = false
3. Khách chọn món → POST /api/pos/public/sessions
   Body: { tableId, items: [{ productId, quantity }] }
4. Backend: Tạo session PENDING
5. Frontend: Hiển thị "Đơn hàng đang chờ xác nhận"
6. Polling: GET /api/pos/public/sessions/{sessionId} mỗi 3s
7. Khi nhân viên confirm → status = ACTIVE
8. Frontend: Chuyển sang view "Đã xác nhận, có thể gọi thêm"
```

#### Trường hợp 2: Bàn có session ACTIVE
```
1. Khách quét QR → GET /api/pos/public/info/{tableId}
2. Frontend: hasActiveSession = true, sessionId = X
3. Khách chọn món → POST /api/pos/public/sessions/{sessionId}/items
   Body: { items: [{ productId, quantity }] }
4. Backend: Thêm món vào session hiện tại
5. Realtime: WebSocket broadcast ORDER_ITEM_ADDED
6. Frontend: Update UI ngay lập tức
```

#### Trường hợp 3: Bàn có session PENDING
```
1. Khách quét QR → GET /api/pos/public/info/{tableId}
2. Frontend: Hiển thị "Bàn đang có đơn hàng chờ xác nhận"
3. Chỉ hiển thị, không cho đặt thêm
```

### Realtime Events

| Event | Khi nào | Payload | UI Update |
|-------|---------|---------|-----------|
| `ORDER_ITEM_ADDED` | Thêm món | `{ sessionId, item }` | Thêm item vào danh sách |
| `ORDER_ITEM_DELETED` | Xóa món | `{ sessionId, itemId }` | Xóa item khỏi UI |
| `ORDER_ITEM_SERVED` | Serve món | `{ sessionId, itemId }` | Đổi status → SERVED |
| `SESSION_CONFIRMED` | Xác nhận | `{ sessionId }` | Status: PENDING → ACTIVE |
| `SESSION_PAID` | Thanh toán | `{ sessionId }` | Hiển thị "Đã thanh toán" |

---

## VI. NGUYÊN TẮC PHÂN QUYỀN

### KHÁCH KHÔNG BAO GIỜ được:
- ❌ Serve món (đổi status PENDING → SERVED)
- ❌ Thanh toán (pay session)
- ❌ Xác nhận order (confirm session)
- ❌ Reject order
- ❌ Xóa món đã SERVED
- ❌ Quản lý bàn (attach/detach table)
- ❌ Xem report doanh thu
- ❌ Quản lý menu/nhân sự

### KHÁCH CHỈ được:
- ✅ Xem menu công khai
- ✅ Chọn món + nhập số lượng
- ✅ Tạo order mới (tạo session PENDING)
- ✅ Thêm món vào session ACTIVE
- ✅ Xóa món khi còn PENDING
- ✅ Xem trạng thái món (PENDING/SERVED)
- ✅ Xem tổng tiền ước tính

### KHÁCH CÓ TÀI KHOẢN THÊM:
- ✅ Xem lịch sử gọi món
- ✅ Xem điểm tích lũy
- ✅ (Tương lai) Lưu món yêu thích
- ✅ (Tương lai) Nhận ưu đãi

---

## VII. TECH STACK & SECURITY

### Backend Security
```java
// SecurityConfig.java
.requestMatchers("/api/pos/public/**").permitAll()
.requestMatchers("/api/public/**").permitAll()
.requestMatchers("/api/customer/**").authenticated() // Customer có TK
.requestMatchers("/api/platform/**").hasRole("PLATFORM")
.requestMatchers("/api/tenants/**").authenticated()
.anyRequest().authenticated()
```

### Frontend Routes
```jsx
// Public - Không cần auth
/shops → PublicTenantListPage
/table/:tableId → CustomerMenuPage

// Authenticated Customer
/customer/profile → CustomerProfilePage
/customer/orders → CustomerOrderHistoryPage

// Tenant Owner/Staff
/pos, /tables, /sessions, /products, etc.

// Platform Admin
/platform/dashboard → PlatformDashboardPage
/platform/tenants → PlatformTenantListPage
```

---

## VIII. TRIỂN KHAI ƯU TIÊN

### Phase 1: CUSTOMER UI (Ưu tiên CAO)
1. ✅ Review & fix CustomerMenuPage
   - Đảm bảo KHÔNG có nút serve/pay
   - Có nút xóa món PENDING
   - Realtime updates hoạt động
2. ❌ Thêm endpoint: `DELETE /api/pos/public/sessions/{sessionId}/items/{itemId}`
3. ❌ Tạo CustomerOrderHistoryPage
4. ❌ Tách CustomerProfilePage

### Phase 2: PLATFORM UI (Ưu tiên TRUNG BÌNH)
1. ❌ Tạo PlatformController + endpoints
2. ❌ Tạo PlatformDashboardPage
3. ❌ Tạo PlatformTenantListPage

### Phase 3: ENHANCEMENT (Ưu tiên THẤP)
1. Customer points/loyalty system
2. Favorite items
3. Advanced analytics cho platform

---

## IX. KẾT LUẬN

### Hiện trạng:
- ✅ **TENANT OWNER:** Hoàn thiện, ổn định, KHÔNG SỬA
- ⚠️ **CUSTOMER:** Có cơ bản, cần bổ sung & review
- ❌ **PLATFORM:** Hoàn toàn thiếu

### Cần làm ngay:
1. Review CustomerMenuPage - đảm bảo đúng vai trò
2. Thêm endpoint xóa món cho khách
3. Tạo Platform UI (dashboard + tenant list)
4. Tách CustomerProfilePage
5. Tạo CustomerOrderHistoryPage

### Không được làm:
- ❌ Sửa logic Tenant Owner
- ❌ Refactor API đã hoạt động
- ❌ Thay đổi UI tenant management
