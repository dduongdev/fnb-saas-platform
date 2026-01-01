# PLATFORM UI SPECIFICATION

## I. TỔNG QUAN

### Vai trò: PLATFORM (Super Admin)
- Người vận hành toàn hệ thống multi-tenant
- Quản lý tất cả tenant
- Xem thống kê tổng hệ thống
- KHÔNG tham gia nghiệp vụ gọi món

### Phạm vi:
- Dashboard tổng quan
- Quản lý tenant (kích hoạt/vô hiệu hóa)
- Thống kê hệ thống
- (Tương lai) Quản lý người dùng platform

---

## II. BACKEND ENDPOINTS CẦN BỔ SUNG

### 1. PlatformController.java

```java
package com.project.fnb.modules.platform.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.platform.dto.PlatformStatsDto;
import com.project.fnb.modules.platform.dto.TenantAdminDto;
import com.project.fnb.modules.platform.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM')") // Chỉ PLATFORM admin
public class PlatformController {

    private final PlatformService platformService;

    /**
     * Lấy thống kê tổng hệ thống
     */
    @GetMapping("/stats")
    public ApiResponse<PlatformStatsDto> getStats() {
        return ApiResponse.success(platformService.getStats());
    }

    /**
     * Lấy danh sách tất cả tenant với thống kê
     */
    @GetMapping("/tenants")
    public ApiResponse<Page<TenantAdminDto>> getAllTenants(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) String search
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(
            platformService.getAllTenantsAdmin(isActive, search, pageable)
        );
    }

    /**
     * Lấy chi tiết tenant với thống kê
     */
    @GetMapping("/tenants/{id}")
    public ApiResponse<TenantAdminDto> getTenantDetail(@PathVariable String id) {
        return ApiResponse.success(platformService.getTenantDetail(id));
    }

    /**
     * Kích hoạt/vô hiệu hóa tenant
     * (Endpoint này đã có ở TenantController, nhưng cần thêm PLATFORM role check)
     */
    @PatchMapping("/tenants/{id}/status")
    public ApiResponse<String> updateTenantStatus(
        @PathVariable String id,
        @RequestParam Boolean isActive
    ) {
        platformService.updateTenantStatus(id, isActive);
        return ApiResponse.success(isActive ? "Đã kích hoạt tenant" : "Đã vô hiệu hóa tenant");
    }
}
```

### 2. DTOs

**PlatformStatsDto.java:**
```java
@Data
@Builder
public class PlatformStatsDto {
    private Long totalTenants;           // Tổng số tenant
    private Long activeTenants;          // Tenant đang hoạt động
    private Long inactiveTenants;        // Tenant bị vô hiệu hóa
    private Long totalUsers;             // Tổng số người dùng
    private BigDecimal totalRevenue;     // Tổng doanh thu (tất cả tenant)
    private BigDecimal monthlyRevenue;   // Doanh thu tháng này
    
    // Top tenants
    private List<TopTenantDto> topTenantsByRevenue;
    
    // Growth metrics
    private Integer newTenantsThisMonth;
    private Integer newTenantsLastMonth;
    private Double tenantGrowthRate;     // % tăng trưởng
}
```

**TenantAdminDto.java:**
```java
@Data
@Builder
public class TenantAdminDto {
    private String id;
    private String name;
    private String address;
    private String logoUrl;
    private Boolean isActive;
    private LocalDateTime createdAt;
    
    // Owner info
    private String ownerId;
    private String ownerName;
    private String ownerEmail;
    
    // Stats
    private Long totalOrders;
    private Long totalProducts;
    private Long totalEmployees;
    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    
    // Last activity
    private LocalDateTime lastOrderAt;
}
```

**TopTenantDto.java:**
```java
@Data
@Builder
public class TopTenantDto {
    private String id;
    private String name;
    private BigDecimal revenue;
    private Long orderCount;
}
```

### 3. PlatformService.java

