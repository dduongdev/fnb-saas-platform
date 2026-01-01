# TÀI LIỆU KIẾN TRÚC HỆ THỐNG (SAD)
**Dự án:** F&B SaaS Platform
**Kiến trúc:** Modular Monolith (Multi-tenant)
**Tech Stack:** Java Spring Boot, MySQL, ReactJS, Keycloak, MinIO.

## 1. TỔNG QUAN KIẾN TRÚC
Chúng ta sẽ sử dụng kiến trúc **Modular Monolith**. Toàn bộ backend nằm trong một Spring Boot Application duy nhất nhưng được chia tách module nghiêm ngặt. Hệ thống chạy trên nền tảng **Docker**, giao tiếp với các dịch vụ vệ tinh (Keycloak, MinIO, MySQL) qua mạng nội bộ Docker.

### 1.1. Sơ đồ Cấu trúc (High-Level Diagram)

```mermaid
graph TD
    Client_Web[ReactJS App (Web/Mobile)] --> |HTTPS / WSS| NGINX[Nginx Reverse Proxy]
    
    subgraph "Docker Infrastructure Network"
        NGINX --> |REST API| BE[Spring Boot Backend]
        NGINX --> |WebSocket| BE
        
        BE --> |Auth/Validation| KC[Keycloak]
        BE --> |Storage S3| MIN[MinIO]
        BE --> |Data| DB[(MySQL 8.0)]
        
        %% Realtime internal flow
        BE -.-> |Stomp Broker| BE
    end
```

---

## 2. CHI TIẾT CÔNG NGHỆ (TECH STACK)

### 2.1. Backend: Java Spring Boot 3.x
*   **Ngôn ngữ:** Java 17 hoặc 21 (LTS).
*   **Framework:** Spring Boot 3 (tận dụng native support, hiệu năng cao).
*   **ORM:** **Spring Data JPA (Hibernate)**.
*   **Build Tool:** Maven hoặc Gradle (khuyên dùng Maven cho cấu trúc đa module dễ quản lý).
*   **Real-time:** **Spring WebSocket + STOMP**.
    *   *Lý do chọn:* Đây là chuẩn "native" của Spring. Nó hỗ trợ giao thức STOMP (Simple Text Oriented Messaging Protocol) giúp frontend (React) dễ dàng subscribe các kênh (topic) dữ liệu. Tích hợp chặt chẽ với Spring Security.

### 2.2. Database: MySQL 8.0
*   **Mô hình:** Relational Database.
*   **Chiến lược Multi-tenant:** **Discriminator Column** (Cột `tenant_id` trong các bảng).
    *   *Ưu điểm:* Tiết kiệm tài nguyên, dễ backup, dễ query báo cáo tổng hợp (Cross-tenant reporting) cho Admin hệ thống.
    *   *Xử lý:* Sử dụng `Hibernate Filter` hoặc `AOP (Aspect Oriented Programming)` để tự động gán `WHERE tenant_id = ?` vào mọi câu truy vấn.

### 2.3. Authentication & Authorization: Keycloak
*   **Phiên bản:** Keycloak (Docker image `quay.io/keycloak/keycloak`).
*   **Giao thức:** OpenID Connect (OIDC).
*   **Tích hợp Spring Boot:** Sử dụng thư viện `spring-boot-starter-oauth2-resource-server`. Backend đóng vai trò là Resource Server, xác thực JWT Token do Keycloak cấp.

### 2.4. File Storage: MinIO
*   **Giao thức:** S3 Compatible API.
*   **Tích hợp Spring Boot:** Sử dụng `AWS SDK for Java v2` (chỉ dùng module S3) để upload/download file.

### 2.5. Frontend: ReactJS
*   **Framework:** React 18+ (Dùng **Vite** để build cho nhẹ).
*   **State Management:** Redux Toolkit hoặc React Query (TanStack Query) để quản lý cache dữ liệu.
*   **Real-time Client:** Sử dụng thư viện `@stomp/stompjs` và `sockjs-client` để kết nối với Spring WebSocket.

---

## 3. THIẾT KẾ MODULE (MODULARIZATION)

Trong Spring Boot, thay vì tạo nhiều file JAR (Microservices), ta tạo nhiều **Package** lớn, mỗi package là một module nghiệp vụ, cô lập nhau.

**Cấu trúc thư mục mã nguồn (`src/main/java/com/project/fnb`):**

