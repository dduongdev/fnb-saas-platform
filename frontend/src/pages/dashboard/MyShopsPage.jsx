import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { 
    Store, Plus, ArrowLeft, Settings, Users, 
    MapPin, Phone, Clock, MoreVertical, 
    BarChart3, ChevronRight
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import { Card, Button, Loading, Modal, Input } from '../../components/common';
import { getMyTenants, createTenant } from '../../api/tenant';
import './MyShopsPage.css';

/**
 * My Shops Page - Trang "Quán của tôi"
 * 
 * Hiển thị danh sách tất cả các quán mà user sở hữu hoặc tham gia quản lý
 * Cho phép tạo quán mới
 */
export function MyShopsPage() {
    const navigate = useNavigate();
    const { user } = useAuth();
    const { selectTenant } = useTenant();
    const [tenants, setTenants] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [creating, setCreating] = useState(false);

    // Form state for new shop
    const [newShopName, setNewShopName] = useState('');
    const [newShopAddress, setNewShopAddress] = useState('');
    const [newShopLogo, setNewShopLogo] = useState(null);

    useEffect(() => {
        loadTenants();
    }, []);

    const loadTenants = async () => {
        try {
            setLoading(true);
            const data = await getMyTenants();
            setTenants(data || []);
        } catch (error) {
            console.error('Failed to load tenants:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleSelectTenant = async (tenant) => {
        await selectTenant(tenant.id);
        navigate('/pos');
    };

    const handleCreateShop = async () => {
        if (!newShopName.trim()) return;

        try {
            setCreating(true);
            await createTenant(newShopName.trim(), newShopAddress.trim(), newShopLogo);
            setShowCreateModal(false);
            setNewShopName('');
            setNewShopAddress('');
            setNewShopLogo(null);
            await loadTenants();
        } catch (error) {
            alert('Lỗi tạo quán: ' + error.message);
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

    // API getMyTenants only returns tenants owned by user
    // All tenants from this API are OWNER role
    const ownedShops = tenants;

    return (
        <div className="my-shops-page">
            {/* Header */}
            <header className="shops-header">
                <button className="btn-back" onClick={() => navigate('/dashboard')}>
                    <ArrowLeft size={20} />
                </button>
                <h1>Quán của tôi</h1>
                <Button onClick={() => setShowCreateModal(true)}>
                    Tạo quán mới
                </Button>
            </header>

            {/* Content */}
            <main className="shops-content">
                {loading ? (
                    <Loading />
                ) : tenants.length === 0 ? (
                    <div className="empty-state">
                        <Store size={64} />
                        <h2>Chưa có quán nào</h2>
                        <p>Bạn chưa sở hữu hoặc tham gia quản lý quán nào.</p>
                        <Button onClick={() => setShowCreateModal(true)} size="lg">
                            <Plus size={20} /> Tạo quán đầu tiên
                        </Button>
                    </div>
                ) : (
                    <>
                        {/* Owned Shops */}
                        {ownedShops.length > 0 && (
                            <section className="shops-section">
                                <h2>Quán của tôi ({ownedShops.length})</h2>
                                <div className="shops-list">
                                    {ownedShops.map(tenant => (
                                        <ShopCard 
                                            key={tenant.id} 
                                            tenant={tenant} 
                                            onSelect={() => handleSelectTenant(tenant)}
                                            isOwner
                                        />
                                    ))}
                                </div>
                            </section>
                        )}

                        {/* TODO: Staff at Shops - cần API riêng để lấy tenants user là staff */}
                    </>
                )}
            </main>

            {/* Create Shop Modal */}
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
                            onChange={e => setNewShopName(e.target.value)}
                            placeholder="VD: Cafe ABC"
                        />
                    </div>
                    <div className="form-group">
                        <label>Địa chỉ</label>
                        <Input
                            value={newShopAddress}
                            onChange={e => setNewShopAddress(e.target.value)}
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
                        <Button 
                            variant="secondary" 
                            onClick={() => setShowCreateModal(false)}
                        >
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
        </div>
    );
}

/**
 * Shop Card Component
 */
function ShopCard({ tenant, onSelect, isOwner }) {
    return (
        <Card className="shop-card-large" onClick={onSelect}>
            <div className="shop-card-left">
                <div className="shop-logo-large">
                    {tenant.logoUrl ? (
                        <img src={tenant.logoUrl} alt={tenant.name} />
                    ) : (
                        <Store size={40} />
                    )}
                </div>
            </div>
            <div className="shop-card-center">
                <h3>{tenant.name}</h3>
                {tenant.address && (
                    <p className="shop-address">
                        <MapPin size={14} /> {tenant.address}
                    </p>
                )}
                <div className="shop-meta">
                    <span className={`role-badge ${isOwner ? 'owner' : 'staff'}`}>
                        {isOwner ? 'Chủ quán' : tenant.roleName || 'Nhân viên'}
                    </span>
                    <span className={`status-badge ${tenant.isActive ? 'active' : 'inactive'}`}>
                        {tenant.isActive ? 'Đang hoạt động' : 'Tạm ngưng'}
                    </span>
                </div>
            </div>
            <div className="shop-card-right">
                <ChevronRight size={24} />
            </div>
        </Card>
    );
}
