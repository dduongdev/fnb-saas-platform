# Frontend Implementation - F&B SaaS Platform

> Tài liệu thiết kế và triển khai Frontend cho hệ thống quản lý quán F&B nhỏ → trung bình.

---

## 1. Tổng quan Thiết kế

### 1.1. Đối tượng người dùng

| Vai trò | Đặc điểm | Ưu tiên |
|---------|----------|---------|
| **Chủ quán** | Không rành công nghệ, quan tâm doanh thu | Nhìn là hiểu, báo cáo rõ ràng |
| **Nhân viên** | Thao tác nhanh trong ca làm | Ít bước, ít tùy chọn |
| **Khách hàng** | Quét QR, tự gọi món | Không cần đăng ký |

### 1.2. Nguyên tắc thiết kế

- **Flat Design**: Bố cục phẳng, ít layer
- **Trung tính**: Màu sắc dễ nhìn lâu, không rực rỡ
- **Table + Form**: Là thành phần UI chủ đạo
- **Icon đơn giản**: Lucide Icons (đơn giản, dễ hiểu)
- **Không animation rườm rà**: Chỉ loading state cơ bản

### 1.3. Color Palette

```css
:root {
  /* Primary - Xanh nhẹ cho action chính */
  --primary: #3B82F6;
  --primary-hover: #2563EB;
  
  /* Background */
  --bg-main: #F8FAFC;
  --bg-card: #FFFFFF;
  --bg-sidebar: #1E293B;
  
  /* Text */
  --text-primary: #1E293B;
  --text-secondary: #64748B;
  --text-muted: #94A3B8;
  
  /* Status */
  --success: #22C55E;
  --warning: #F59E0B;
  --danger: #EF4444;
  --info: #06B6D4;
  
  /* Border */
  --border: #E2E8F0;
  --border-hover: #CBD5E1;
}
```

### 1.4. Typography

```css
/* Font chính: Inter (Google Fonts) */
--font-family: 'Inter', -apple-system, sans-serif;

/* Size */
--text-xs: 12px;
--text-sm: 14px;
--text-base: 16px;
--text-lg: 18px;
--text-xl: 20px;
--text-2xl: 24px;

/* Weight */
--font-normal: 400;
--font-medium: 500;
--font-semibold: 600;
--font-bold: 700;
```

### 1.5. Spacing

```css
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 20px;
--space-6: 24px;
--space-8: 32px;
--space-10: 40px;
```

---

## 2. Kiến trúc Frontend

### 2.1. Tech Stack

| Thành phần | Công nghệ |
|------------|-----------|
| Framework | **React 18** + Vite |
| Routing | React Router v6 |
| State Management | React Context + useReducer (đủ đơn giản) |
| API Calls | Fetch API / Axios |
| Styling | **CSS Modules** hoặc vanilla CSS |
| Icons | Lucide React |
| Date/Time | date-fns |
| Auth | Keycloak JS |

### 2.2. Cấu trúc thư mục

