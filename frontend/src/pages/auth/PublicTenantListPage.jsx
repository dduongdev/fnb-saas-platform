import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, MapPin, ArrowRight } from 'lucide-react';
import { Loading, Empty, Card, Button } from '../../components/common';
import { getPublicTenants } from '../../api/tenant';
import { useAuth } from '../../context/AuthContext';
import './PublicTenantListPage.css';

export function PublicTenantListPage() {
    const navigate = useNavigate();
    const { isAuthenticated, user, logout } = useAuth();
    const [shops, setShops] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadShops();
    }, []);

    const loadShops = async () => {
        try {
            setLoading(true);
            const data = await getPublicTenants(0, 50); // Get first 50 shops
            setShops(data?.content || []);
        } catch (error) {
            console.error('Failed to load shops:', error);
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="public-tenant-page">
                <Loading fullPage text="Đang tải danh sách quán..." />
            </div>
        );
    }

    return (
        <div className="public-tenant-page">
            <header className="public-tenant-header">
                <h1>F&B SaaS Platform</h1>
                <p>Khám phá các quán ăn, cà phê tuyệt vời</p>

                <div style={{ marginTop: 24 }}>
                    {isAuthenticated ? (
                        <div style={{ display: 'flex', justifyContent: 'center', gap: 16, alignItems: 'center' }}>
                            <span>Xin chào, <strong>{user?.fullName}</strong></span>
                            <Button size="sm" onClick={() => navigate('/dashboard')}>
                                Quản lý quán
                            </Button>
                        </div>
                    ) : (
                        <Button variant="secondary" onClick={() => navigate('/login')}>
                            Đăng nhập quản lý
                        </Button>
                    )}
                </div>
            </header>

            {shops.length === 0 ? (
                <Empty
                    icon={Store}
                    message="Chưa có quán nào"
                    description="Hệ thống đang được cập nhật..."
                />
            ) : (
                <div className="public-tenant-grid">
                    {shops.map(shop => (
                        <Card
                            key={shop.id}
                            className="public-shop-card"
                            onClick={() => navigate(`/menu/${shop.id}`)}
                        >
                            <div className="shop-row">
                                <div className="shop-logo-wrapper">
                                    {shop.logoUrl ? (
                                        <img src={shop.logoUrl} alt={shop.name} className="shop-logo-small" />
                                    ) : (
                                        <Store className="shop-placeholder-icon" />
                                    )}
                                </div>
                                <div className="shop-details">
                                    <h3 className="shop-name">{shop.name}</h3>
                                    <div className="shop-address">
                                        <MapPin size={14} />
                                        <span>{shop.address}</span>
                                    </div>
                                </div>
                            </div>
                            <div className="shop-actions">
                                <Button
                                    className="visit-btn"
                                    variant="secondary"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        navigate(`/menu/${shop.id}`);
                                    }}
                                >
                                    Xem Menu <ArrowRight size={16} />
                                </Button>
                            </div>
                            {/* Note: Real flow requires scanning QR at table, but here we just list shops */}
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}