```text
com.project.fnb
├── common            # Shared Kernel (DTOs, Utils, Exception, BaseEntity)
├── config            # Cấu hình (Security, Swagger, WebSocket, MinIO)
├── security          # Xử lý TenantContext, JWT Converter
│
├── modules
│   ├── recruitment   # [Module] Tuyển dụng (Global Data)
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   └── entity
│   │
│   ├── pos           # [Module] Bán hàng (Tenant Data)
│   │   ├── controller
│   │   ├── service   # Xử lý logic Gộp bàn, Tách bàn
│   │   ├── repository
│   │   └── entity    # Order, Table, OrderItem
│   │
│   ├── inventory     # [Module] Kho & Menu (Tenant Data)
│   │
│   └── reporting     # [Module] Báo cáo
│
└── Application.java
```

### Quy tắc giao tiếp giữa các Module:
1.  **Giao tiếp Đồng bộ (Direct Call):** Module `POS` có thể gọi Service của `Inventory` (ví dụ: trừ kho khi bán). Do cùng chạy trong 1 JVM nên tốc độ rất nhanh.
2.  **Giao tiếp Bất đồng bộ (Event-Driven):** Sử dụng **Spring Application Events**.
    *   *Ví dụ:* Khi `OrderService` (Module POS) hoàn tất thanh toán -> Publish sự kiện `OrderPaidEvent`.
    *   `ReportingService` (Module Reporting) lắng nghe sự kiện này để cập nhật doanh thu.
    *   Cách này giúp Module Báo cáo không phụ thuộc trực tiếp vào Module POS (Loose Coupling).

---

## 4. CHI TIẾT GIẢI PHÁP REAL-TIME (SPRING WEBSOCKET)

Đây là phần quan trọng để Bếp và Thu ngân nhận thông tin tức thời.

### 4.1. Luồng hoạt động
1.  **Kết nối:** Client (React) kết nối socket tới endpoint: `wss://api.domain.com/ws`.
2.  **Subscribe (Đăng ký nhận tin):**
    *   Nhân viên Bếp quán A subscribe topic: `/topic/tenant/tenant_A/kitchen`.
    *   Nhân viên Thu ngân quán A subscribe topic: `/topic/tenant/tenant_A/cashier`.
3.  **Gửi tin (Message Sending):**
    *   Khách quét QR gọi món -> API REST `POST /api/orders` được gọi.
    *   Backend lưu Order vào MySQL.
    *   Backend dùng `SimpMessagingTemplate` bắn message tới topic `/topic/tenant/tenant_A/kitchen`.
    *   Màn hình Bếp tự động hiện món mới.

### 4.2. Cấu hình Spring Boot
Sử dụng **RabbitMQ** làm Message Broker.

## 5. QUY TRÌNH TRIỂN KHAI (DEPLOYMENT)

Sử dụng `docker-compose.yml` để dựng toàn bộ hệ thống local hoặc trên VPS.

**Các Service trong Docker:**
1.  **mysql-db:** Container chạy MySQL 8.
2.  **minio-server:** Container chạy MinIO.
3.  **keycloak-auth:** Container chạy Keycloak.
4.  **backend-app:** Container chạy Spring Boot (Build ra file JAR).
5.  **frontend-web:** Container chạy Nginx (Chứa file build static của React).

### Luồng Request & Bảo mật (Security Flow)
1.  Client đăng nhập trên React -> Chuyển hướng sang Keycloak -> Nhập Pass -> Keycloak trả về **Access Token (JWT)**.
2.  React lưu Token. Mỗi khi gọi API hoặc mở WebSocket, đính kèm Token này vào Header (`Authorization: Bearer ...`).
3.  Spring Boot chặn Request:
    *   Validate Token với Keycloak (hoặc check signature offline).
    *   Đọc `tenant_id` từ Token (nếu User là nhân viên).
    *   Lưu `tenant_id` vào `TenantContextHolder` (ThreadLocal).
4.  Hibernate/JPA tự động lấy `TenantContextHolder` để filter dữ liệu MySQL.

---

## TỔNG KẾT
Với kiến trúc này:
1.  **Spring Boot + MySQL:** Đảm bảo sự chắc chắn, transaction mạnh mẽ (cần thiết cho tính tiền, kho vận).
2.  **WebSocket STOMP:** Chuẩn mực cho Java, giải quyết tốt bài toán Realtime Order.
3.  **Modular Monolith:** Giúp bạn phát triển nhanh, dễ debug như một khối thống nhất, nhưng vẫn gọn gàng để mở rộng sau này.
4.  **Keycloak/MinIO:** Giảm bớt lượng code phải viết cho tính năng đăng nhập và lưu file.
