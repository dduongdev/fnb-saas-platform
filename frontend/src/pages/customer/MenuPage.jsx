import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Utensils, X } from 'lucide-react';
import { Loading, Card, Button, Empty } from '../../components/common';
import { getPublicMenu } from '../../api/pos';
import { getPublicTenantDetail } from '../../api/tenant';
import { formatPrice } from '../../utils/format';
import './MenuPage.css';

export function MenuPage() {
    const { tenantId } = useParams();
    const navigate = useNavigate();
    const [tenant, setTenant] = useState(null);
    const [categories, setCategories] = useState([]);
    const [activeCategory, setActiveCategory] = useState(null);
    const [loading, setLoading] = useState(true);
    const [selectedProduct, setSelectedProduct] = useState(null);

    useEffect(() => {
        const loadData = async () => {
            try {
                setLoading(true);
                const [tenantData, menuData] = await Promise.all([
                    getPublicTenantDetail(tenantId),
                    getPublicMenu(tenantId),
                ]);

                setTenant(tenantData);
                setCategories(menuData || []);
                setActiveCategory(menuData?.[0]?.categoryId || null);
            } catch (error) {
                console.error('[MenuPage] Load failed:', error);
            } finally {
                setLoading(false);
            }
        };

        if (tenantId) {
            loadData();
        }
    }, [tenantId]);

    if (loading) {
        return (
            <div className="menu-page-loading">
                <Loading fullPage text="Đang tải thực đơn..." />
            </div>
        );
    }

    if (!tenant) {
        return (
            <div className="menu-page-empty">
                <Empty
                    icon={Utensils}
                    message="Không tìm thấy quán"
                    description="Xin hãy quay lại danh sách quán để chọn lại."
                />
                <Button onClick={() => navigate('/shops')}>
                    Quay lại quán
                </Button>
            </div>
        );
    }

    const products = categories.find(c => c.categoryId === activeCategory)?.products || [];

    return (
        <div className="menu-page">
            <header className="menu-header">
                <Button variant="ghost" onClick={() => navigate('/shops')}>
                    &larr; Quay về danh sách quán
                </Button>
                <div>
                    <h1>{tenant.name || 'Menu quán'}</h1>
                    <p>{tenant.address || 'Không có địa chỉ'}</p>
                </div>
            </header>

            <nav className="menu-categories">
                {categories.map(category => (
                    <button
                        key={category.categoryId}
                        className={activeCategory === category.categoryId ? 'active' : ''}
                        onClick={() => setActiveCategory(category.categoryId)}
                    >
                        {category.categoryName}
                    </button>
                ))}
            </nav>

            <main className="menu-products">
                {products.length === 0 ? (
                    <Empty
                        icon={Utensils}
                        message="Chưa có món trong mục này"
                        description="Vui lòng chọn mục khác hoặc quay lại sau."
                    />
                ) : (
                    <div className="menu-grid">
                        {products.map(product => (
                            <Card key={product.id} className="menu-product-card" onClick={() => setSelectedProduct(product)}>
                                <div className="menu-product-image">
                                    {product.thumbnailUrl ? (
                                        <img src={product.thumbnailUrl} alt={product.name} />
                                    ) : (
                                        <div className="menu-no-image"><Utensils size={28} /></div>
                                    )}
                                </div>
                                <div className="menu-product-info">
                                    <h3>{product.name}</h3>
                                    <p className="price">{formatPrice(product.price)}</p>
                                    <p className="short-desc">{product.description || 'Không có mô tả'}</p>
                                </div>
                                <div className="view-more">Xem chi tiết</div>
                            </Card>
                        ))}
                    </div>
                )}
            </main>

            {selectedProduct && (
                <div className="menu-modal-overlay" onClick={() => setSelectedProduct(null)}>
                    <div className="menu-modal" onClick={(e) => e.stopPropagation()}>
                        <div className="menu-modal-header">
                            <h3>Chi tiết món</h3>
                            <button className="close-button" onClick={() => setSelectedProduct(null)}>
                                <X size={18} />
                            </button>
                        </div>
                        <div className="menu-modal-content">
                            <div className="modal-image">
                                {selectedProduct.thumbnailUrl ? (
                                    <img src={selectedProduct.thumbnailUrl} alt={selectedProduct.name} />
                                ) : (
                                    <div className="menu-no-image"><Utensils size={48} /></div>
                                )}
                            </div>
                            <h4>{selectedProduct.name}</h4>
                            <p className="price">{formatPrice(selectedProduct.price)}</p>
                            <p>{selectedProduct.description || 'Không có mô tả'}</p>
                        </div>
                        <div className="menu-modal-actions">
                            <Button variant="secondary" onClick={() => setSelectedProduct(null)}>
                                Đóng
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