```
frontend/
├── public/
├── src/
│   ├── api/               # API client
│   │   ├── client.js      # Base axios/fetch config
│   │   ├── auth.js
│   │   ├── tenant.js
│   │   ├── menu.js
│   │   ├── pos.js
│   │   ├── hrm.js
│   │   ├── payment.js
│   │   └── reports.js
│   ├── components/        # UI Components
│   │   ├── common/
│   │   │   ├── Button.jsx
│   │   │   ├── Card.jsx
│   │   │   ├── Table.jsx
│   │   │   ├── Modal.jsx
│   │   │   ├── Input.jsx
│   │   │   ├── Select.jsx
│   │   │   ├── Loading.jsx
│   │   │   ├── Empty.jsx
│   │   │   └── StatusBadge.jsx
│   │   ├── layout/
│   │   │   ├── Sidebar.jsx
│   │   │   ├── Header.jsx
│   │   │   └── PageLayout.jsx
│   │   └── domain/        # Domain-specific components
│   │       ├── TableCard.jsx
│   │       ├── OrderItem.jsx
│   │       ├── ProductCard.jsx
│   │       └── ...
│   ├── pages/             # Page components
│   │   ├── auth/
│   │   │   ├── LoginPage.jsx
│   │   │   └── SelectTenantPage.jsx
│   │   ├── dashboard/
│   │   │   └── DashboardPage.jsx
│   │   ├── pos/
│   │   │   ├── POSPage.jsx          # Màn hình bán hàng chính
│   │   │   └── TableGridPage.jsx
│   │   ├── menu/
│   │   │   ├── CategoryListPage.jsx
│   │   │   └── ProductListPage.jsx
│   │   ├── hrm/
│   │   │   ├── StaffListPage.jsx
│   │   │   ├── JobListPage.jsx
│   │   │   └── ApplicationListPage.jsx
│   │   ├── reports/
│   │   │   └── ReportsPage.jsx
│   │   ├── settings/
│   │   │   ├── TenantSettingsPage.jsx
│   │   │   └── PaymentSettingsPage.jsx
│   │   └── customer/      # Màn hình cho khách (quét QR)
│   │       └── CustomerMenuPage.jsx
│   ├── context/
│   │   ├── AuthContext.jsx
│   │   └── TenantContext.jsx
│   ├── hooks/
│   │   ├── useAuth.js
│   │   ├── useTenant.js
│   │   └── useApi.js
│   ├── utils/
│   │   ├── format.js      # Format tiền, ngày
│   │   └── constants.js
│   ├── styles/
│   │   ├── global.css
│   │   └── variables.css
│   ├── App.jsx
│   └── main.jsx
├── index.html
├── vite.config.js
└── package.json
```

---

## 3. Mapping Backend → UI

### 3.1. Module Global (Auth, Tenant)

| API | Màn hình | Hành động UI |
|-----|----------|--------------|
| `POST /api/auth/sync` | - | Tự động gọi sau login Keycloak |
| `GET /api/tenants/me` | **SelectTenantPage** | Hiển thị danh sách quán, chọn quán |
| `POST /api/tenants` | Modal trong SelectTenantPage | Form tạo quán mới |
| `PUT /api/tenants/{id}` | **TenantSettingsPage** | Form cập nhật thông tin quán |
| `PUT /api/tenants/{id}/payment-config` | **PaymentSettingsPage** | Form cấu hình VNPay/Momo |
| `POST /api/profile/avatar` | **Header** (dropdown) | Upload avatar |

### 3.2. Module Menu

| API | Màn hình | Hành động UI |
|-----|----------|--------------|
| `GET /api/categories` | **CategoryListPage** | Table danh sách danh mục |
| `POST /api/categories` | Modal | Form tạo danh mục |
| `DELETE /api/categories/{id}` | Table action | Nút xóa (confirm) |
| `GET /api/products/{id}` | Modal | Chi tiết sản phẩm |
| `POST /api/products` | **ProductListPage** modal | Form tạo sản phẩm + upload ảnh |
| `PUT /api/products/{id}` | Modal | Form sửa sản phẩm |
| `DELETE /api/products/{id}` | Table action | Nút xóa (soft delete) |
| `POST /api/products/{id}/images` | Modal | Upload thêm ảnh |
| `DELETE /api/products/images/{imageId}` | Modal | Xóa ảnh |

### 3.3. Module POS (Bán hàng)

| API | Màn hình | Hành động UI |
|-----|----------|--------------|
| `GET /api/pos/tables` | **TableGridPage** | Grid các bàn (card view) |
| `POST /api/pos/tables` | Modal | Form thêm bàn |
| `POST /api/pos/tables/merge` | Modal | Chọn nhiều bàn để gộp |
| `POST /api/pos/tables/{id}/split` | Table action | Nút tách bàn |
| `GET /api/pos/orders/table/{tableId}` | **POSPage** | Hiển thị order hiện tại |
| `POST /api/pos/orders/table/{tableId}/items` | POSPage | Click món → thêm vào order |
| `DELETE /api/pos/orders/items/{itemId}` | POSPage | Swipe/click xóa món |
| `POST /api/pos/orders/{orderId}/cancel` | POSPage | Nút hủy order |
| `POST /api/pos/orders/{orderId}/pay/cash` | POSPage | Nút thanh toán tiền mặt |