```java
@Service
@RequiredArgsConstructor
public class PlatformService {
    
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    // ... other repositories
    
    public PlatformStatsDto getStats() {
        Long totalTenants = tenantRepository.count();
        Long activeTenants = tenantRepository.countByIsActive(true);
        Long inactiveTenants = totalTenants - activeTenants;
        Long totalUsers = userRepository.count();
        
        // Calculate revenue (from all tenants)
        BigDecimal totalRevenue = orderRepository.sumAllRevenue();
        
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        BigDecimal monthlyRevenue = orderRepository.sumRevenueByDateRange(
            monthStart.atStartOfDay(),
            now.atTime(23, 59, 59)
        );
        
        // Top tenants
        List<TopTenantDto> topTenants = orderRepository
            .findTopTenantsByRevenue(PageRequest.of(0, 5))
            .stream()
            .map(result -> TopTenantDto.builder()
                .id((String) result[0])
                .name((String) result[1])
                .revenue((BigDecimal) result[2])
                .orderCount((Long) result[3])
                .build())
            .toList();
        
        // Growth rate
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        Integer newTenantsThisMonth = tenantRepository.countByCreatedAtBetween(
            monthStart.atStartOfDay(),
            now.atTime(23, 59, 59)
        );
        Integer newTenantsLastMonth = tenantRepository.countByCreatedAtBetween(
            lastMonthStart.atStartOfDay(),
            monthStart.minusDays(1).atTime(23, 59, 59)
        );
        
        Double growthRate = newTenantsLastMonth > 0 
            ? ((newTenantsThisMonth - newTenantsLastMonth) * 100.0 / newTenantsLastMonth)
            : 0.0;
        
        return PlatformStatsDto.builder()
            .totalTenants(totalTenants)
            .activeTenants(activeTenants)
            .inactiveTenants(inactiveTenants)
            .totalUsers(totalUsers)
            .totalRevenue(totalRevenue)
            .monthlyRevenue(monthlyRevenue)
            .topTenantsByRevenue(topTenants)
            .newTenantsThisMonth(newTenantsThisMonth)
            .newTenantsLastMonth(newTenantsLastMonth)
            .tenantGrowthRate(growthRate)
            .build();
    }
    
    public Page<TenantAdminDto> getAllTenantsAdmin(
        Boolean isActive,
        String search,
        Pageable pageable
    ) {
        // Filter logic
        Page<Tenant> tenants;
        
        if (isActive != null && search != null) {
            tenants = tenantRepository.findByIsActiveAndNameContaining(
                isActive, search, pageable
            );
        } else if (isActive != null) {
            tenants = tenantRepository.findByIsActive(isActive, pageable);
        } else if (search != null) {
            tenants = tenantRepository.findByNameContaining(search, pageable);
        } else {
            tenants = tenantRepository.findAll(pageable);
        }
        
        return tenants.map(this::mapToAdminDto);
    }
    
    private TenantAdminDto mapToAdminDto(Tenant tenant) {
        User owner = userRepository.findById(tenant.getOwnerId()).orElse(null);
        
        Long totalOrders = orderRepository.countByTenantId(tenant.getId());
        Long totalProducts = productRepository.countByTenantId(tenant.getId());
        Long totalEmployees = employeeRepository.countByTenantId(tenant.getId());
        
        BigDecimal totalRevenue = orderRepository.sumRevenueByTenantId(tenant.getId());
        
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        BigDecimal monthlyRevenue = orderRepository.sumRevenueByTenantIdAndDateRange(
            tenant.getId(),
            monthStart.atStartOfDay(),
            LocalDate.now().atTime(23, 59, 59)
        );
        
        LocalDateTime lastOrderAt = orderRepository.findLastOrderDateByTenantId(tenant.getId());
        
        return TenantAdminDto.builder()
            .id(tenant.getId())
            .name(tenant.getName())
            .address(tenant.getAddress())
            .logoUrl(tenant.getLogoUrl())
            .isActive(tenant.getIsActive())
            .createdAt(tenant.getCreatedAt())
            .ownerId(tenant.getOwnerId())
            .ownerName(owner != null ? owner.getName() : "N/A")
            .ownerEmail(owner != null ? owner.getEmail() : "N/A")
            .totalOrders(totalOrders)
            .totalProducts(totalProducts)
            .totalEmployees(totalEmployees)
            .totalRevenue(totalRevenue)
            .monthlyRevenue(monthlyRevenue)
            .lastOrderAt(lastOrderAt)
            .build();
    }
    
    public TenantAdminDto getTenantDetail(String id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Tenant not found"));
        return mapToAdminDto(tenant);
    }
    
    public void updateTenantStatus(String id, Boolean isActive) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Tenant not found"));
        tenant.setIsActive(isActive);
        tenantRepository.save(tenant);
    }
}
```

