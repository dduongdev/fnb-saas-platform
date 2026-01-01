# PROGRESS.MD - F&B SAAS PLATFORM IMPLEMENTATION

**Tech Stack:** Java Spring Boot 3, ReactJS (Vite), MySQL 8, Keycloak 22+, MinIO, RabbitMQ.
**Architecture:** Modular Monolith (Multi-tenant) with Docker.

---

## 🚀 PHASE 1: INFRASTRUCTURE & CONFIGURATION (Hạ tầng)

### 1.1. Docker Environment Setup
- [ ] **Docker Compose:** Thiết lập file `docker-compose.yml` chứa:
    - [x] `mysql-container`: Port 3306, Volume persistent data.
    - [x] `keycloak-container`: Port 8080, Import file cấu hình Realm.
    - [x] `minio-container`: Port 9000 (API) & 9001 (Console).
    - [x] `rabbitmq-container`: Port 5672 (AMQP) & 15672 (Management).
- [ ] **Keycloak Configuration (SYS-AUTH)**
    - [x] Tạo Realm `fnb-platform`.
    - [x] Cấu hình Client `fnb-backend` (Access Type: Confidential - Service Account).
    - [x] Cấu hình Client `fnb-web` (Access Type: Public - PKCE).
    - [ ] Cấu hình Identity Providers (Google, Facebook) cho Social Login.
- [ ] **MinIO Configuration**
    - [x] Tạo Bucket `user-profiles` (Public Read).
    - [x] Tạo AccessKey/SecretKey cho Backend dùng.
- [ ] **RabbitMQ Configuration**
    - [x] Tạo Exchange `fnb.core.exchange` (Topic Type).

---

## 🛠 PHASE 2: BACKEND FOUNDATION (Spring Boot Core)

### 2.1. Project Skeleton & Shared Kernel
- [ ] Init Spring Boot Project (Modules: Web, Security, JPA, AMQP, WebSocket).
- [ ] **Multi-tenancy Core:**
    - [x] Implement `TenantContextHolder` (ThreadLocal).
    - [x] Implement `TenantFilter`: Chặn Request -> Parse JWT -> Lấy `tenant_id` -> Set vào Context.
    - [x] Implement `HibernateFilter` hoặc `EntityListener`: Tự động gán `WHERE tenant_id = ?` cho các Entity có Tenant.
- [ ] **Security (SYS-AUTH):**
    - [x] Config `SecurityFilterChain`: Tích hợp OAuth2 Resource Server.
    - [x] Custom `JwtAuthenticationConverter` để map Roles từ Keycloak vào Spring Security Authorities.
- [ ] **Integrations:**
    - [x] `StorageService`: Wrapper class để upload/delete file S3.
    - [x] `RabbitMQSender`: Generic class để bắn event.

---

## 📦 PHASE 3: IMPLEMENTATION - GLOBAL & PLATFORM (Người dùng Nền tảng)

### 3.1. Authentication & Profile (PF-01)
- [ ] **Auth API:**
    - [x] Endpoint lấy thông tin User từ Token (`/api/me`).
- [ ] **Profile Management:**
    - [x] Entity `User` (Global scope).
    - [x] API Upload Avatar/CV: Upload sang MinIO bucket `user-profiles`, lưu URL về DB.
    - [x] Entity `UserAvailability`: Lưu lịch rảnh (Thứ, Giờ bắt đầu, Giờ kết thúc).
    - [x] API CRUD `UserAvailability`.

### 3.2. Tenant Discovery & Recruitment (PF-02, PF-03)
- [ ] **Public Tenant API:**
    - [x] API `GET /api/public/tenants`: List quán (Bỏ qua filter tenant mặc định).
    - [x] Logic: Trả về Public URL ảnh cover quán từ MinIO.
- [ ] **Recruitment:**
    - [x] Entity `Application` (user_id, tenant_id, status).
    - [x] API `POST /api/apply`: User apply vào quán.

---

## 🏢 PHASE 4: IMPLEMENTATION - TENANT ADMIN (Chủ quán)

### 4.1. Onboarding & Tenant Management (OWN-01, OWN-04)
- [ ] **Tenant Creation (Onboarding):**
    - [x] API `POST /api/tenants` (Create Tenant).
    - [X] **Keycloak Integration:** Code gọi Keycloak Admin API để tạo Group `tenant_{id}_staff`.
    - [x] **MinIO Integration:** Code gọi MinIO Client tạo bucket `tenant-{id}-assets`.
- [ ] **Payment Configuration:**
    - [X] Column `payment_config` trong bảng Tenant.
    - [X] Logic: Mã hóa (AES) SecretKey/PartnerCode trước khi lưu DB.

### 4.2. Menu Management (OWN-02)
- [x] **Menu Entities:** `Category`, `Product`.
- [x] **Upload Logic:**
    - [x] API Upload ảnh món ăn: Detect `tenant_id` hiện tại -> Upload vào bucket `tenant-{id}-assets`.
