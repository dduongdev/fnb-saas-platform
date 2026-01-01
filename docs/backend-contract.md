# Backend Contract - F&B SaaS Platform

> **Lưu ý**: Tài liệu này chỉ phản ánh những gì **thực tế đang có** trong code backend. Không bổ sung tính năng.

---

## 1. Tổng quan Backend

### 1.1. Bài toán Backend đang giải quyết

Backend này là **hệ thống quản lý quán F&B** (ăn uống, cà phê) quy mô **nhỏ đến trung bình** (1-3 chi nhánh), được thiết kế dành cho:

- **Chủ quán** (Owner): quản lý toàn bộ quán của mình
- **Nhân viên** (Staff): phục vụ, bán hàng, xử lý order
- **Khách hàng** (Customer): xem menu, tự gọi món (nếu quán cho phép)

**Đặc điểm chính**:
- Multi-tenant: Mỗi quán (tenant) có dữ liệu riêng biệt, cách ly hoàn toàn
- Đơn giản, dễ dùng, không phức tạp như enterprise
- Chạy ổn định hằng ngày cho hoạt động bán hàng

### 1.2. Tech Stack

- **Framework**: Spring Boot 3.5.8, Java 21
- **Database**: MySQL (JPA/Hibernate)
- **Authentication**: Keycloak (OAuth2 Resource Server)
- **Multi-tenant**: ThreadLocal + Hibernate Filter + AOP
- **Storage**: MinIO (upload ảnh)
- **Message Queue**: RabbitMQ (notification, events)
- **Payment Gateway**: VNPay, MoMo
- **WebSocket**: STOMP (real-time notification)

### 1.3. Các Module/Domain chính

Backend chia thành **6 modules** chính:

| Module | Mục đích |
|--------|----------|
| **global** | Authentication, User, Tenant (Quán) |
| **menu** | Quản lý thực đơn: Category, Product |
| **pos** | Point of Sale: Order, Table (Bàn), Customer |
| **hrm** | Quản lý nhân sự: JobPost, Employee, Recruitment |
| **payment** | Tích hợp thanh toán: VNPay, MoMo |
| **reporting** | Báo cáo doanh thu, thống kê |

---

## 2. API Contract (Các Endpoint của Backend)

### 2.1. Cấu trúc Response chung

Tất cả API trả về cấu trúc:

```json
{
  "code": 200,
  "message": "Success",
  "data": { /* payload */ }
}
```

- **200**: Success
- **4xx/5xx**: Error (message sẽ chứa lý do)

### 2.2. Authentication & Authorization

#### 2.2.1. Auth Module (`/api/auth`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| POST | `/api/auth/sync` | ✅ | ❌ | Đồng bộ User từ Keycloak token vào DB |

**Request**: None (lấy từ JWT)  
**Response**:
```json
{
  "id": "uuid",
  "username": "john.doe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "avatarUrl": "https://..."
}
```

#### 2.2.2. Profile Module (`/api/profile`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| POST | `/api/profile/avatar` | ✅ | ❌ | Upload avatar cho user |

**Request**: multipart/form-data
- `file`: MultipartFile

**Response**: URL của ảnh đã upload

---

### 2.3. Tenant (Quán) Management

#### 2.3.1. Tenant Controller (`/api/tenants`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| POST | `/api/tenants` | ✅ | ❌ | Tạo quán mới (user trở thành owner) |
| PUT | `/api/tenants/{id}` | ✅ | ❌ | Cập nhật thông tin quán |
| GET | `/api/tenants/me` | ✅ | ❌ | Lấy danh sách quán của user |
| GET | `/api/tenants/{id}` | ❌ | ❌ | Lấy chi tiết 1 quán (PUBLIC) |
| PATCH | `/api/tenants/{id}/status` | ✅ | ❌ | Bật/tắt trạng thái quán |
| PUT | `/api/tenants/{id}/payment-config` | ✅ | ❌ | Cấu hình thanh toán (VNPay, Momo) |

**Tạo Tenant** (`POST /api/tenants`):
```
Content-Type: multipart/form-data
- name: string (required)
- address: string (required)
- logo: file (optional)
```

