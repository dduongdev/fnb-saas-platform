# TÀI LIỆU PHÂN TÍCH & THIẾT KẾ CƠ SỞ DỮ LIỆU

## 1. NGUYÊN TẮC THIẾT KẾ
1.  **Multi-tenancy:** Tất cả các bảng dữ liệu thuộc về quán (Menu, Order, Table, Staff...) bắt buộc phải có cột `tenant_id` (VARCHAR/UUID).
2.  **Identity:** `user_id` sẽ là UUID được đồng bộ từ **Keycloak**. Hệ thống này không lưu mật khẩu.
3.  **Storage:** Hình ảnh (Avatar, Món ăn) chỉ lưu đường dẫn URL (kết quả trả về từ MinIO).
4.  **Audit:** Các bảng quan trọng đều có `created_at`, `updated_at`.

---

## 2. PHÂN TÍCH CHI TIẾT CÁC THỰC THỂ (ENTITIES)

Hệ thống được chia thành 3 nhóm thực thể chính: **Global (Toàn cục)**, **HRM (Nhân sự & Tuyển dụng)**, và **Operation (Vận hành & POS)**.

### NHÓM 1: GLOBAL & SYSTEM (Dùng chung cho cả nền tảng)

1.  **`users` (Người dùng nền tảng)**
    *   Lưu thông tin profile cơ bản độc lập với các quán.
    *   Dữ liệu này được dùng để đi xin việc (Profile ứng viên).
    *   *Quan trọng:* `trust_score` (Điểm tín nhiệm) tính dựa trên hiệu suất làm việc.

2.  **`tenants` (Quán/Nhà hàng)**
    *   Thông tin quán, cấu hình thanh toán (lưu JSON mã hóa cho PartnerCode/SecretKey).
    *   `owner_id`: Link tới bảng users.

### NHÓM 2: HRM (QUẢN LÝ NHÂN SỰ & TUYỂN DỤNG)

3.  **`employees` (Nhân viên trong quán)**
    *   Bảng liên kết giữa `users` và `tenants`.
    *   Lưu vai trò (Manager, Staff) và trạng thái (Active, Resigned).

4.  **`shifts` (Ca làm việc / Phân ca)**
    *   Chủ quán phân ca cho nhân viên.
    *   ShiftsStatus xác định trạng thái ca làm việc: PLANNED, COMPLETED - chủ quán đã xác nhận nhân viên có đi làm, ABSENT - nhân viên vắng mặt

5.  **`application`** (Ứng tuyển)
    * Người dùng hệ thống ứng tuyển vào một JobPost.

6.  **`job_posts`** (Tin tuyển dụng)

### NHÓM 3: OPERATION & POS (VẬN HÀNH QUÁN)
*Tất cả bảng nhóm này đều có `tenant_id`.*

7.  **`categories` & `products` (Thực đơn)**
    *   Menu của quán. `products` chứa URL ảnh từ MinIO.

8.  **`tables` (Bàn ăn - Logic phức tạp)**
    *   **Logic Gộp bàn:**
        *   Sử dụng chiến lượng Master-Slave theo cấu trúc hình sao (Star Topology).

9.  **`orders` (Phiên ăn / Hóa đơn)**
    *   Đại diện cho một lượt khách ăn.
    *   Trạng thái: `OPEN` (Đang ăn), `WAITING_PAYMENT` (Chờ tt), `PAID` (Xong), `CANCELLED`.
    *   Lưu tổng tiền, phương thức thanh toán.

10. **`order_items` (Món trong order)**
    *   Lưu chi tiết món: Số lượng, giá tại thời điểm bán, ghi chú.
    *   **Logic Xóa món:** Cột `status` (`PENDING`, `SENT_TO_KITCHEN`, `COOKING`, `SERVED`).
    *   *Quy tắc:* Chỉ cho phép xóa/sửa khi `status = PENDING`.

---

## 3. MÔ HÌNH HÓA DATABASE (DBDIAGRAM CODE)