- [x] **CRUD Menu:** Thêm/Sửa/Ẩn món ăn.

### 4.3. HR Management (OWN-03)
- [x] **Staff Management:**
    - [x] API `POST /api/staff`: Nhập email User -> Add User vào Keycloak Group của Tenant -> Insert bảng `Employee`.
- [x] **Shift Scheduling:**
    - [X] Entity `Shift`.

### 4.4. Reporting (OWN-05)
- [x] **Analytics Engine:**
    - [x] Scheduled Task (Cronjob) hoặc Event Listener tổng hợp data.
    - [x] API Report Doanh thu (Ngày/Tháng).
    - [x] **Performance Logic:** Update `trust_score` cho User.

---

## 👨‍🍳 PHASE 5: IMPLEMENTATION - STAFF OPERATIONS (Nhân viên)

### 5.2. Table Operations (ST-02) - *Logic Phức tạp*
- [x] **Table Entity:** `id`, `status`, `master_table_id` (Star Topology).
- [x] **Real-time Status:**
    - [x] WebSocket Controller endpoint `/topic/tables`.
    - [x] Gửi update khi trạng thái bàn thay đổi.
- [x] **Merge Tables (Gộp):**
    - [x] API `POST /api/tables/merge`.
    - [x] Logic: Set `master_table_id` của bàn Slave. Move `OrderItems` sang bàn Master. Lưu vết History.
- [x] **Split Tables (Tách):**
    - [x] API `POST /api/tables/split`.
    - [x] Validate: Bàn Master phải `AVAILABLE` (đã thanh toán/trống).
    - [x] Logic: Set `master_table_id = NULL` cho các bàn Slave.

### 5.3. Order Processing (ST-03)
- [x] **Order Entities:** `Order`, `OrderItem`.
- [x] **Delete Item Logic:**
    - [x] API `DELETE /api/order-items/{id}`.
    - [x] Check: Nếu `status != PENDING` -> Throw Exception 403.

### 5.4. Payment (ST-04)
- [x] **Cash Payment:**
    - [x] API `POST /api/orders/{id}/pay/cash`.
    - [x] Logic: Set Order Status `PAID` -> Giải phóng bàn (`AVAILABLE`) -> Trigger in hóa đơn.
- [x] **Invoice:** Template HTML/PDF hóa đơn để in.

---

## 📱 PHASE 6: IMPLEMENTATION - CUSTOMER (Khách hàng)

### 6.1. QR & Ordering (CUST-01)
- [x] **QR Logic:**
    - [x] Middleware kiểm tra URL query param `?tenantId=...&tableId=...`.
- [x] **Menu View:** API lấy Menu công khai (không cần Token User, có thể dùng Token Guest).

### 6.2. Online Payment (CUST-02)
- [x] **Gateway Integration:**
    - [x] API tạo Payment URL (Momo/VNPay) sử dụng config đã giải mã của Tenant.
- [ ] **Webhook Handler:**
    - [ ] API `POST /api/webhook/payment`.
    - [ ] Validate Signature -> Update Order `PAID`.
    - [ ] **Socket Notification:** Notify Staff là "Bàn X đã thanh toán online" -> Staff dọn bàn.

---

## 💻 PHASE 7: FRONTEND DEVELOPMENT (ReactJS)

### 7.1. Common & Auth
- [ ] Setup `keycloak-js`, Redux Toolkit, Axios Interceptor.
- [ ] Login/Register Page (Redirect Keycloak).

### 7.2. User Portal (Candidate)
- [ ] Page: Profile (Avatar Upload UI, Availability Scheduler UI).
- [ ] Page: Job Market (List Tenants, Apply Button).

### 7.3. Owner Dashboard
- [ ] Page: Menu Manager (CRUD, Drag & Drop Image Upload).
- [ ] Page: Staff Manager (Add User form, Shift Calendar UI).
- [ ] Page: Reports (Charts using Recharts/ChartJS).

### 7.4. Staff POS (Operational)
- [ ] **Table Map UI:**
    - [ ] Grid layout các bàn.
    - [ ] Hiển thị bàn gộp (Gom nhóm UI).
    - [ ] Context Menu: Chức năng Gộp/Tách bàn.
- [ ] **Ordering UI:**
    - [ ] List món, Search, Note món.
    - [ ] Button "Gửi Bếp" (Trigger status PENDING -> SENT).
- [ ] **Kitchen View:** List các món đang chờ chế biến.

---

## 🧪 PHASE 8: TESTING & DEPLOYMENT

- [ ] **Unit Tests:**
    - [ ] Test logic Gộp bàn (Ensure data integrity).
    - [ ] Test logic Validate Check-in chéo quán.
- [ ] **Deployment:**
    - [ ] Build Docker Images.
    - [ ] Setup Nginx Reverse Proxy (SSL).
    - [ ] UAT (User Acceptance Testing) toàn bộ flow.

---

### 📝 Trạng thái
*   [ ] : Chưa làm
*   [~] : Đang làm
*   [x] : Hoàn thành