**Response**:
```json
{
  "id": "uuid",
  "name": "Quán Cà Phê ABC",
  "address": "123 Điện Biên Phủ",
  "ownerId": "user-id",
  "isActive": true,
  "logoUrl": "https://...",
  "createdAt": "2024-01-01T10:00:00",
  "paymentConfig": {
    "vnpay": {
      "enabled": true,
      "tmnCode": "...",
      "hashSecret": "..."
    },
    "momo": null
  }
}
```

#### 2.3.2. Public Tenant (`/api/public/tenants`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/public/tenants` | ❌ | ❌ | Danh sách quán đang hoạt động (pagination) |

**Query Params**:
- `page`: int (default: 0)
- `size`: int (default: 10)

**Response**: Page<TenantPublicDto>

---

### 2.4. Menu Management

#### 2.4.1. Category Controller (`/api/categories`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/categories` | ❌ | ✅ | Lấy tất cả danh mục của quán |
| POST | `/api/categories` | ✅ | ✅ | Tạo danh mục mới |
| DELETE | `/api/categories/{id}` | ✅ | ✅ | Xóa danh mục |

**Tạo Category** (`POST /api/categories`):
```
- name: string (required)
- order: integer (default: 1)
```

**Response**:
```json
{
  "id": 1,
  "name": "Cà phê",
  "displayOrder": 1,
  "isDefault": false
}
```

#### 2.4.2. Product Controller (`/api/products`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/products/{id}` | ❌ | ✅ | Chi tiết sản phẩm |
| POST | `/api/products` | ✅ | ✅ | Tạo sản phẩm mới |
| PUT | `/api/products/{id}` | ✅ | ✅ | Cập nhật thông tin sản phẩm |
| POST | `/api/products/{id}/images` | ✅ | ✅ | Thêm ảnh cho sản phẩm |
| DELETE | `/api/products/images/{imageId}` | ✅ | ✅ | Xóa ảnh sản phẩm |
| DELETE | `/api/products/{id}` | ✅ | ✅ | Xóa sản phẩm (soft delete) |

**Tạo Product** (`POST /api/products`):
```
Content-Type: multipart/form-data
- categoryId: integer (required)
- name: string (required)
- price: decimal (required)
- description: string (optional)
- images: List<MultipartFile> (optional)
```

**Response**:
```json
{
  "id": 100,
  "name": "Cà phê đen",
  "description": "Cà phê truyền thống",
  "price": 25000,
  "status": "AVAILABLE",
  "category": {
    "id": 1,
    "name": "Cà phê"
  },
  "images": [
    {
      "id": 1,
      "imageUrl": "https://..."
    }
  ]
}
```

**Product Status Enum**:
- `AVAILABLE`: Có sẵn
- `OUT_OF_STOCK`: Hết hàng
- `HIDDEN`: Ẩn khỏi menu

---

### 2.5. POS (Point of Sale) - Bán hàng

#### 2.5.1. Table Controller (`/api/pos/tables`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/pos/tables` | ✅ | ✅ | Danh sách bàn |
| POST | `/api/pos/tables` | ✅ | ✅ | Tạo bàn mới |
| POST | `/api/pos/tables/merge` | ✅ | ✅ | Gộp nhiều bàn thành 1 |
| POST | `/api/pos/tables/{id}/split` | ✅ | ✅ | Tách bàn / Trả bàn về trạng thái EMPTY |

**Table Response**:
```json
{
  "id": 1,
  "name": "Bàn 01",
  "status": "EMPTY",
  "currentOrderId": null
}
```

**Table Status Enum**:
- `EMPTY`: Trống
- `OCCUPIED`: Đang có khách
- `RESERVED`: Đã đặt trước (chưa implement)

**Merge Tables** (`POST /api/pos/tables/merge`):
```json
{
  "masterTableId": 1,
  "slaveTableIds": [2, 3]
}
```

#### 2.5.2. Order Controller (`/api/pos/orders`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/pos/orders/table/{tableId}` | ❌ | ✅ | Lấy order hiện tại của bàn (tạo mới nếu chưa có) |
| POST | `/api/pos/orders/table/{tableId}/items` | ❌ | ✅ | Thêm món vào order |
| DELETE | `/api/pos/orders/items/{itemId}` | ❌ | ✅ | Xóa món (nếu chưa gửi bếp) |
| POST | `/api/pos/orders/{orderId}/cancel` | ❌ | ✅ | Hủy đơn hàng |
| POST | `/api/pos/orders/{orderId}/pay/cash` | ❌ | ✅ | Thanh toán tiền mặt |