### 3.4. Module HRM (Nhân sự)

| API | Màn hình | Hành động UI | Chỉ Owner |
|-----|----------|--------------|-----------|
| `GET /api/staff` | **StaffListPage** | Table danh sách nhân viên | ✅ |
| `DELETE /api/staff/{id}` | Table action | Nút xóa nhân viên | ✅ |
| `GET /api/hrm/jobs` | **JobListPage** | Table tin tuyển dụng | ✅ |
| `POST /api/hrm/jobs` | Modal | Form tạo tin | ✅ |
| `PUT /api/hrm/jobs/{id}` | Modal | Form sửa tin | ✅ |
| `DELETE /api/hrm/jobs/{id}` | Table action | Nút xóa tin | ✅ |
| `GET /api/recruitment/jobs/{jobId}/applications` | **ApplicationListPage** | Table đơn ứng tuyển | ✅ |
| `PATCH /api/recruitment/applications/{id}` | Table action | Nút Duyệt / Từ chối | ✅ |

### 3.5. Module Reporting

| API | Màn hình | Hành động UI | Chỉ Owner |
|-----|----------|--------------|-----------|
| `GET /api/reports/revenue` | **ReportsPage** (tab Revenue) | Biểu đồ doanh thu | ✅ |
| `GET /api/reports/top-products` | ReportsPage (tab Products) | Biểu đồ top món | ✅ |
| `GET /api/reports/peak-hours` | ReportsPage (tab Hours) | Biểu đồ khung giờ | ✅ |

### 3.6. Module Payment

| API | Màn hình | Hành động UI |
|-----|----------|--------------|
| `GET /api/public/payment/methods/{tenantId}` | POSPage / CustomerMenuPage | Hiển thị lựa chọn thanh toán |
| `POST /api/public/payment/create-url` | Checkout flow | Redirect đến VNPay/Momo |

### 3.7. Customer Flow (Khách quét QR)

| API | Màn hình | Hành động UI |
|-----|----------|--------------|
| `GET /api/pos/public/info/{tableId}` | **CustomerMenuPage** | Hiển thị tên quán, bàn |
| `GET /api/pos/public/menu` | CustomerMenuPage | Hiển thị menu |
| `GET /api/pos/orders/table/{tableId}` | CustomerMenuPage | Hiển thị giỏ hàng |
| `POST /api/pos/orders/table/{tableId}/items` | CustomerMenuPage | Thêm món vào giỏ |
| `POST /api/public/payment/create-url` | CustomerMenuPage | Thanh toán online |

---

## 4. Danh sách Màn hình

### 4.1. Luồng Auth & Tenant Selection

```
1. LoginPage
   └── Redirect to Keycloak
       └── Callback (auto sync user)
           └── SelectTenantPage
               ├── [Chọn quán] → Dashboard
               └── [Tạo quán mới] → Modal → Dashboard
```

### 4.2. Dashboard Layout (Sau khi chọn quán)

```
┌─────────────────────────────────────────────────────────────┐
│  Header: Logo | Tên quán | Avatar dropdown                  │
├──────────┬──────────────────────────────────────────────────┤
│ Sidebar  │  Main Content Area                               │
│          │                                                  │
│ • Bán hàng │                                                │
│ • Thực đơn │                                                │
│ • Sơ đồ bàn│                                                │
│ --------- │                                                │
│ • Nhân sự │  (Owner only)                                  │
│ • Báo cáo │  (Owner only)                                  │
│ --------- │                                                │
│ • Cài đặt │  (Owner only)                                  │
│          │                                                  │
└──────────┴──────────────────────────────────────────────────┘
```

### 4.3. Chi tiết từng màn hình

#### 4.3.1. Dashboard (Trang chủ)

**Mục đích**: Tổng quan nhanh cho người dùng