Bạn có thể copy đoạn mã dưới đây và paste vào trang **[dbdiagram.io](https://dbdiagram.io/)** để xem sơ đồ trực quan.

```dbml
// --- GLOBAL MODULE (Platform) --- Đã triển khai

Table users {
  id varchar(64) [pk, note: "Keycloak User ID (UUID)"]
  email varchar(255) [unique]
  full_name varchar(255)
  phone varchar(20)
  avatar_url varchar(500) [note: "MinIO URL"]
  trust_score int [default: 100, note: "Điểm tín nhiệm"]
  created_at timestamp [default: `now()`]
  updated_at timestamp
}

Table tenants {
  id varchar(64) [pk, note: "UUID"]
  name varchar(255)
  address text
  owner_id varchar(64) [ref: > users.id]
  payment_config json [note: "Encrypted: {partnerCode, secretKey, ...}"]
  logoUrl varchar(500)
  is_active boolean [default: true]
  created_at timestamp
}

// --- HRM MODULE (Tenant Scope) --- Đã triển khai

Table employees {
  id int [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  user_id varchar(64) [ref: > users.id]
  role varchar(20) [note: "'MANAGER', 'STAFF'"] // Là sự chuẩn bị cho sự mở rộng sau này, vì ban đầu hệ thống hướng đến các quán ăn nhỏ, chỉ có nhân viên.
  status varchar(20) [default: 'ACTIVE', note: "'ACTIVE', 'RESIGNED'"]
  joined_at date
  is_deleted boolean
  updated_at datetime
  created_at datetime
  indexes {
    (tenant_id, user_id) [unique]
  }
}

Table applications {
  id int [pk, increment]
  user_id varchar(64) [ref: > users.id]
  job_post_id bigint [ref: > job_posts.id]
  status varchar(20) [default: 'PENDING', note: "'PENDING', 'APPROVED', 'REJECTED'"]
  message text
  is_deleted boolean
  updated_at datetime
  created_at datetime
  tenant_id varchar(64) [ref: > tenants.id]
}

Table shifts {
  id bigint [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  employee_id int [ref: > employees.id]
  start_time datetime
  end_time datetime
  note varchar(255)
  status varchar(20) [default: 'PLANNED', note: "'PLANNED', 'COMPLETED', 'ABSENT'"]
  is_deleted boolean
  updated_at datetime
  created_at datetime
  Note: "Lịch làm việc được phân công. Dùng để check conflict."
}

Table job_posts {
  id bigint [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  title varchar(64)
  description text
  is_active boolean
  is_deleted boolean
  updated_at datetime
  created_at datetime
}

// --- MENU MODULE (Tenant Scope) --- Đã triển khai 

Table categories {
  id int [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  name varchar(100)
  display_order int
  is_active boolean
  is_deleted boolean
  is_default boolean [default: false] // non null
  updated_at datetime
  created_at datetime
}

Table products {
  id bigint [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  category_id int [ref: > categories.id]
  name varchar(255)
  price decimal(10,2)
  description text
  status varchar(20) [default: 'AVAILABLE', note: "'AVAILABLE', 'OUT_OF_STOCK', 'HIDDEN'"]
  is_deleted boolean
  updated_at datetime
  created_at datetime
}

Table productimages {
  id bigint [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  is_deleted boolean
  updated_at datetime
  created_at datetime
  image_url varchar(500) [note: "MinIO URL"]
  is_primary boolean
  display_order int
  product_id int [ref: > products.id]
}

// --- POS & OPERATION MODULE (Tenant Scope) ---

Table tables {
  id int [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  name varchar(50) [note: "Bàn 1, Bàn 2..."]

  status varchar(20) [default: 'AVAILABLE', note: "'AVAILABLE', 'SERVING', 'RESERVED'"]
  
  master_table_id int [ref: > tables.id, null] 

  qr_code_url varchar(500)
  is_deleted boolean
  updated_at datetime
  created_at datetime
  indexes {
    (tenant_id, id)
  }
}

Table orders {
  id bigint [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  table_id int [ref: > tables.id]
  
  // Thông tin thanh toán
  total_amount decimal(15,2) [default: 0]
  payment_method varchar(20) [null, note: "'CASH', 'MOMO', 'VNPAY'"]
  payment_status varchar(20) [default: 'UNPAID', note: "'UNPAID', 'PAID'"]
  
  // Trạng thái phiên
  status varchar(20) [default: 'OPEN', note: "'OPEN', 'COMPLETED', 'CANCELLED'"]
  
  created_by int [ref: > employees.id, null, note: "Null nếu khách tự order"]
  completed_at timestamp

  is_deleted boolean
  updated_at datetime
  created_at datetime
}

Table order_items {
  id bigint [pk, increment]
  order_id bigint [ref: > orders.id] // Luôn trỏ về Order của bàn Master
  product_id bigint [ref: > products.id]
  
  original_table_id int [ref: > tables.id] 
  
  quantity int
  price decimal(10,2)
  note varchar(255)
  status varchar(20)
  created_at timestamp
}

// --- REPORTING (Aggregated Data) ---
// Bảng này thường được update async qua RabbitMQ để tránh query nặng
Table daily_stats {
  id bigint [pk, increment]
  tenant_id varchar(64) [ref: > tenants.id]
  report_date date
  total_revenue decimal(15,2)
  total_orders int
  updated_at timestamp
}
```