**Lưu ý**: Các API Order **KHÔNG yêu cầu JWT** (để khách tự gọi món), nhưng **BẮT BUỘC có `X-Tenant-ID` header**.

**Order Response**:
```json
{
  "id": 500,
  "tableId": 1,
  "tableName": "Bàn 01",
  "totalAmount": 75000,
  "status": "OPEN",
  "items": [
    {
      "id": 1,
      "productId": 100,
      "productName": "Cà phê đen",
      "quantity": 2,
      "price": 25000,
      "subtotal": 50000
    },
    {
      "id": 2,
      "productId": 101,
      "productName": "Bánh mì",
      "quantity": 1,
      "price": 25000,
      "subtotal": 25000
    }
  ],
  "createdAt": "2024-01-01T10:00:00"
}
```

**Order Status Enum**:
- `OPEN`: Đang phục vụ
- `WAITING_PAYMENT`: Chờ thanh toán
- `COMPLETED`: Đã thanh toán xong
- `CANCELLED`: Đã hủy

**Add Items** (`POST /api/pos/orders/table/{tableId}/items`):
```json
[
  {
    "productId": 100,
    "quantity": 2
  },
  {
    "productId": 101,
    "quantity": 1
  }
]
```

**Pay Cash** (`POST /api/pos/orders/{orderId}/pay/cash`):
```json
{
  "orderId": 500,
  "totalAmount": 75000,
  "paymentMethod": "CASH",
  "items": [...],
  "qrCode": "data:image/png;base64,..."
}
```
> Invoice có QR code để khách scan (QR chứa thông tin hóa đơn)

#### 2.5.3. Customer Controller (`/api/pos/public`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/pos/public/menu` | ❌ | ✅ | Lấy menu công khai (để khách xem) |
| GET | `/api/pos/public/info/{tableId}` | ❌ | ❌ | Lấy thông tin bàn + quán (không cần tenant header) |

**Menu Response**:
```json
[
  {
    "categoryId": 1,
    "categoryName": "Cà phê",
    "products": [
      {
        "id": 100,
        "name": "Cà phê đen",
        "price": 25000,
        "imageUrl": "https://..."
      }
    ]
  }
]
```

---

### 2.6. HRM (Human Resource Management)

#### 2.6.1. JobPost Controller (`/api/hrm/jobs`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/hrm/jobs` | ✅ | ✅ | Danh sách tin tuyển dụng của quán (pagination) |
| GET | `/api/hrm/jobs/{id}` | ✅ | ✅ | Chi tiết tin tuyển dụng |
| POST | `/api/hrm/jobs` | ✅ | ✅ | Tạo tin tuyển dụng |
| PUT | `/api/hrm/jobs/{id}` | ✅ | ✅ | Cập nhật tin tuyển dụng |
| DELETE | `/api/hrm/jobs/{id}` | ✅ | ✅ | Xóa tin tuyển dụng |

**JobPost Request**:
```json
{
  "title": "Tuyển nhân viên phục vụ",
  "description": "...",
  "isActive": true
}
```

#### 2.6.2. Public Job Controller (`/api/public/jobs`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/public/jobs` | ❌ | ❌ | Danh sách tất cả tin tuyển dụng (pagination) |

**Response**: Page<JobPostResponse> (kèm thông tin quán: tên, logo)

#### 2.6.3. Recruitment Controller (`/api/recruitment`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| POST | `/api/recruitment/apply` | ✅ | ❌ | Ứng tuyển vào một tin tuyển dụng |
| GET | `/api/recruitment/jobs/{jobId}/applications` | ✅ | ✅ | Danh sách đơn ứng tuyển của 1 tin |
| PATCH | `/api/recruitment/applications/{id}` | ✅ | ✅ | Duyệt/Từ chối đơn ứng tuyển |

**Apply Request**:
```json
{
  "jobId": 10,
  "coverLetter": "Tôi muốn ứng tuyển vị trí này..."
}
```