**Nội dung**:
- Số đơn hôm nay (từ reporting API)
- Doanh thu hôm nay
- Số bàn đang có khách
- Shortcut đến POS (Bán hàng)

**API sử dụng**:
- `GET /api/reports/revenue?from=today&to=today`
- `GET /api/pos/tables`

---

#### 4.3.2. POSPage (Bán hàng)

**Mục đích**: Màn hình chính để nhân viên bán hàng

**Layout**: 2 cột
```
┌────────────────────────┬─────────────────────┐
│      MENU (trái)       │   ORDER (phải)      │
│                        │                     │
│ [Tab các Category]     │ Bàn: [Dropdown]     │
│                        │                     │
│ ┌─────┐ ┌─────┐ ┌─────┐│ ┌─────────────────┐ │
│ │ Món │ │ Món │ │ Món ││ │ Cà phê đen x2   │ │
│ │ 25k │ │ 30k │ │ 40k ││ │           50.000│ │
│ └─────┘ └─────┘ └─────┘│ ├─────────────────┤ │
│                        │ │ Bánh mì x1      │ │
│ Click món → thêm vào   │ │           25.000│ │
│ order bên phải         │ └─────────────────┘ │
│                        │                     │
│                        │ Tổng: 75.000 VND    │
│                        │                     │
│                        │ [Thanh toán]        │
│                        │ [Hủy order]         │
└────────────────────────┴─────────────────────┘
```

**API sử dụng**:
- `GET /api/categories`
- `GET /api/pos/public/menu` (lấy menu nhóm theo category)
- `GET /api/pos/orders/table/{tableId}`
- `POST /api/pos/orders/table/{tableId}/items`
- `DELETE /api/pos/orders/items/{itemId}`
- `POST /api/pos/orders/{orderId}/pay/cash`
- `POST /api/pos/orders/{orderId}/cancel`

---

#### 4.3.3. TableGridPage (Sơ đồ bàn)

**Mục đích**: Xem nhanh tình trạng các bàn

**Layout**: Grid cards
```
┌───────────────────────────────────────────────┐
│  SƠ ĐỒ BÀN                    [+ Thêm bàn]    │
├───────────────────────────────────────────────┤
│                                               │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐       │
│  │ Bàn 01  │  │ Bàn 02  │  │ Bàn 03  │       │
│  │ [TRỐNG] │  │ [CÓ KHÁCH]│ │ [TRỐNG] │       │
│  │         │  │ 125.000  │  │         │       │
│  └─────────┘  └─────────┘  └─────────┘       │
│                                               │
│  Click bàn → Mở POSPage với bàn đó            │
│                                               │
└───────────────────────────────────────────────┘
```

**Màu sắc bàn**:
- `EMPTY` → Xanh lá nhạt
- `OCCUPIED` → Cam (có số tiền)

**API sử dụng**:
- `GET /api/pos/tables`
- `POST /api/pos/tables` (thêm bàn mới)

---

#### 4.3.4. ProductListPage (Quản lý thực đơn)

**Mục đích**: CRUD sản phẩm

**Layout**: Table với filter
```
┌───────────────────────────────────────────────┐
│  THỰC ĐƠN              [Danh mục v] [+ Thêm]  │
├───────────────────────────────────────────────┤
│  Ảnh | Tên món | Danh mục | Giá | Trạng thái  │
│ ──────────────────────────────────────────────│
│  [img] Cà phê đen | Đồ uống | 25k | Có sẵn    │
│  [img] Bánh mì    | Ăn vặt  | 30k | Ẩn        │
│                                               │
└───────────────────────────────────────────────┘
```

**Trạng thái sản phẩm**:
- `AVAILABLE` → Badge xanh
- `OUT_OF_STOCK` → Badge vàng
- `HIDDEN` → Badge xám

