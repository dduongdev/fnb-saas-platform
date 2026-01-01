# TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS)
**Dự án:** F&B SaaS Platform (Quản lý Nhà hàng & Tuyển dụng)
**Kiến trúc:** Microservices / Modular Monolith (Multi-tenant)
**Phiên bản:** 2.0

---

## 1. GIỚI THIỆU & PHẠM VI
### 1.1. Mục đích
Xây dựng nền tảng SaaS phục vụ hai mục đích chính:
1.  **Vận hành:** Cung cấp giải pháp phần mềm quản lý (POS, HRM, Inventory) cho các nhà hàng (Tenant) trên cùng một hạ tầng nhưng dữ liệu độc lập.
2.  **Kết nối:** Mạng lưới tuyển dụng chuyên biệt cho ngành F&B, nơi hồ sơ người dùng (Global User) có thể kết nối với nhiều Tenant.

### 1.2. Định nghĩa thuật ngữ kiến trúc
*   **Tenant (Khách thuê):** Một nhà hàng hoặc chuỗi nhà hàng sử dụng hệ thống. Dữ liệu của Tenant này **tuyệt đối không** được lộ sang Tenant khác.
*   **Global User (Người dùng toàn cục):** Tài khoản người dùng tồn tại độc lập với Tenant. Một User có thể là "Khách hàng" ở quán A, nhưng là "Nhân viên" ở quán B và là "Chủ quán" của quán C.
*   **Component-based:** Sử dụng các giải pháp mã nguồn mở tiêu chuẩn thay vì tự code (Keycloak cho Auth, MinIO cho Storage).

---

## 2. KIẾN TRÚC HỆ THỐNG & CÁC THÀNH PHẦN TÁI SỬ DỤNG (REUSABLE COMPONENTS)

Hệ thống được thiết kế theo hướng containerization (Docker) với các service vệ tinh:

### 2.1. Identity & Access Management (IAM) - **Keycloak**
*   **Vai trò:** Quản lý định danh, xác thực (Authentication) và phân quyền (Authorization).
*   **Cấu hình Multi-tenancy:**
    *   Sử dụng **Realm** hoặc **Group** để phân chia Tenant.
    *   Hỗ trợ đăng nhập SSO (Single Sign-On).
    *   Quản lý Roles: `platform_admin`, `owner`, `staff`, `user`.

### 2.2. Object Storage - **MinIO** (S3 Compatible)
*   **Vai trò:** Lưu trữ toàn bộ file tĩnh (Ảnh món ăn, Avatar, QR Code, File báo cáo).
*   **Cấu trúc Bucket:**
    *   `public-assets`: Tài nguyên chung của nền tảng.
    *   `tenant-{id}-assets`: Tài nguyên riêng của từng quán (Menu, Logo quán).
    *   `user-profiles`: CV, Avatar người dùng.

### 2.3. Database - **PostgreSQL** (Multi-tenant Strategy)
*   **Chiến lược:** **Row-level Isolation (Shared Database, Separate Schema hoặc Discriminator Column)**.
    *   Mỗi bảng dữ liệu thuộc về quán đều có cột `tenant_id`.
    *   Hệ thống Backend tự động gán `WHERE tenant_id = ...` trong mọi truy vấn thông qua Middleware.

### 2.4. Containerization - **Docker & Docker Compose**
*   Mỗi thành phần (Backend API, Frontend, Keycloak, MinIO, DB) đều chạy trong container riêng biệt.

---

## 3. TÁC NHÂN (ACTORS) VÀ PHÂN QUYỀN (KEYCLOAK MAPPING)

| Tác nhân | Keycloak Role | Mô tả |
| :--- | :--- | :--- |
| **Người dùng Nền tảng** | `global_user` | Người dùng vãng lai, ứng viên tìm việc. Có thể đăng nhập vào hệ thống chung. |
| **Nhân viên** | `tenant_staff` | Người dùng `global_user` được gán vào Group của một Tenant cụ thể. |
| **Chủ quán** | `tenant_owner` | Người tạo ra Tenant, có quyền quản trị cao nhất trong phạm vi Tenant đó. |
| **Khách hàng** | `anonymous` / `guest` | Khách ăn tại quán (không cần login). |

---

## 4. YÊU CẦU CHỨC NĂNG CHI TIẾT

### 4.1. Phân hệ Core & Nền tảng (Platform & Recruitment)
*Dành cho Actor: Người dùng Nền tảng*

*   **SYS-AUTH (Authentication):**
    *   Đăng ký/Đăng nhập chuyển hướng qua trang Login của **Keycloak**.
    *   Hỗ trợ Social Login (Google, Facebook) cấu hình tại Keycloak.
    *   Quản lý phiên đăng nhập (Session) tập trung.
*   **PF-01: Hồ sơ cá nhân (Global Profile):**
    *   Upload Avatar/CV (Lưu vào **MinIO** bucket `user-profiles`).
*   **PF-02: Xem & Tìm kiếm Quán:**
    *   Hiển thị danh sách Tenant công khai.
    *   API trả về URL ảnh từ MinIO (Presigned URL hoặc Public URL).