**Process Application** (`PATCH /api/recruitment/applications/{id}`):
```json
{
  "status": "APPROVED"
}
```

**Application Status Enum**:
- `PENDING`: Chờ xét duyệt
- `APPROVED`: Đã duyệt (user trở thành nhân viên)
- `REJECTED`: Từ chối

#### 2.6.4. Staff Controller (`/api/staff`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/staff` | ✅ | ✅ | Danh sách nhân viên (pagination) |
| DELETE | `/api/staff/{id}` | ✅ | ✅ | Xóa nhân viên (thu hồi quyền truy cập) |

**Employee Response**:
```json
{
  "id": 1,
  "userId": "uuid",
  "username": "john.doe",
  "fullName": "John Doe",
  "role": "STAFF",
  "status": "ACTIVE",
  "joinedAt": "2024-01-01"
}
```

**Employee Role Enum**:
- `MANAGER`: Quản lý
- `STAFF`: Nhân viên

**Employee Status Enum**:
- `ACTIVE`: Đang làm việc
- `RESIGNED`: Đã nghỉ việc

---

### 2.7. Payment

#### 2.7.1. Payment Controller (`/api/public/payment`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/public/payment/methods/{tenantId}` | ❌ | ❌ | Danh sách PP thanh toán của quán |
| POST | `/api/public/payment/create-url` | ❌ | ❌ | Tạo URL thanh toán (VNPay/Momo) |

**Payment Methods Response**:
```json
[
  {
    "code": "CASH",
    "name": "Tiền mặt",
    "iconUrl": "https://..."
  },
  {
    "code": "VNPAY",
    "name": "VNPay QR",
    "iconUrl": "https://..."
  }
]
```

**Create Payment URL**:
```json
{
  "orderId": 500,
  "paymentMethodCode": "VNPAY"
}
```
Response: URL để redirect khách đến cổng thanh toán

#### 2.7.2. Payment Callback (`/api/public/payment`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/public/payment/vnpay-ipn` | ❌ | ❌ | IPN callback từ VNPay (cổng thanh toán gọi) |

**Lưu ý**: Endpoint này được VNPay gọi, không phải frontend. Backend tự verify checksum và cập nhật trạng thái order.

---

### 2.8. Reporting

#### 2.8.1. Reporting Controller (`/api/reports`)

| Method | Endpoint | Auth | Tenant | Mô tả |
|--------|----------|------|--------|-------|
| GET | `/api/reports/revenue` | ✅ | ✅ | Báo cáo doanh thu theo ngày |
| GET | `/api/reports/top-products` | ✅ | ✅ | Top món bán chạy |
| GET | `/api/reports/peak-hours` | ✅ | ✅ | Biểu đồ khung giờ đắt khách |

**Query Params** (tất cả các API):
- `from`: LocalDate (default: 30 ngày trước)
- `to`: LocalDate (default: hôm nay)
- `limit`: int (chỉ cho top-products, default: 5)

**Revenue Report Response**:
```json
[
  {
    "date": "2024-01-01",
    "totalRevenue": 1500000,
    "orderCount": 50
  },
  {
    "date": "2024-01-02",
    "totalRevenue": 2000000,
    "orderCount": 65
  }
]
```

**Top Products Response**:
```json
[
  {
    "productId": 100,
    "productName": "Cà phê đen",
    "quantitySold": 120,
    "totalRevenue": 3000000
  }
]
```

**Peak Hours Response**:
```json
[
  {
    "hour": 8,
    "orderCount": 15,
    "totalRevenue": 450000
  },
  {
    "hour": 9,
    "orderCount": 25,
    "totalRevenue": 750000
  }
]
```

---

## 3. Nghiệp vụ thể hiện qua API

### 3.1. Chủ quán có thể làm gì?

**Quản lý quán**:
- Tạo quán mới, cập nhật thông tin quán
- Bật/tắt trạng thái hoạt động
- Cấu hình cổng thanh toán (VNPay, Momo)

**Quản lý thực đơn**:
- Tạo/xóa danh mục sản phẩm
- Tạo/sửa/xóa sản phẩm
- Thêm/xóa ảnh sản phẩm
- Ẩn/hiện sản phẩm (AVAILABLE / HIDDEN)