---

## III. FRONTEND UI PAGES

### 1. PlatformDashboardPage.jsx

**Route:** `/platform/dashboard`

**Layout:**
```
+-------------------------------------------+
|  Platform Admin Dashboard                 |
+-------------------------------------------+
|  [Total Tenants] [Active] [Inactive]     |
|  [Total Users] [Monthly Revenue]         |
+-------------------------------------------+
|  📈 Tenant Growth Chart                   |
+-------------------------------------------+
|  🏆 Top Tenants by Revenue                |
+-------------------------------------------+
```

**Code:**
```jsx
import { useState, useEffect } from 'react';
import { BarChart3, TrendingUp, Users, Building2, DollarSign } from 'lucide-react';
import { Loading, Card } from '../../components/common';
import { getPlatformStats } from '../../api/platform';
import { formatPrice } from '../../utils/format';
import './PlatformDashboardPage.css';

export function PlatformDashboardPage() {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadStats();
    }, []);

    const loadStats = async () => {
        try {
            setLoading(true);
            const data = await getPlatformStats();
            setStats(data);
        } catch (error) {
            console.error('Failed to load stats:', error);
        } finally {
            setLoading(false);
        }
    };

    if (loading) return <Loading fullPage />;

    return (
        <div className="platform-dashboard">
            <header className="dashboard-header">
                <h1>Platform Dashboard</h1>
                <p>Tổng quan hệ thống F&B Multi-Tenant</p>
            </header>

            {/* Stats Cards */}
            <div className="stats-grid">
                <StatCard
                    icon={<Building2 />}
                    title="Tổng Tenant"
                    value={stats.totalTenants}
                    subtitle={`${stats.activeTenants} đang hoạt động`}
                    trend={stats.tenantGrowthRate}
                />
                <StatCard
                    icon={<Users />}
                    title="Người dùng"
                    value={stats.totalUsers}
                />
                <StatCard
                    icon={<DollarSign />}
                    title="Doanh thu tháng"
                    value={formatPrice(stats.monthlyRevenue)}
                    subtitle={`Tổng: ${formatPrice(stats.totalRevenue)}`}
                />
                <StatCard
                    icon={<TrendingUp />}
                    title="Tenant mới tháng này"
                    value={stats.newTenantsThisMonth}
                    subtitle={`Tháng trước: ${stats.newTenantsLastMonth}`}
                    trend={stats.tenantGrowthRate}
                />
            </div>

            {/* Top Tenants */}
            <Card className="top-tenants">
                <h3>🏆 Top Tenant theo Doanh Thu</h3>
                <table>
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Tên Quán</th>
                            <th>Doanh thu</th>
                            <th>Số đơn</th>
                        </tr>
                    </thead>
                    <tbody>
                        {stats.topTenantsByRevenue?.map((tenant, idx) => (
                            <tr key={tenant.id}>
                                <td>{idx + 1}</td>
                                <td>{tenant.name}</td>
                                <td>{formatPrice(tenant.revenue)}</td>
                                <td>{tenant.orderCount}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </Card>
        </div>
    );
}

function StatCard({ icon, title, value, subtitle, trend }) {
    return (
        <Card className="stat-card">
            <div className="stat-icon">{icon}</div>
            <div className="stat-content">
                <p className="stat-title">{title}</p>
                <h2 className="stat-value">{value}</h2>
                {subtitle && <p className="stat-subtitle">{subtitle}</p>}
                {trend !== undefined && trend !== 0 && (
                    <div className={`trend ${trend > 0 ? 'up' : 'down'}`}>
                        <TrendingUp size={14} />
                        <span>{trend > 0 ? '+' : ''}{trend.toFixed(1)}%</span>
                    </div>
                )}
            </div>
        </Card>
    );
}
```

---

### 2. PlatformTenantListPage.jsx

**Route:** `/platform/tenants`