**API sử dụng**:
- `GET /api/categories`
- `POST /api/products` (với multipart/form-data)
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`

---

#### 4.3.5. CategoryListPage (Quản lý danh mục)

**Mục đích**: CRUD danh mục

**Layout**: Table đơn giản
```
┌───────────────────────────────────────────────┐
│  DANH MỤC                           [+ Thêm]  │
├───────────────────────────────────────────────┤
│  Tên | Thứ tự hiển thị | Hành động            │
│ ──────────────────────────────────────────────│
│  Đồ uống       | 1      | [Xóa]               │
│  Món chính     | 2      | [Xóa]               │
│  Ăn vặt        | 3      | [Xóa]               │
│                                               │
└───────────────────────────────────────────────┘
```

**API sử dụng**:
- `GET /api/categories`
- `POST /api/categories`
- `DELETE /api/categories/{id}`

---

#### 4.3.6. StaffListPage (Quản lý nhân viên) - Chỉ Owner

**Mục đích**: Xem danh sách nhân viên, xóa nhân viên

**Layout**: Table
```
┌───────────────────────────────────────────────┐
│  NHÂN VIÊN                                    │
├───────────────────────────────────────────────┤
│  Họ tên | Username | Vai trò | Trạng thái     │
│ ──────────────────────────────────────────────│
│  Nguyễn Văn A | nva | STAFF | Đang làm        │
│  Trần Thị B   | ttb | MANAGER | Đang làm      │
│                                               │
└───────────────────────────────────────────────┘
```

**API sử dụng**:
- `GET /api/staff`
- `DELETE /api/staff/{id}`

---

#### 4.3.7. JobListPage (Tin tuyển dụng) - Chỉ Owner

**Mục đích**: CRUD tin tuyển dụng

**API sử dụng**:
- `GET /api/hrm/jobs`
- `POST /api/hrm/jobs`
- `PUT /api/hrm/jobs/{id}`
- `DELETE /api/hrm/jobs/{id}`

---

#### 4.3.8. ApplicationListPage (Đơn ứng tuyển) - Chỉ Owner

**Mục đích**: Duyệt/từ chối đơn ứng tuyển

**Layout**: Table với action buttons
```
┌───────────────────────────────────────────────┐
│  ĐƠN ỨNG TUYỂN - [Tên tin tuyển dụng]         │
├───────────────────────────────────────────────┤
│  Họ tên | Email | Ngày ứng tuyển | Hành động  │
│ ──────────────────────────────────────────────│
│  Lê Văn C | lvc@gmail | 01/01/2024 | [Duyệt] [Từ chối] │
│                                               │
└───────────────────────────────────────────────┘
```

**API sử dụng**:
- `GET /api/recruitment/jobs/{jobId}/applications`
- `PATCH /api/recruitment/applications/{id}`

---

#### 4.3.9. ReportsPage (Báo cáo) - Chỉ Owner

**Mục đích**: Xem báo cáo doanh thu, thống kê

**Layout**: Tabs với date picker
```
┌───────────────────────────────────────────────┐
│  BÁO CÁO          [Từ ngày] → [Đến ngày]      │
├───────────────────────────────────────────────┤
│  [Doanh thu] | [Top món] | [Khung giờ]        │
├───────────────────────────────────────────────┤
│                                               │
│  (Biểu đồ / Bảng số liệu)                     │
│                                               │
└───────────────────────────────────────────────┘
```

**API sử dụng**:
- `GET /api/reports/revenue?from=...&to=...`
- `GET /api/reports/top-products?from=...&to=...&limit=10`
- `GET /api/reports/peak-hours?from=...&to=...`

---

#### 4.3.10. TenantSettingsPage (Cài đặt quán) - Chỉ Owner

**Mục đích**: Cập nhật thông tin quán

**Layout**: Form
```
┌───────────────────────────────────────────────┐
│  CÀI ĐẶT QUÁN                                 │
├───────────────────────────────────────────────┤
│                                               │
│  Logo: [Upload]                               │
│  Tên quán: [_______________]                  │
│  Địa chỉ:  [_______________]                  │
│  Trạng thái: [Đang hoạt động v]               │
│                                               │
│                         [Lưu thay đổi]        │
│                                               │
└───────────────────────────────────────────────┘
```

**API sử dụng**:
- `GET /api/tenants/{id}`
- `PUT /api/tenants/{id}`
- `PATCH /api/tenants/{id}/status`

---

#### 4.3.11. PaymentSettingsPage (Cài đặt thanh toán) - Chỉ Owner

**Mục đích**: Cấu hình VNPay, Momo

**Layout**: Form
```
┌───────────────────────────────────────────────┐
│  CÀI ĐẶT THANH TOÁN                           │
├───────────────────────────────────────────────┤
│                                               │
│  ☑ VNPay                                      │
│    TMN Code: [_______________]                │
│    Hash Secret: [_______________]             │
│                                               │
│  ☐ Momo (Coming soon)                         │
│                                               │
│                         [Lưu thay đổi]        │
│                                               │
└───────────────────────────────────────────────┘
```

**API sử dụng**:
- `PUT /api/tenants/{id}/payment-config`

---

#### 4.3.12. CustomerMenuPage (Khách quét QR)

**Mục đích**: Khách xem menu, gọi món, thanh toán

**URL**: `/customer/{tableId}` (từ QR code)

**Layout**: Mobile-first
```
┌─────────────────────────────┐
│  [Logo quán]                │
│  Quán ABC - Bàn 01          │
├─────────────────────────────┤
│  [Tabs: Danh mục]           │
│                             │
│  ┌───────────────────────┐  │
│  │ [img] Cà phê đen      │  │
│  │       25.000 VND [+]  │  │
│  └───────────────────────┘  │
│                             │
│  ┌───────────────────────┐  │
│  │ [img] Bánh mì         │  │
│  │       30.000 VND [+]  │  │
│  └───────────────────────┘  │
│                             │
├─────────────────────────────┤
│  Giỏ hàng: 2 món - 55.000   │
│       [Xem giỏ hàng]        │
└─────────────────────────────┘
```

**API sử dụng**:
- `GET /api/pos/public/info/{tableId}` (lấy thông tin quán + bàn)
- `GET /api/pos/public/menu`
- `GET /api/pos/orders/table/{tableId}`
- `POST /api/pos/orders/table/{tableId}/items`
- `POST /api/public/payment/create-url`

---

## 5. Phân quyền hiển thị theo Role

### 5.1. Cách xác định Owner vs Staff

**Logic**:
```javascript
// Sau khi login, lấy user info từ JWT
const userId = jwt.sub; // User ID từ token