**Quản lý bàn**:
- Tạo bàn mới
- Gộp nhiều bàn thành 1
- Tách bàn

**Quản lý nhân viên**:
- Đăng tin tuyển dụng
- Xem danh sách đơn ứng tuyển
- Duyệt/từ chối đơn (duyệt → user trở thành nhân viên)
- Xóa nhân viên khỏi quán

**Báo cáo**:
- Xem doanh thu theo ngày
- Xem top món bán chạy
- Xem khung giờ đắt khách

### 3.2. Nhân viên có thể làm gì?

Backend **KHÔNG phân quyền chi tiết** giữa Owner vs Staff. Tất cả API quán đều yêu cầu `X-Tenant-ID` header, không kiểm tra role cụ thể.

→ **Frontend phải tự xử lý phân quyền** dựa trên JWT role.

### 3.3. Khách hàng có thể làm gì?

- Xem menu công khai của quán
- Lấy thông tin bàn + quán
- Tạo order (tự gọi món)
- Thêm/xóa món trong order
- Thanh toán (tiền mặt hoặc quét mã VNPay/Momo)

### 3.4. Các luồng nghiệp vụ chính

#### 3.4.1. Luồng bán hàng (Điểm mấu chốt)

1. **Khách ngồi bàn** → Frontend lấy tableId (từ QR code hoặc chọn thủ công)
2. **GET /api/pos/orders/table/{tableId}** → Backend tạo order mới (status = OPEN) nếu chưa có
3. **POST /api/pos/orders/table/{tableId}/items** → Thêm món vào order
4. **DELETE /api/pos/orders/items/{itemId}** → Xóa món (nếu khách đổi ý)
5. **POST /api/pos/orders/{orderId}/pay/cash** → Thanh toán tiền mặt
   - Order chuyển sang `COMPLETED`
   - Bàn chuyển về `EMPTY`
   - Trả về invoice (có QR code)

#### 3.4.2. Luồng thanh toán online (VNPay)

1. **POST /api/public/payment/create-url** → Tạo URL thanh toán
2. Frontend redirect khách đến VNPay
3. Khách quét mã / thanh toán
4. VNPay gọi **GET /api/public/payment/vnpay-ipn** (IPN callback)
5. Backend verify checksum → Cập nhật order → Giải phóng bàn
6. Gửi notification qua WebSocket cho frontend

#### 3.4.3. Luồng tuyển dụng

1. **Chủ quán**: POST /api/hrm/jobs → Đăng tin tuyển dụng
2. **Người xin việc**: POST /api/recruitment/apply → Ứng tuyển
3. **Chủ quán**: GET /api/recruitment/jobs/{jobId}/applications → Xem đơn
4. **Chủ quán**: PATCH /api/recruitment/applications/{id} (status = APPROVED)
   - Backend tự động tạo Employee record
   - User được add vào quán, có thể login và làm việc

### 3.5. Các trạng thái quan trọng

**Order Status**:
- `OPEN`: Đang phục vụ (khách đang gọi món)
- `WAITING_PAYMENT`: Chờ thanh toán (chưa dùng nhiều)
- `COMPLETED`: Đã thanh toán xong
- `CANCELLED`: Đã hủy

**Table Status**:
- `EMPTY`: Trống
- `OCCUPIED`: Đang có khách (có order đang OPEN)

**Product Status**:
- `AVAILABLE`: Có sẵn (hiện trên menu)
- `OUT_OF_STOCK`: Hết hàng (chưa tự động xử lý)
- `HIDDEN`: Ẩn (không hiện trên menu)

**Employee Status**:
- `ACTIVE`: Đang làm việc
- `RESIGNED`: Đã nghỉ việc

**Application Status**:
- `PENDING`: Chờ xét duyệt
- `APPROVED`: Đã duyệt
- `REJECTED`: Từ chối

---

## 4. Authentication, Authorization & Multi-Tenancy

### 4.1. Authentication (Xác thực)

Backend sử dụng **Keycloak OAuth2 Resource Server**:

1. User login qua Keycloak → Nhận JWT token
2. Frontend gửi JWT trong header: `Authorization: Bearer {token}`
3. Backend verify token qua Keycloak's JWK endpoint
4. JWT chứa:
   - `sub`: User ID
   - `preferred_username`: Username
   - `realm_access.roles`: Danh sách roles (admin, user, manager, v.v.)