**Layout:**
```
+-------------------------------------------+
|  Quản lý Tenant                           |
|  [Search] [Filter: All/Active/Inactive]   |
+-------------------------------------------+
|  Table:                                   |
|  | Tên | Owner | Status | Orders | Rev | |
|  | ... | ...   | ...    | ...    | ... | |
+-------------------------------------------+
|  [Pagination]                             |
+-------------------------------------------+
```

**Code:**
```jsx
import { useState, useEffect } from 'react';
import { Search, Filter, ToggleLeft, ToggleRight } from 'lucide-react';
import { Loading, Card, Input, Button, StatusBadge } from '../../components/common';
import { getAllTenants, updateTenantStatus } from '../../api/platform';
import { formatPrice, formatDate } from '../../utils/format';
import './PlatformTenantListPage.css';

export function PlatformTenantListPage() {
    const [tenants, setTenants] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [search, setSearch] = useState('');
    const [filter, setFilter] = useState(null); // null | true | false

    useEffect(() => {
        loadTenants();
    }, [page, filter]);

    const loadTenants = async () => {
        try {
            setLoading(true);
            const params = { page, size: 20 };
            if (filter !== null) params.isActive = filter;
            if (search) params.search = search;

            const data = await getAllTenants(params);
            setTenants(data.content);
            setTotalPages(data.totalPages);
        } catch (error) {
            console.error('Failed to load tenants:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = () => {
        setPage(0);
        loadTenants();
    };

    const handleToggleStatus = async (tenantId, currentStatus) => {
        if (!confirm(`Bạn muốn ${currentStatus ? 'vô hiệu hóa' : 'kích hoạt'} tenant này?`)) {
            return;
        }

        try {
            await updateTenantStatus(tenantId, !currentStatus);
            loadTenants(); // Refresh
        } catch (error) {
            alert('Cập nhật thất bại: ' + error.message);
        }
    };

    if (loading && page === 0) return <Loading fullPage />;

    return (
        <div className="platform-tenant-list">
            <header>
                <h1>Quản lý Tenant</h1>
                <p>Danh sách tất cả quán trong hệ thống</p>
            </header>

            {/* Filters */}
            <div className="filters">
                <div className="search-box">
                    <Input
                        placeholder="Tìm kiếm tenant..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                    />
                    <Button onClick={handleSearch}>
                        <Search size={16} /> Tìm
                    </Button>
                </div>

                <div className="filter-buttons">
                    <button
                        className={filter === null ? 'active' : ''}
                        onClick={() => setFilter(null)}
                    >
                        Tất cả
                    </button>
                    <button
                        className={filter === true ? 'active' : ''}
                        onClick={() => setFilter(true)}
                    >
                        Đang hoạt động
                    </button>
                    <button
                        className={filter === false ? 'active' : ''}
                        onClick={() => setFilter(false)}
                    >
                        Đã vô hiệu hóa
                    </button>
                </div>
            </div>

            {/* Table */}
            <Card>
                <table className="tenant-table">
                    <thead>
                        <tr>
                            <th>Tên quán</th>
                            <th>Owner</th>
                            <th>Địa chỉ</th>
                            <th>Trạng thái</th>
                            <th>Đơn hàng</th>
                            <th>Doanh thu tháng</th>
                            <th>Hoạt động gần nhất</th>
                            <th>Hành động</th>
                        </tr>
                    </thead>
                    <tbody>
                        {tenants.map(tenant => (
                            <tr key={tenant.id}>
                                <td>
                                    <div className="tenant-info">
                                        {tenant.logoUrl && (
                                            <img src={tenant.logoUrl} alt="" className="tenant-logo" />
                                        )}
                                        <strong>{tenant.name}</strong>
                                    </div>
                                </td>
                                <td>
                                    <div>
                                        <div>{tenant.ownerName}</div>
                                        <small>{tenant.ownerEmail}</small>
                                    </div>
                                </td>
                                <td>{tenant.address}</td>
                                <td>
                                    <StatusBadge
                                        status={tenant.isActive ? 'active' : 'inactive'}
                                        text={tenant.isActive ? 'Hoạt động' : 'Vô hiệu hóa'}
                                    />
                                </td>
                                <td>{tenant.totalOrders || 0}</td>
                                <td>{formatPrice(tenant.monthlyRevenue || 0)}</td>
                                <td>
                                    {tenant.lastOrderAt
                                        ? formatDate(tenant.lastOrderAt)
                                        : 'Chưa có'}
                                </td>
                                <td>
                                    <button
                                        className="toggle-btn"
                                        onClick={() => handleToggleStatus(tenant.id, tenant.isActive)}
                                    >
                                        {tenant.isActive ? (
                                            <><ToggleRight /> Vô hiệu hóa</>
                                        ) : (
                                            <><ToggleLeft /> Kích hoạt</>
                                        )}
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </Card>

            {/* Pagination */}
            {totalPages > 1 && (
                <div className="pagination">
                    <Button
                        disabled={page === 0}
                        onClick={() => setPage(page - 1)}
                    >
                        Trước
                    </Button>
                    <span>Trang {page + 1} / {totalPages}</span>
                    <Button
                        disabled={page >= totalPages - 1}
                        onClick={() => setPage(page + 1)}
                    >
                        Sau
                    </Button>
                </div>
            )}
        </div>
    );
}
```

