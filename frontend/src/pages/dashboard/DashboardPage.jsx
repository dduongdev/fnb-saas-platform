import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { 
    Store, User, Settings, ChefHat, CreditCard, 
    BarChart3, Users, Coffee, ArrowRight, Plus,
    Sparkles, Shield, Zap, Globe
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import { Card, Button, Loading } from '../../components/common';
import { getMyTenants } from '../../api/tenant';
import './DashboardPage.css';

/**
 * Dashboard Page - Trang chính sau khi đăng nhập
 * 
 * Hiển thị:
 * - Thông tin user
 * - Quick links đến các quán của user
 * - Giới thiệu tính năng nền tảng
 */
export function DashboardPage() {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const { selectTenant } = useTenant();
    const [tenants, setTenants] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadTenants();
    }, []);

    const loadTenants = async () => {
        try {
            const data = await getMyTenants();
            setTenants(data || []);
        } catch (error) {
            console.error('Failed to load tenants:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleSelectTenant = async (tenant) => {
        // Use context to properly set tenant state
        await selectTenant(tenant.id);
        navigate('/pos');
    };

    return (
        <div className="dashboard-page">
            {/* Header */}
            <header className="dashboard-header">
                <div className="header-left">
                    <div className="logo">
                        <Coffee size={32} />
                        <span>F&B Platform</span>
                    </div>
                </div>
                <div className="header-right">
                    <Link to="/profile" className="header-link">
                        <User size={18} />
                        <span>{user?.fullName || 'Tài khoản'}</span>
                    </Link>
                    <button className="btn-logout" onClick={logout}>
                        Đăng xuất
                    </button>
                </div>
            </header>

            {/* Hero Section */}
            <section className="dashboard-hero">
                <div className="hero-content">
                    <h1>Xin chào, {user?.fullName || 'bạn'}! 👋</h1>
                    <p>Chào mừng bạn đến với nền tảng quản lý F&B thông minh</p>
                </div>
            </section>

            {/* Main Content */}
            <main className="dashboard-main">
                {/* My Shops Section */}
                <section className="dashboard-section">
                    <div className="section-header">
                        <h2><Store size={24} /> Quán của tôi</h2>
                        <Link to="/my-shops" className="view-all-link">
                            Xem tất cả <ArrowRight size={16} />
                        </Link>
                    </div>

                    {loading ? (
                        <Loading />
                    ) : tenants.length === 0 ? (
                        <Card className="empty-shops-card">
                            <Store size={48} className="empty-icon" />
                            <h3>Chưa có quán nào</h3>
                            <p>Bạn chưa sở hữu hoặc tham gia quản lý quán nào.</p>
                            <Button onClick={() => navigate('/my-shops')}>
                                <Plus size={18} /> Tạo quán mới
                            </Button>
                        </Card>
                    ) : (
                        <div className="shops-grid">
                            {tenants.slice(0, 3).map(tenant => (
                                <Card 
                                    key={tenant.id} 
                                    className="shop-card"
                                    onClick={() => handleSelectTenant(tenant)}
                                >
                                    <div className="shop-logo">
                                        {tenant.logoUrl ? (
                                            <img src={tenant.logoUrl} alt={tenant.name} />
                                        ) : (
                                            <Store size={32} />
                                        )}
                                    </div>
                                    <div className="shop-info">
                                        <h3>{tenant.name}</h3>
                                        <p>{tenant.address || 'Chưa có địa chỉ'}</p>
                                        <span className="shop-role owner">
                                            👑 Chủ quán
                                        </span>
                                    </div>
                                    <ArrowRight size={20} className="shop-arrow" />
                                </Card>
                            ))}
                        </div>
                    )}
                </section>

                {/* Quick Actions */}
                <section className="dashboard-section">
                    <h2><Zap size={24} /> Truy cập nhanh</h2>
                    <div className="quick-actions">
                        <Card className="action-card" onClick={() => navigate('/my-shops')}>
                            <Store size={28} />
                            <span>Quán của tôi</span>
                        </Card>
                        <Card className="action-card" onClick={() => navigate('/profile')}>
                            <User size={28} />
                            <span>Hồ sơ cá nhân</span>
                        </Card>
                        <Card className="action-card" onClick={() => navigate('/shops')}>
                            <Globe size={28} />
                            <span>Khám phá quán</span>
                        </Card>
                    </div>
                </section>

                {/* Platform Features */}
                <section className="dashboard-section features-section">
                    <h2><Sparkles size={24} /> Tính năng nổi bật</h2>
                    <div className="features-grid">
                        <div className="feature-card">
                            <div className="feature-icon">
                                <ChefHat size={32} />
                            </div>
                            <h3>Quản lý thực đơn</h3>
                            <p>Dễ dàng thêm, sửa, xóa món ăn. Phân loại theo danh mục, quản lý giá và trạng thái.</p>
                        </div>
                        <div className="feature-card">
                            <div className="feature-icon">
                                <CreditCard size={32} />
                            </div>
                            <h3>Thanh toán đa dạng</h3>
                            <p>Hỗ trợ tiền mặt, VNPAY, MoMo. Quản lý hóa đơn và lịch sử giao dịch.</p>
                        </div>
                        <div className="feature-card">
                            <div className="feature-icon">
                                <BarChart3 size={32} />
                            </div>
                            <h3>Báo cáo thông minh</h3>
                            <p>Thống kê doanh thu theo ngày, tuần, tháng. Phân tích món bán chạy.</p>
                        </div>
                        <div className="feature-card">
                            <div className="feature-icon">
                                <Users size={32} />
                            </div>
                            <h3>Quản lý nhân sự</h3>
                            <p>Phân quyền nhân viên, quản lý ca làm việc, đăng tin tuyển dụng.</p>
                        </div>
                        <div className="feature-card">
                            <div className="feature-icon">
                                <Shield size={32} />
                            </div>
                            <h3>Bảo mật cao</h3>
                            <p>Dữ liệu được mã hóa, xác thực OAuth2, phân quyền theo vai trò.</p>
                        </div>
                        <div className="feature-card">
                            <div className="feature-icon">
                                <Zap size={32} />
                            </div>
                            <h3>Realtime Updates</h3>
                            <p>Cập nhật đơn hàng, thông báo tức thì qua WebSocket.</p>
                        </div>
                    </div>
                </section>
            </main>

            {/* Footer */}
            <footer className="dashboard-footer">
                <p>© 2024 F&B Platform. Nền tảng quản lý nhà hàng, quán cafe thông minh.</p>
            </footer>
        </div>
    );
}