**Endpoint không cần Auth**:
- `/api/public/**`
- `/api/auth/**`
- `/api/recruitment/**`
- `/api/profile/**`
- GET `/api/categories/**`
- GET `/api/products/**`
- GET `/api/tenants/**`
- `/api/pos/orders/**` (để khách tự gọi món)
- `/api/pos/public/**`

### 4.2. Authorization (Phân quyền)

Backend **CHƯA phân quyền chi tiết**. Tất cả API chỉ kiểm tra:
- User đã login hay chưa (có JWT hợp lệ không)
- Request có `X-Tenant-ID` header hợp lệ không

→ **Frontend phải tự kiểm tra role** (từ JWT) để ẩn/hiện chức năng.

**Ví dụ**: API xóa nhân viên (`DELETE /api/staff/{id}`) không kiểm tra user có phải Owner hay không → Frontend phải tự ẩn nút này với Staff.

### 4.3. Multi-Tenancy (Cách ly dữ liệu giữa các quán)

**Cơ chế**:

1. **TenantFilter**: Bắt mọi request, đọc `X-Tenant-ID` header
   - Nếu thiếu header → Trả về 403 Forbidden
   - Nếu có header → Lưu vào `TenantContext` (ThreadLocal)

2. **TenantContext**: Lưu trữ tenantId trong ThreadLocal của mỗi request

3. **BaseEntity**: Tất cả entity kế thừa BaseEntity
   - Có trường `tenantId`
   - Tự động set `tenantId` khi tạo entity mới (từ TenantContext)

4. **TenantAspect** (AOP): Trước khi gọi bất kỳ repository method nào
   - Enable Hibernate Filter `tenantFilter` với `tenantId` từ TenantContext
   - Mọi query tự động thêm điều kiện `WHERE tenant_id = :tenantId`

5. **@SQLRestriction**: Entity còn có soft delete filter
   - Tự động loại bỏ entity đã bị xóa (`is_deleted = false`)

**Endpoints không cần X-Tenant-ID**:
- `/api/public/**`
- `/api/auth/**`
- `/api/recruitment/**`
- `/api/profile/**`
- `/api/tenants` (quản lý quán)

**Lưu ý**: Payment IPN callback (`/api/public/payment/vnpay-ipn`) không có tenant header, backend tự set TenantContext từ Order's tenantId.

---

## 5. Thiết kế phù hợp với Quán F&B Việt Nam

### 5.1. Các quyết định thiết kế có chủ đích

**Phân quyền đơn giản**:
- Backend xác định Owner thông qua `ownerId` trong Tenant entity
- Frontend kiểm tra `tenant.ownerId === user.id` để phân quyền UI
- Không cần hệ thống role phức tạp cho quán nhỏ

**Không có Kitchen Display System**:
- Phù hợp với đặc trưng quán ăn lề đường Việt Nam
- Bếp nhỏ, owner/staff tự quản lý, không cần hệ thống bếp riêng
- Giảm độ phức tạp vận hành

**Quản lý kho đơn giản**:
- Không cần module inventory chi tiết
- Quán nhỏ chỉ cần bật/ẩn món (`AVAILABLE` / `HIDDEN` / `OUT_OF_STOCK`)
- Phù hợp với thực tế vận hành hàng ngày

**Order API không cần Auth**:
- **Có chủ đích**: Khách quét QR, gọi món không cần tạo tài khoản
- Giảm rào cản, tăng trải nghiệm khách hàng
- Nhân viên quán xác nhận order → đảm bảo kiểm soát
- Phù hợp văn hóa quán ăn Việt Nam: nhanh, gọn, không phiền phức

### 5.2. Tính năng có sẵn nhưng chưa có API

**Quản lý ca làm (Shift)**:
- Entity `Shift` đã có với các status: `PLANNED`, `COMPLETED`, `ABSENT`
- Chưa có controller/API để quản lý
- Có thể bổ sung API khi cần

**Đặt bàn (Booking)**:
- Table có status `RESERVED` nhưng chưa có API đặt bàn

### 5.3. Tính năng KHÔNG cần thiết cho quán nhỏ

