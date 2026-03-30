import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { 
    Store, User, Camera, Settings, ChefHat, CreditCard, 
    BarChart3, Users, Coffee, ArrowRight, Plus,
    Sparkles, Shield, Zap, Globe
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import { Card, Button, Loading, Modal, Input } from '../../components/common';
import { createTenant } from '../../api/tenant';
import { uploadAvatar } from '../../api/auth';
import { useToast } from '../../context/ToastContext';
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
    const location = useLocation();
    const { user, logout } = useAuth();
    const { tenants, loading, selectTenant, reloadTenants } = useTenant();
    const toast = useToast();
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showProfileModal, setShowProfileModal] = useState(false);
    const [profileUploading, setProfileUploading] = useState(false);
    const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || null);
    const [creating, setCreating] = useState(false);
    const [newShopName, setNewShopName] = useState('');
    const [newShopAddress, setNewShopAddress] = useState('');
    const [newShopLogo, setNewShopLogo] = useState(null);

    // Dashboard reads tenants from TenantContext, no local API call needed.
    useEffect(() => {
        const params = new URLSearchParams(location.search);
        if (params.get('profile') === 'true') {
            setShowProfileModal(true);
        }
    }, [location.search]);

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        if (params.get('profile') === 'true') {
            setShowProfileModal(true);
        }
    }, [location.search]);

    useEffect(() => {
        if (user?.avatarUrl) {
            setAvatarUrl(user.avatarUrl);
        }
    }, [user]);

    const handleSelectTenant = async (tenant) => {
        // Use context to properly set tenant state
        await selectTenant(tenant.id);
        navigate('/pos');
    };

    const handleCreateShop = async () => {
        if (!newShopName.trim()) {
            toast.warning('Vui lòng nhập tên quán');
            return;
        }

        try {
            setCreating(true);
            await createTenant(newShopName.trim(), newShopAddress.trim(), newShopLogo);
            toast.success('Tạo quán mới thành công');
            setShowCreateModal(false);
            setNewShopName('');
            setNewShopAddress('');
            setNewShopLogo(null);
            await reloadTenants();
        } catch (error) {
            toast.error('Lỗi tạo quán: ' + error.message);
        } finally {
            setCreating(false);
        }
    };

    const handleLogoChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            if (file.size > 5 * 1024 * 1024) {
                alert('File ảnh quá lớn (max 5MB)');
                return;
            }
            setNewShopLogo(file);
        }
    };

    const handleAvatarChange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        if (file.size > 5 * 1024 * 1024) {
            toast.error('File ảnh quá lớn (max 5MB)');
            return;
        }

        try {
            setProfileUploading(true);
            const data = await uploadAvatar(file);

            if (data?.avatarUrl) {
                setAvatarUrl(data.avatarUrl);
            }

            toast.success('Cập nhật ảnh đại diện thành công');
            setShowProfileModal(false);
        } catch (error) {
            toast.error('Lỗi upload avatar: ' + error.message);
        } finally {
            setProfileUploading(false);
        }
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
                    <button className="header-link" onClick={() => setShowProfileModal(true)}>
                        <User size={18} />
                        <span>{user?.fullName || 'Tài khoản'}</span>
                    </button>
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
                    <div className="section-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <h2><Store size={24} /> Quán của tôi</h2>
                        <Button onClick={() => setShowCreateModal(true)}>
                            Tạo quán mới
                        </Button>
                    </div>

                    {loading ? (
                        <Loading />
                    ) : tenants.length === 0 ? (
                        <Card className="empty-shops-card">
                            <Store size={48} className="empty-icon" />
                            <h3>Chưa có quán nào</h3>
                            <p>Bạn chưa sở hữu hoặc tham gia quản lý quán nào.</p>
                            <Button onClick={() => setShowCreateModal(true)}>
                                Tạo quán mới
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
                        <Card className="action-card" onClick={() => setShowProfileModal(true)}>
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

            <Modal
                isOpen={showProfileModal}
                onClose={() => {
                    setShowProfileModal(false);
                    navigate('/dashboard', { replace: true });
                }}
                title="Hồ sơ cá nhân"
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ position: 'relative', width: 60, height: 60 }}>
                            {avatarUrl ? (
                                <img
                                    src={avatarUrl}
                                    alt={user?.fullName || user?.username || 'Tài khoản'}
                                    style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
                                />
                            ) : user?.avatarUrl ? (
                                <img
                                    src={user.avatarUrl}
                                    alt={user.fullName || user.username}
                                    style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
                                />
                            ) : (
                                <div style={{ width: '100%', height: '100%', borderRadius: '50%', background: '#eee', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                    <User size={32} />
                                </div>
                            )}
                            <label
                                style={{
                                    position: 'absolute',
                                    bottom: -4,
                                    right: -4,
                                    width: 24,
                                    height: 24,
                                    borderRadius: '50%',
                                    background: '#fff',
                                    border: '1px solid #ccc',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    cursor: 'pointer',
                                    boxShadow: '0 0 4px rgba(0,0,0,0.2)'
                                }}
                                title="Cập nhật avatar"
                            >
                                <Camera size={14} />
                                <input
                                    type="file"
                                    accept="image/*"
                                    onChange={handleAvatarChange}
                                    style={{ display: 'none' }}
                                    disabled={profileUploading}
                                />
                            </label>
                        </div>
                        <div>
                            <h3 style={{ margin: 0 }}>{user?.fullName || user?.username || 'Tài khoản'}</h3>
                            <p style={{ margin: '4px 0 0' }}>{user?.email}</p>
                        </div>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <span><strong>User ID:</strong> {user?.id || 'N/A'}</span>
                    </div>
                    {profileUploading && <span>Đang tải ảnh...</span>}
                    <Button variant="secondary" onClick={() => { setShowProfileModal(false); logout(); }}>
                        Đăng xuất
                    </Button>
                </div>
            </Modal>

            <Modal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                title="Tạo quán mới"
            >
                <div className="create-shop-form">
                    <div className="form-group">
                        <label>Tên quán *</label>
                        <Input
                            value={newShopName}
                            onChange={(e) => setNewShopName(e.target.value)}
                            placeholder="VD: Cafe ABC"
                        />
                    </div>
                    <div className="form-group">
                        <label>Địa chỉ</label>
                        <Input
                            value={newShopAddress}
                            onChange={(e) => setNewShopAddress(e.target.value)}
                            placeholder="VD: 123 Đường ABC, Quận XYZ"
                        />
                    </div>
                    <div className="form-group">
                        <label>Logo quán</label>
                        <input
                            type="file"
                            accept="image/*"
                            onChange={handleLogoChange}
                            className="file-input"
                        />
                        {newShopLogo && (
                            <p className="file-name">{newShopLogo.name}</p>
                        )}
                    </div>

                    <div className="form-actions">
                        <Button variant="secondary" onClick={() => setShowCreateModal(false)}>
                            Hủy
                        </Button>
                        <Button
                            onClick={handleCreateShop}
                            loading={creating}
                            disabled={!newShopName.trim()}
                        >
                            Tạo quán
                        </Button>
                    </div>
                </div>
            </Modal>

            {/* Footer */}
            <footer className="dashboard-footer">
                <p>© 2024 F&B Platform. Nền tảng quản lý nhà hàng, quán cafe thông minh.</p>
            </footer>
        </div>
    );
}
