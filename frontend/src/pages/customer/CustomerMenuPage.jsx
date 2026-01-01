import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { ShoppingCart, Minus, Plus, X, Utensils, Clock, CheckCircle, XCircle, RefreshCw, Trash2 } from 'lucide-react';
import { Loading, Button, Card, Empty, Input } from '../../components/common';
import { getPublicMenu, getTableInfo } from '../../api/pos';
import { createCustomerOrder, getCustomerOrderStatus, addCustomerItems, removeCustomerItem } from '../../api/session';
import { useSessionByIdWebSocket } from '../../hooks/useWebSocket';
import { formatPrice } from '../../utils/format';
import './CustomerMenuPage.css';

console.log('[CustomerMenuPage] Component loaded, CSS should be imported');

export function CustomerMenuPage() {
    const { tableId, tenantId } = useParams();
    const [tableInfo, setTableInfo] = useState(null);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeCategory, setActiveCategory] = useState(null);
    const [cart, setCart] = useState([]);
    const [showCart, setShowCart] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Order status tracking - using same structure as backend SessionResponse
    const [session, setSession] = useState(null); // Full session state from backend
    const [orderView, setOrderView] = useState('menu'); // 'menu' | 'pending' | 'confirmed' | 'rejected'
    const [actionLoading, setActionLoading] = useState(null); // itemId being processed

    // WebSocket: Handle real-time updates - UPDATE STATE DIRECTLY from events
    const handleSessionUpdate = useCallback((data) => {
        console.log('[WS Client] Session full update:', data);
        setSession(data);

        // Update view based on status
        if (data.status === 'ACTIVE') {
            setOrderView('confirmed');
        } else if (data.status === 'CANCELLED') {
            setOrderView('rejected');
        } else if (data.status === 'PENDING') {
            setOrderView('pending');
        }
    }, []);

    const handleItemEvent = useCallback((event) => {
        console.log('[WS Client] Item event:', event);

        // Update state directly from event like OrderSessionPage
        setSession(prev => {
            if (!prev) return prev;
            const order = prev.orders?.[0];
            if (!order) return prev;

            let newItems = [...(order.items || [])];

            switch (event.type) {
                case 'ORDER_ITEM_ADDED':
                    // Add new item to list
                    newItems = [...newItems, {
                        id: event.item.id,
                        productId: event.item.productId,
                        productName: event.item.productName,
                        productImage: event.item.productImage,
                        quantity: event.item.quantity,
                        price: event.item.price,
                        note: event.item.note,
                        status: event.item.status,
                        total: event.item.total
                    }];
                    break;

                case 'ORDER_ITEM_DELETED':
                    // Remove item from list
                    newItems = newItems.filter(i => i.id !== event.item.id);
                    break;

                case 'ORDER_ITEM_SERVED':
                case 'ORDER_ITEM_UPDATED':
                    // Update item in list
                    newItems = newItems.map(i =>
                        i.id === event.item.id
                            ? { ...i, ...event.item }
                            : i
                    );
                    break;
            }

            return {
                ...prev,
                totalAmount: event.newTotalAmount,
                orders: [{
                    ...order,
                    items: newItems
                }]
            };
        });
    }, []);

    useSessionByIdWebSocket(
        session?.sessionId,
        handleSessionUpdate, // onSessionUpdate
        handleItemEvent, // onItemEvent
        tenantId // tenantId override for public access
    );

    const fetchCurrentOrder = async (sessionId) => {
        try {
            console.log('[Customer] Fetching session:', sessionId);
            const data = await getCustomerOrderStatus(sessionId);
            console.log('[Customer] Session data received:', data);
            setSession(data);

            if (data.status === 'ACTIVE') {
                console.log('[Customer] Session is ACTIVE, showing confirmed view');
                setOrderView('confirmed');
            } else if (data.status === 'CANCELLED') {
                console.log('[Customer] Session is CANCELLED, showing rejected view');
                setOrderView('rejected');
            } else if (data.status === 'PENDING') {
                console.log('[Customer] Session is PENDING, showing pending view');
                setOrderView('pending');
            }
        } catch (error) {
            console.error('[Customer] Failed to fetch session:', error);
        }
    };

    useEffect(() => {
        // Ưu tiên lấy tenantId từ URL nếu có
        if (tenantId) {
            localStorage.setItem('tenant_id', tenantId);
        }

        if (tableId) {
            loadData();
        }

        // Reload table info khi user quay lại trang (để check session existing)
        const handleFocus = () => {
            if (tableId) {
                reloadTableInfo();
            }
        };
        window.addEventListener('focus', handleFocus);
        return () => window.removeEventListener('focus', handleFocus);
    }, [tableId, tenantId]);

    const loadData = async () => {
        try {
            setLoading(true);
            // 1. Get table info (includes tenantId needed for subsequent requests)
            const info = await getTableInfo(tableId);
            setTableInfo(info);
            console.log('[Customer] Table info loaded:', info);

            // Set tenant_id from API info if URL param missing
            if (info.tenantId && !tenantId) {
                localStorage.setItem('tenant_id', info.tenantId);
            }

            // 2. Check if table has existing session → load it immediately
            if (info.sessionId) {
                console.log('[Customer] Table has existing session:', info.sessionId);
                await fetchCurrentOrder(info.sessionId);
            } else {
                console.log('[Customer] No existing session for this table');
            }

            // 3. Get public menu
            const menuData = await getPublicMenu();
            console.log('[Customer] Menu data loaded:', menuData);
            setCategories(menuData || []);
            if (menuData?.length > 0) {
                setActiveCategory(menuData[0].categoryId);
            }
        } catch (error) {
            console.error('[Customer] Failed to load data:', error);
            // Không hiện alert cho error menu vì có thể là lỗi network tạm thời
            // Menu vẫn load được sau khi retry
        } finally {
            setLoading(false);
        }
    };

    const reloadTableInfo = async () => {
        try {
            const info = await getTableInfo(tableId);
            setTableInfo(info);
            console.log('[Customer] Table info reloaded:', info);

            // Nếu table có session, load session đó
            if (info.sessionId) {
                await fetchCurrentOrder(info.sessionId);
            }
        } catch (error) {
            console.error('Failed to reload table info:', error);
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

            // Reload table info để check session mới nhất
            await reloadTableInfo();

            // Check if table already has active session
            if (tableInfo?.hasActiveSession && tableInfo?.sessionId) {
                // Add to existing session
                const result = await addCustomerItems(tableInfo.sessionId, {
                    tableId: parseInt(tableId),
                    items
                });
                setSession(result);
                setOrderView(result.status === 'ACTIVE' ? 'confirmed' : 'pending');
            } else {
                // Create new pending session
                const result = await createCustomerOrder({
                    tableId: parseInt(tableId),
                    items
                });
                setSession(result);
                setOrderView('pending');
            }

            setCart([]);
            setShowCart(false);
        } catch (error) {
            console.error('[Customer] Submit order error:', error);

            // Nếu lỗi do session đã tồn tại, reload để lấy session hiện tại
            if (error.message?.includes('đang có order chờ') || error.message?.includes('PENDING')) {
                alert('Bàn này đang có đơn hàng chờ xác nhận. Đang tải đơn hàng...');
                await reloadTableInfo();
            } else {
                alert('Đặt món thất bại: ' + error.message);
            }
        } finally {
            setSubmitting(false);
        }
    };

    const handleAddMoreItems = () => {
        setOrderView('menu');
    };

    const handleNewOrder = () => {
        setSession(null);
        setOrderView('menu');
        loadData(); // Refresh table info
    };

    // Remove item from session (only PENDING items)
    const handleRemoveItem = async (item) => {
        if (item.status !== 'PENDING') {
            alert('Không thể xóa món đã mang ra');
            return;
        }

        if (!confirm(`Xóa "${item.productName}"?`)) {
            return;
        }

        try {
            setActionLoading(item.id);
            await removeCustomerItem(session.sessionId, item.id);
            // State will be updated via WebSocket event
        } catch (error) {
            if (error.status === 409) {
                alert('Món đã được mang ra, không thể xóa');
                await fetchCurrentOrder(session.sessionId); // Reload to sync
            } else {
                alert(error.message);
            }
        } finally {
            setActionLoading(null);
        }
    };

    // Derived data
    let orderItems = [];
    if (session?.items) {
        orderItems = session.items; // REST API: CustomerOrderResponse
    } else if (session?.orders?.[0]?.items) {
        orderItems = session.orders[0].items; // WebSocket: SessionResponse
    }
    const pendingItems = orderItems.filter(i => i.status === 'PENDING');
    const servedItems = orderItems.filter(i => i.status === 'SERVED');
    const sessionTotal = session?.totalAmount || 0;

    // Debug logs
    if (session && orderView === 'confirmed') {
        console.log('[Customer] Session data:', {
            sessionId: session.sessionId || session.id,
            status: session.status,
            ordersCount: session.orders?.length,
            firstOrder: session.orders?.[0],
            itemsCount: orderItems.length,
            items: orderItems
        });
    }

    if (loading) {
        return (
            <div className="customer-loading">
                <Loading />
                <p>Đang tải thực đơn...</p>
            </div>
        );
    }

    // Main layout: Always show menu + order status together
    return (
        <div className="customer-page">
            {/* Header */}
            <header className="customer-header">
                <div className="shop-info">
                    <h1>{tableInfo?.tenantName || 'Menu quán'}</h1>
                    <p>🪑 {tableInfo?.tableName || `Bàn ${tableId}`}</p>
                </div>
            </header>

            {/* Order Status Banner (if exists) */}
            {/* Order Status Banner (if exists) */}
            {session && (
                <div
                    className={`order-status-banner ${session.status?.toLowerCase()}`}
                    onClick={() => setShowCart(true)} // Click to open drawer
                >
                    <div className="status-header">
                        {session.status === 'PENDING' && (
                            <div className="status-content">
                                <Clock size={20} className="pulse" />
                                <span>Đơn hàng đang chờ xác nhận...</span>
                            </div>
                        )}
                        {session.status === 'ACTIVE' && (
                            <div className="status-content">
                                <CheckCircle size={20} />
                                <span>Đơn hàng đã được xác nhận</span>
                            </div>
                        )}
                        {session.status === 'CANCELLED' && (
                            <div className="status-content">
                                <XCircle size={20} />
                                <span>Đơn hàng bị từ chối</span>
                            </div>
                        )}
                        <span className="view-details-link">Xem chi tiết &gt;</span>
                    </div>

                    {/* Mini Summary Text */}
                    {orderItems.length > 0 && (
                        <div className="status-summary">
                            <span>{orderItems.length} món</span>
                            <span>•</span>
                            <span>{formatPrice(sessionTotal)}</span>
                        </div>
                    )}
                </div>
            )}

            {/* Current Order Summary (if exists) */}


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

            {/* Floating Action Button: Cart or Bill */}
            {(cart.length > 0 || (session && orderItems.length > 0)) && (
                <button
                    className={`cart-float-btn ${cart.length === 0 ? 'bill-mode' : ''}`}
                    onClick={() => setShowCart(true)}
                >
                    <div className="cart-icon">
                        {cart.length > 0 ? <ShoppingCart size={24} /> : <Utensils size={24} />}
                        <span className="badge">
                            {cart.length > 0 ? calculateTotalItems(cart) : orderItems.length}
                        </span>
                    </div>
                    <span className="label">
                        {cart.length > 0 ? 'Xem giỏ hàng' : 'Xem đơn / Thanh toán'}
                    </span>
                    <span className="total">{formatPrice(sessionTotal + totalAmount)}</span>
                </button>
            )}

            {/* Cart Modal / Order Panel */}
            {showCart && (
                <div className="cart-overlay">
                    <div className="cart-modal">
                        <div className="cart-header">
                            <h3>🛒 Đơn hàng của bạn</h3>
                            <button className="close-btn" onClick={() => setShowCart(false)}>
                                <X size={24} />
                            </button>
                        </div>

                        <div className="cart-items">
                            {/* 1. New Items in Cart (Chưa gửi) */}
                            {cart.length > 0 && (
                                <div className="cart-section">
                                    <h4 className="cart-section-title text-primary">Món mới (Chưa gửi)</h4>
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
                                    <div className="cart-section-divider"></div>
                                </div>
                            )}

                            {/* 2. Pending Items (Đang chờ xác nhận) */}
                            {pendingItems.length > 0 && (
                                <div className="cart-section">
                                    <h4 className="cart-section-title text-warning">Đang chờ xác nhận</h4>
                                    {pendingItems.map(item => (
                                        <div key={item.id} className="cart-item">
                                            <div className="item-info">
                                                <h4>{item.productName}</h4>
                                                <span className="item-price">{formatPrice(item.total)}</span>
                                                <span className="item-qty-badge">x{item.quantity}</span>
                                            </div>
                                            <button
                                                className="btn-remove-item"
                                                onClick={() => handleRemoveItem(item)}
                                                disabled={actionLoading === item.id}
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    ))}
                                    <div className="cart-section-divider"></div>
                                </div>
                            )}

                            {/* 3. Served/Confirmed Items (Đã đặt) */}
                            {servedItems.length > 0 && (
                                <div className="cart-section">
                                    <h4 className="cart-section-title text-success">Đã xác nhận / Đã ra</h4>
                                    {servedItems.map(item => (
                                        <div key={item.id} className="cart-item">
                                            <div className="item-info">
                                                <h4>{item.productName}</h4>
                                                <span className="item-price">{formatPrice(item.total)}</span>
                                            </div>
                                            <span className="item-qty-display">x{item.quantity}</span>
                                        </div>
                                    ))}
                                </div>
                            )}

                            {cart.length === 0 && pendingItems.length === 0 && servedItems.length === 0 && (
                                <div className="empty-cart-msg">
                                    <Utensils size={48} className="text-muted" />
                                    <p>Bạn chưa gọi món nào</p>
                                </div>
                            )}
                        </div>

                        <div className="cart-footer">
                            <div className="cart-total">
                                <span>Tổng cộng:</span>
                                <span className="amount">{formatPrice(sessionTotal + totalAmount)}</span>
                            </div>

                            {cart.length > 0 ? (
                                <Button
                                    className="checkout-btn"
                                    onClick={handleSubmitOrder}
                                    loading={submitting}
                                >
                                    Gửi gọi món ({cart.length})
                                </Button>
                            ) : (
                                <div className="bill-actions">
                                    {session?.status === 'ACTIVE' && (
                                        <Button
                                            className="btn-payment"
                                            onClick={() => alert('Đã gửi yêu cầu thanh toán đến thu ngân!')}
                                        >
                                            Thanh toán / Gọi Bill
                                        </Button>
                                    )}
                                    <Button
                                        className="close-drawer-btn"
                                        onClick={() => setShowCart(false)}
                                        variant="secondary"
                                    >
                                        Đóng
                                    </Button>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