| Tính năng | Lý do không cần |
|-----------|-----------------|
| Kitchen Display | Bếp nhỏ, tự quản lý |
| Inventory chi tiết | Quá phức tạp, chỉ cần bật/ẩn món |
| Customer CRM | Quán lề đường không cần tích điểm |
| Multi-branch | Mỗi quán là 1 tenant riêng |
| Discount/Promotion | Có thể bổ sung sau nếu cần |

### 5.2. API khiến Frontend khó dùng

**Thêm món vào Order**:
- POST `/api/pos/orders/table/{tableId}/items`
- Request body: `List<AddItemRequest>`
- ❌ Thiếu: Không có API lấy danh sách món **sau khi thêm**, phải gọi lại GET `/api/pos/orders/table/{tableId}`

**Gộp bàn**:
- POST `/api/pos/tables/merge`
- ❌ Chưa rõ: Order của các bàn slave sẽ như thế nào? Có tự động merge không?

**Payment callback**:
- GET `/api/public/payment/vnpay-ipn`
- ❌ Frontend không biết khi nào thanh toán thành công (phải dùng WebSocket hoặc long polling)

**Reporting**:
- Các API báo cáo trả về raw data
- ❌ Thiếu: Không có summary (tổng doanh thu, tổng đơn, trung bình...)

### 5.3. Ràng buộc Frontend bắt buộc phải tuân theo

**X-Tenant-ID header**:
- Tất cả API nghiệp vụ (trừ public) **BẮT BUỘC** có header `X-Tenant-ID`
- Frontend phải tự quản lý tenantId (lưu trong localStorage/sessionStorage)

**Không có API lấy tenantId từ JWT**:
- Backend không có endpoint trả về "quán mà user đang làm việc"
- Frontend phải:
  1. Gọi `GET /api/tenants/me` → Lấy danh sách quán
  2. User chọn quán
  3. Lưu tenantId vào storage
  4. Gửi kèm mọi request

**Order API không cần Auth**:
- Để khách tự gọi món, API `/api/pos/orders/**` không yêu cầu JWT
- Nhưng vẫn cần `X-Tenant-ID`
- ❌ Rủi ro: Bất kỳ ai biết tenantId + tableId đều có thể thêm món vào bàn

**Soft Delete không có API khôi phục**:
- Entity xóa chỉ set `is_deleted = true`
- ❌ Không có API để khôi phục (undelete)

**Upload file phải dùng multipart/form-data**:
- Các API upload (avatar, product images, tenant logo) đều dùng `multipart/form-data`
- Không hỗ trợ base64 hoặc URL

**Pagination không có metadata**:
- Một số API trả về `Page<T>` (Spring Data), có metadata
- Một số API chỉ trả về `List<T>`, không có tổng số trang, tổng số item

**Reporting không có filter nâng cao**:
- Chỉ có `from`, `to` date
- ❌ Thiếu: Không filter theo category, product, staff, v.v.

---

## 6. Kết luận

Backend này cung cấp **nền tảng cơ bản** để quản lý quán F&B nhỏ → trung bình:
- **Hoàn thiện**: Authentication, Multi-tenancy, Menu, POS, Payment
- **Cơ bản**: HRM (tuyển dụng, quản lý nhân viên)
- **Đơn giản**: Reporting (báo cáo doanh thu, thống kê)

**Điểm mạnh**:
- Multi-tenant cách ly tốt (Filter + AOP)
- Tích hợp Keycloak OAuth2
- Hỗ trợ thanh toán online (VNPay, Momo)
- Soft delete, audit trail (createdAt, updatedAt)

**Điểm yếu**:
- Chưa phân quyền chi tiết
- Thiếu nhiều tính năng (ca làm, đặt bàn, kho, bếp, khách hàng)
- Một số API thiết kế chưa tốt (gộp bàn, thêm món)

**Khuyến nghị**:
- Frontend phải tự xử lý phân quyền (OWNER vs STAFF)
- Frontend phải tự quản lý tenantId (chọn quán, lưu storage)
- Frontend nên dùng WebSocket để nhận real-time notification (thanh toán thành công, order mới, v.v.)

---

**Tài liệu này phản ánh đúng 100% những gì backend hiện tại đang có.**
