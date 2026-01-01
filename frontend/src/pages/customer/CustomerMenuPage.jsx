import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { ShoppingCart, Minus, Plus, X, Utensils, Clock, CheckCircle, XCircle, RefreshCw } from 'lucide-react';
import { Loading, Button, Card, Empty, Input } from '../../components/common';
import { getPublicMenu, getTableInfo } from '../../api/pos';
import { createCustomerOrder, getCustomerOrderStatus, addCustomerItems } from '../../api/session';
import { formatPrice } from '../../utils/format';
import './CustomerMenuPage.css';

export function CustomerMenuPage() {
    const { tableId } = useParams();
    const [tableInfo, setTableInfo] = useState(null);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeCategory, setActiveCategory] = useState(null);
    const [cart, setCart] = useState([]);
    const [showCart, setShowCart] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Order status tracking
    const [currentOrder, setCurrentOrder] = useState(null); // { sessionId, status, ... }
    const [orderView, setOrderView] = useState('menu'); // 'menu' | 'pending' | 'confirmed' | 'rejected'
    const pollingRef = useRef(null);

    useEffect(() => {
        if (tableId) {
            loadData();
        }

        // Cleanup polling on unmount
        return () => {
            if (pollingRef.current) {
                clearInterval(pollingRef.current);
            }
        };
    }, [tableId]);

    // Poll for order status when pending
    useEffect(() => {
        if (currentOrder?.sessionId && currentOrder.status === 'PENDING') {
            startPollingStatus(currentOrder.sessionId);
        }
        return () => {
            if (pollingRef.current) {
                clearInterval(pollingRef.current);
            }
        };
    }, [currentOrder?.sessionId, currentOrder?.status]);

    const startPollingStatus = (sessionId) => {
        if (pollingRef.current) {
            clearInterval(pollingRef.current);
        }

        pollingRef.current = setInterval(async () => {
            try {
                const status = await getCustomerOrderStatus(sessionId);
                setCurrentOrder(status);

                if (status.status === 'ACTIVE') {
                    setOrderView('confirmed');
                    clearInterval(pollingRef.current);
                } else if (status.status === 'CANCELLED') {
                    setOrderView('rejected');
                    clearInterval(pollingRef.current);
                }
            } catch (error) {
                console.error('Failed to check status:', error);
            }
        }, 3000); // Poll every 3 seconds
    };

    const loadData = async () => {
        try {
            setLoading(true);
            // 1. Get table info (includes tenantId needed for subsequent requests)
            const info = await getTableInfo(tableId);
            setTableInfo(info);

            // IMPORTANT: Set tenant_id for API client to use in subsequent requests
            if (info.tenantId) {
                localStorage.setItem('tenant_id', info.tenantId);
            }

            // 2. Get public menu
            const menuData = await getPublicMenu();
            setCategories(menuData || []);
            if (menuData?.length > 0) {
                setActiveCategory(menuData[0].categoryId);
            }
        } catch (error) {
            console.error('Failed to load menu:', error);
            alert('Không thể tải menu: ' + error.message);
        } finally {
            setLoading(false);
        }
    };

    const addToCart = (product) => {
        setCart(prev => {
            const existing = prev.find(item => item.productId === product.id);
            if (existing) {
                return prev.map(item =>
                    item.productId === product.id
                        ? { ...item, quantity: item.quantity + 1 }
                        : item
                );
            }
            return [...prev, {
                productId: product.id,
                productName: product.name,
                price: product.price,
                imageUrl: product.thumbnailUrl,
                quantity: 1
            }];
        });
    };

    const updateQuantity = (productId, delta) => {
        setCart(prev => {
            return prev.map(item => {
                if (item.productId === productId) {
                    return { ...item, quantity: Math.max(0, item.quantity + delta) };
                }
                return item;
            }).filter(item => item.quantity > 0);
        });
    };

    const totalAmount = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    const totalItems = cart.reduce((sum, item) => sum + item.quantity, 0);

    const handleSubmitOrder = async () => {
        if (cart.length === 0) return;

        try {
            setSubmitting(true);

            const items = cart.map(item => ({
                productId: item.productId,
                quantity: item.quantity
            }));

            // Check if table already has active session
            if (tableInfo?.hasActiveSession && tableInfo?.sessionId) {
                // Add to existing session
                const result = await addCustomerItems(tableInfo.sessionId, {
                    tableId: parseInt(tableId),
                    items
                });
                setCurrentOrder(result);
                setOrderView('confirmed');
            } else {
                // Create new pending session
                const result = await createCustomerOrder({
                    tableId: parseInt(tableId),
                    items
                });
                setCurrentOrder(result);
                setOrderView('pending');
            }

            setCart([]);
            setShowCart(false);
        } catch (error) {
            alert('Đặt món thất bại: ' + error.message);
        } finally {
            setSubmitting(false);
        }
    };

    const handleAddMoreItems = () => {
        setOrderView('menu');
    };

    const handleNewOrder = () => {
        setCurrentOrder(null);
        setOrderView('menu');
        loadData(); // Refresh table info
    };

    if (loading) {
        return (
            <div className="customer-loading">
                <Loading />
                <p>Đang tải thực đơn...</p>
            </div>
        );
    }

    // Pending view - waiting for staff confirmation
    if (orderView === 'pending') {
        return (
            <div className="order-status-page">
                <div className="status-card pending">
                    <div className="status-icon">
                        <Clock size={64} className="pulse" />
                    </div>
                    <h2>Đang chờ xác nhận</h2>
                    <p>{currentOrder?.statusMessage || 'Order của bạn đã được gửi đến nhân viên.'}</p>
                    <p className="sub-text">Vui lòng đợi trong giây lát...</p>

                    {currentOrder && (
                        <div className="order-summary">
                            <h4>Chi tiết order</h4>
                            <p className="table-info">🪑 {currentOrder.tableName}</p>
                            <ul className="item-list">
                                {currentOrder.items?.map((item, idx) => (
                                    <li key={idx}>
                                        {item.productName} x{item.quantity} - {formatPrice(item.total)}
                                    </li>
                                ))}
                            </ul>
                            <p className="total">Tổng: {formatPrice(currentOrder.totalAmount)}</p>
                        </div>
                    )}

                    <div className="status-indicator">
                        <RefreshCw size={16} className="spin" />
                        <span>Đang kiểm tra trạng thái...</span>
                    </div>
                </div>
            </div>
        );
    }

    // Confirmed view - order accepted
    if (orderView === 'confirmed') {
        return (
            <div className="order-status-page">
                <div className="status-card confirmed">
                    <div className="status-icon success">
                        <CheckCircle size={64} />
                    </div>
                    <h2>Order đã được xác nhận!</h2>
                    <p>Món ăn của bạn đang được chuẩn bị.</p>

                    {currentOrder && (
                        <div className="order-summary">
                            <h4>Chi tiết order</h4>
                            <p className="table-info">🪑 {currentOrder.tableName}</p>
                            <ul className="item-list">
                                {currentOrder.items?.map((item, idx) => (
                                    <li key={idx}>
                                        {item.productName} x{item.quantity} - {formatPrice(item.total)}
                                    </li>
                                ))}
                            </ul>
                            <p className="total">Tổng: {formatPrice(currentOrder.totalAmount)}</p>
                        </div>
                    )}

                    <Button onClick={handleAddMoreItems} className="add-more-btn">
                        <Plus size={16} /> Gọi thêm món
                    </Button>
                </div>
            </div>
        );
    }

    // Rejected view - order rejected
    if (orderView === 'rejected') {
        return (
            <div className="order-status-page">
                <div className="status-card rejected">
                    <div className="status-icon error">
                        <XCircle size={64} />
                    </div>
                    <h2>Order bị từ chối</h2>
                    {currentOrder?.rejectReason && (
                        <p className="reject-reason">Lý do: {currentOrder.rejectReason}</p>
                    )}
                    <p>Vui lòng liên hệ nhân viên hoặc thử lại.</p>

                    <Button onClick={handleNewOrder} className="retry-btn">
                        Thử lại
                    </Button>
                </div>
            </div>
        );
    }

    return (
        <div className="customer-page">
            {/* Header */}
            <header className="customer-header">
                <div className="shop-info">
                    <h1>{tableInfo?.tenantName || 'Menu quán'}</h1>
                    <p>{tableInfo?.tableName || 'Bàn'}</p>
                </div>
            </header>

            {/* Categories Nav */}
            <nav className="category-nav">
                {categories.map(cat => (
                    <button
                        key={cat.categoryId}
                        className={`cat-btn ${activeCategory === cat.categoryId ? 'active' : ''}`}
                        onClick={() => setActiveCategory(cat.categoryId)}
                    >
                        {cat.categoryName}
                    </button>
                ))}
            </nav>

            {/* Products Grid */}
            <main className="product-grid">
                {categories.find(c => c.categoryId === activeCategory)?.products.map(product => (
                    <div key={product.id} className="product-card" onClick={() => addToCart(product)}>
                        <div className="product-img">
                            {product.thumbnailUrl ? (
                                <img src={product.thumbnailUrl} alt={product.name} />
                            ) : (
                                <div className="no-img"><Utensils /></div>
                            )}
                        </div>
                        <div className="product-info">
                            <h3>{product.name}</h3>
                            <p className="price">{formatPrice(product.price)}</p>
                            {product.description && (
                                <p className="desc">{product.description}</p>
                            )}
                        </div>
                        <button className="add-btn">
                            <Plus size={16} />
                        </button>
                    </div>
                ))}
            </main>

            {/* Cart Float Button */}
            {cart.length > 0 && (
                <button className="cart-float-btn" onClick={() => setShowCart(true)}>
                    <div className="cart-icon">
                        <ShoppingCart size={24} />
                        <span className="badge">{totalItems}</span>
                    </div>
                    <span className="total">{formatPrice(totalAmount)}</span>
                </button>
            )}

            {/* Cart Modal */}
            {showCart && (
                <div className="cart-overlay">
                    <div className="cart-modal">
                        <div className="cart-header">
                            <h3>Giỏ hàng của bạn</h3>
                            <button className="close-btn" onClick={() => setShowCart(false)}>
                                <X size={24} />
                            </button>
                        </div>

                        <div className="cart-items">
                            {cart.map(item => (
                                <div key={item.productId} className="cart-item">
                                    <div className="item-info">
                                        <h4>{item.productName}</h4>
                                        <span className="item-price">{formatPrice(item.price)}</span>
                                    </div>
                                    <div className="qty-control">
                                        <button onClick={() => updateQuantity(item.productId, -1)}>
                                            <Minus size={16} />
                                        </button>
                                        <span>{item.quantity}</span>
                                        <button onClick={() => updateQuantity(item.productId, 1)}>
                                            <Plus size={16} />
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="cart-footer">
                            <div className="cart-total">
                                <span>Tổng cộng:</span>
                                <span className="amount">{formatPrice(totalAmount)}</span>
                            </div>
                            <Button
                                className="checkout-btn"
                                onClick={handleSubmitOrder}
                                loading={submitting}
                                disabled={cart.length === 0}
                            >
                                Gửi gọi món
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