*   **PF-03: Tuyển dụng:**
    *   Apply vào một JobPost. Hệ thống tạo bản ghi `Application` liên kết `user_id` và `job_post_id`.

### 4.2. Phân hệ Vận hành Tenant - Nhân viên (Staff)
*Dành cho Actor: Nhân viên (Scope: Trong 1 Tenant cụ thể)*

*   **ST-01: Quản lý Bàn (Table Management):**
    *   **Real-time Update:** Sử dụng WebSocket để đồng bộ trạng thái bàn (Trống, Có khách, Đang dọn).
    *   **Nghiệp vụ Gộp bàn (Merge):**
        *   Input: `Table_A` (Master), `Table_B` (Slave).
        *   Logic: Update `Order.table_id` của B sang A. Update trạng thái B thành `Merged`. Lưu vết `Merge_History` để biết B đã gộp vào A.
    *   **Nghiệp vụ Tách bàn (Split):**
        *   Điều kiện: Bàn A đang trống (đã thanh toán).
        *   Logic: Truy xuất `Merge_History`, khôi phục trạng thái `Available` cho cả A và B.
*   **ST-02: Quản lý Order (Phiên bàn):**
    *   Thêm món/Sửa món.
    *   **Ràng buộc MinIO:** Ảnh món ăn hiển thị trên POS lấy từ bucket `tenant-{id}-assets`.
    *   **Xóa món:** Chỉ cho phép xóa khi `status == 'PENDING'`. Nếu `status == 'COOKING'`, hệ thống trả về lỗi 403 (cần quyền Manager).
*   **ST-04: Thanh toán (Payment):**
    *   Xác nhận tiền mặt -> Gọi API đóng phiên -> In hóa đơn (Template hóa đơn lưu trong DB tenant).

### 4.3. Phân hệ Quản trị Tenant - Chủ quán (Owner)
*Dành cho Actor: Chủ quán*

*   **OWN-01: Khởi tạo Tenant (Onboarding):**
    *   Khi User đăng ký mở quán:
        *   Tạo `tenant_id` mới trong DB.
        *   Tạo Group/Role mới trong Keycloak.
        *   Tạo Bucket `tenant-{id}-assets` trong MinIO (qua API MinIO).
*   **OWN-02: Quản lý Menu & Media:**
    *   Upload ảnh món ăn -> Backend upload sang MinIO -> Lưu URL vào DB.
*   **OWN-03: Quản lý Nhân sự & Phân quyền:**
    *   Thêm nhân viên: Nhập email User -> Hệ thống gán User đó vào Group của Tenant trong Keycloak.
*   **OWN-04: Cấu hình Thanh toán Online:**
    *   Lưu trữ `PartnerCode`, `SecretKey` (Mã hóa AES trong DB).
*   **OWN-05: Báo cáo (Analytics):**
    *   Tổng hợp dữ liệu theo `tenant_id`.
    *   Tính toán "Điểm hiệu suất nhân viên": Dựa trên số giờ làm (`Timesheets`) và số Orders xử lý.

### 4.4. Phân hệ Khách hàng (Customer)
*   **CUST-01: Quét QR & Order:**
    *   QR chứa thông tin: `https://app.domain.com/menu?tenantId=XYZ&tableId=123`.
    *   Hệ thống load Menu từ `tenantId` tương ứng.
*   **CUST-02: Thanh toán Online:**
    *   Gọi API Payment Gateway.
    *   Webhook từ Gateway gọi về Backend -> Backend update trạng thái Order -> Notify qua Socket tới Nhân viên.

---

## 5. YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL & TECHNICAL)

### 5.1. Bảo mật & Isolation (Multi-tenancy Security)
*   **Data Isolation:** Tất cả query database bắt buộc phải có điều kiện `WHERE tenant_id = current_tenant_id` (trừ các bảng Global như User). Việc này nên được handle tại lớp **Middleware** hoặc **Hibernate Filter/Sequelize Scope**.
*   **API Security:** Mọi API request phải kèm Bearer Token (JWT) từ Keycloak. Backend validate token và trích xuất `tenant_id` từ token claims (nếu có) hoặc header.

### 5.2. Khả năng mở rộng & Docker
*   Hệ thống phải được đóng gói thành các **Docker Images**:
    *   `backend-api`: Node.js/Java/Go.
    *   `frontend-web`: React/Vue (Nginx serve).
    *   `socket-server`: Service riêng cho real-time.
*   Sử dụng **Docker Compose** cho môi trường Dev và **K8s/Docker Swarm** cho Prod.
*   Keycloak và MinIO chạy như các service độc lập trong mạng docker (`docker network`).

### 5.3. Hiệu năng
*   Ảnh từ MinIO nên được cache qua CDN (Cloudflare/AWS CloudFront) hoặc Nginx Cache khi ra môi trường Production.
*   Sử dụng Redis để cache thông tin Menu và Session bàn ăn để giảm tải DB.

https://aistudio.google.com/prompts/1IdRgz0XF6vjYWMHS7ugzXXDwxirugTX_