---

## IV. API CLIENT

### src/api/platform.js

```javascript
import client from './client';

export const getPlatformStats = async () => {
    const res = await client.get('/api/platform/stats');
    return res.data.data;
};

export const getAllTenants = async (params) => {
    const res = await client.get('/api/platform/tenants', { params });
    return res.data.data;
};

export const getTenantDetail = async (id) => {
    const res = await client.get(`/api/platform/tenants/${id}`);
    return res.data.data;
};

export const updateTenantStatus = async (id, isActive) => {
    const res = await client.patch(`/api/platform/tenants/${id}/status`, null, {
        params: { isActive }
    });
    return res.data;
};
```

---

## V. ROUTES

### App.jsx - Thêm Platform routes

```jsx
// Platform Routes (Role-based)
<Route
    path="/platform/dashboard"
    element={
        <ProtectedRoute>
            <PlatformRoute>
                <PlatformDashboardPage />
            </PlatformRoute>
        </ProtectedRoute>
    }
/>
<Route
    path="/platform/tenants"
    element={
        <ProtectedRoute>
            <PlatformRoute>
                <PlatformTenantListPage />
            </PlatformRoute>
        </ProtectedRoute>
    }
/>
```

### Route Wrapper:

```jsx
function PlatformRoute({ children }) {
    const { user } = useAuth();

    // Check if user has PLATFORM role from JWT
    const isPlatformAdmin = user?.roles?.includes('PLATFORM');

    if (!isPlatformAdmin) {
        return <Navigate to="/pos" replace />;
    }

    return children;
}
```

---

## VI. CHECKLIST TRIỂN KHAI

### Backend:
- [ ] Tạo module `platform` (controller, service, dto)
- [ ] Implement PlatformService với logic thống kê
- [ ] Thêm repository methods (countByIsActive, sumRevenue, etc.)
- [ ] Update SecurityConfig: `.requestMatchers("/api/platform/**").hasRole("PLATFORM")`
- [ ] Test endpoints với Postman/curl

### Frontend:
- [ ] Tạo `src/api/platform.js`
- [ ] Tạo `PlatformDashboardPage.jsx`
- [ ] Tạo `PlatformTenantListPage.jsx`
- [ ] Tạo `PlatformRoute` wrapper
- [ ] Thêm routes vào App.jsx
- [ ] Styling (CSS files)
- [ ] Test UI flow

### Security:
- [ ] Đảm bảo JWT có claim `roles: ["PLATFORM"]`
- [ ] Frontend check user role trước khi render
- [ ] Backend enforce @PreAuthorize("hasRole('PLATFORM')")

---

## VII. MỘT SỐ LƯU Ý

1. **Role Management:**
   - Platform admin phải được cấp role `PLATFORM` trong Keycloak
   - Frontend check role từ JWT token
   - Backend enforce bằng Spring Security

2. **Performance:**
   - Cache stats nếu cần (Redis)
   - Pagination cho danh sách tenant
   - Lazy load biểu đồ

3. **Future Enhancements:**
   - Export reports (PDF/Excel)
   - Email notifications cho tenant
   - Advanced analytics với charts (Chart.js/Recharts)
   - Tenant subscription management