// Sau khi chọn tenant
const tenant = selectedTenant;

// Kiểm tra Owner
const isOwner = tenant.ownerId === userId;
```

### 5.2. Menu hiển thị theo role

| Menu Item | Staff | Owner |
|-----------|-------|-------|
| Bán hàng | ✅ | ✅ |
| Sơ đồ bàn | ✅ | ✅ |
| Thực đơn | ✅ | ✅ |
| Danh mục | ✅ | ✅ |
| Nhân viên | ❌ | ✅ |
| Tuyển dụng | ❌ | ✅ |
| Báo cáo | ❌ | ✅ |
| Cài đặt quán | ❌ | ✅ |
| Cài đặt thanh toán | ❌ | ✅ |

### 5.3. Protected Routes

```jsx
// Component bảo vệ route chỉ cho Owner
function OwnerRoute({ children }) {
  const { user } = useAuth();
  const { tenant } = useTenant();
  
  if (!tenant || tenant.ownerId !== user.id) {
    return <Navigate to="/pos" />;
  }
  
  return children;
}
```

---

## 6. Xử lý States

### 6.1. Loading State

```jsx
// Spinner đơn giản
function Loading() {
  return (
    <div className="loading-container">
      <div className="spinner" />
      <span>Đang tải...</span>
    </div>
  );
}
```

### 6.2. Empty State

```jsx
function Empty({ message = "Không có dữ liệu" }) {
  return (
    <div className="empty-container">
      <IconInbox size={48} color="var(--text-muted)" />
      <p>{message}</p>
    </div>
  );
}
```

### 6.3. Error State

```jsx
function ErrorMessage({ message, onRetry }) {
  return (
    <div className="error-container">
      <IconAlertCircle size={48} color="var(--danger)" />
      <p>{message}</p>
      {onRetry && <Button onClick={onRetry}>Thử lại</Button>}
    </div>
  );
}
```

---

## 7. API Client

### 7.1. Base Configuration

```javascript
// src/api/client.js
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export async function apiRequest(endpoint, options = {}) {
  const token = localStorage.getItem('access_token');
  const tenantId = localStorage.getItem('tenant_id');
  
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }
  
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });
  
  const data = await response.json();
  
  if (data.code !== 200) {
    throw new Error(data.message || 'Đã có lỗi xảy ra');
  }
  
  return data.data;
}

// Shorthand methods
export const api = {
  get: (url) => apiRequest(url, { method: 'GET' }),
  post: (url, body) => apiRequest(url, { method: 'POST', body: JSON.stringify(body) }),
  put: (url, body) => apiRequest(url, { method: 'PUT', body: JSON.stringify(body) }),
  patch: (url, body) => apiRequest(url, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (url) => apiRequest(url, { method: 'DELETE' }),
};
```

### 7.2. Multipart Upload

```javascript
// src/api/upload.js
export async function uploadFile(endpoint, formData) {
  const token = localStorage.getItem('access_token');
  const tenantId = localStorage.getItem('tenant_id');
  
  const headers = {};
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }
  
  // Không set Content-Type, browser tự thêm boundary
  const response = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers,
    body: formData,
  });
  
  const data = await response.json();
  
  if (data.code !== 200) {
    throw new Error(data.message || 'Upload thất bại');
  }
  
  return data.data;
}
```

---

## 8. Ghi chú cho việc mở rộng

### 8.1. Tính năng có thể thêm sau

| Tính năng | Backend cần | UI cần |
|-----------|-------------|--------|
| Quản lý ca làm | API cho Shift entity | ShiftListPage, ShiftCalendarPage |
| Đặt bàn trước | API Booking | BookingListPage, Calendar view |
| Thông báo realtime | WebSocket endpoint | Toast notification |
| Tìm kiếm sản phẩm | API search | Search bar trong POSPage |
| In hóa đơn | Không cần | Print view component |

### 8.2. Component có thể tái sử dụng

- **DataTable**: Table với sort, filter, pagination
- **FormField**: Input wrapper với label, error message
- **ConfirmModal**: Modal xác nhận xóa
- **DateRangePicker**: Chọn khoảng ngày cho báo cáo
- **ImageUpload**: Drag & drop upload ảnh

### 8.3. Lưu ý Performance

- Lazy load các route (React.lazy)
- Pagination cho danh sách dài
- Debounce cho search input
- Cache API response với SWR hoặc React Query (nếu cần)

---

## 9. Checklist Triển khai

### Phase 1: Foundation
- [ ] Setup Vite + React
- [ ] Cấu hình routing
- [ ] Thiết lập AuthContext + Keycloak
- [ ] Thiết lập TenantContext
- [ ] Base components (Button, Card, Table, Modal, Input)
- [ ] Layout components (Sidebar, Header, PageLayout)
- [ ] API client

### Phase 2: Core Features
- [ ] LoginPage + SelectTenantPage
- [ ] DashboardPage
- [ ] POSPage (bán hàng)
- [ ] TableGridPage (sơ đồ bàn)

### Phase 3: Menu Management
- [ ] CategoryListPage
- [ ] ProductListPage

### Phase 4: HRM (Owner only)
- [ ] StaffListPage
- [ ] JobListPage
- [ ] ApplicationListPage

### Phase 5: Reports & Settings (Owner only)
- [ ] ReportsPage
- [ ] TenantSettingsPage
- [ ] PaymentSettingsPage

### Phase 6: Customer Flow
- [ ] CustomerMenuPage (QR scan)

---

**Tài liệu này là blueprint cho việc triển khai frontend. Chỉ implement những gì backend hỗ trợ.**
