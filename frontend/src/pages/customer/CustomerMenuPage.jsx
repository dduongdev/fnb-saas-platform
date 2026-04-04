import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Loading, Button } from '../../components/common';
import { useToast } from '../../context/ToastContext';
import { getPublicMenu, getTableInfo } from '../../api/pos';
import { getTenantDetail } from '../../api/tenant';
import { createCustomerOrder, getCustomerOrderStatus, addCustomerItems, removeCustomerItem } from '../../api/session';
import { getPublicPaymentMethods, requestPayment, createPaymentUrl } from '../../api/payment';
import { usePublicTableWebSocket } from '../../hooks/useWebSocket';
import { formatPrice } from '../../utils/format';
import './CustomerMenuPage.css';

console.log('[CustomerMenuPage] Component loaded, CSS should be imported');

const OTHER_CATEGORY_ID = 'other';

const withDefaultCategory = (categories) => {
    const source = Array.isArray(categories) ? categories : [];
    const cleaned = [];
    const seenKeys = new Set();
    let hasOther = false;

    source.forEach((cat, index) => {
        const idRaw = String(cat?.categoryId ?? '').trim();
        const nameRaw = String(cat?.categoryName ?? '').trim();
        const id = idRaw || `category-${index}`;
        const name = nameRaw || 'Khác';
        const normalizedName = name.toLowerCase();
        const isOther = id === OTHER_CATEGORY_ID || normalizedName === 'khac' || normalizedName === 'khác';

        if (isOther) {
            hasOther = true;
        }

        const dedupeKey = `${id}|${normalizedName}`;
        if (seenKeys.has(dedupeKey)) {
            return;
        }
        seenKeys.add(dedupeKey);

        cleaned.push({
            ...cat,
            categoryId: isOther ? OTHER_CATEGORY_ID : id,
            categoryName: isOther ? 'Khác' : name,
            products: Array.isArray(cat?.products) ? cat.products : [],
        });
    });

    if (!hasOther) {
        cleaned.push({
            categoryId: OTHER_CATEGORY_ID,
            categoryName: 'Khác',
            products: []
        });
    }

    return cleaned;
};

export function CustomerMenuPage() {
    const { tableId, tenantId } = useParams();
    const toast = useToast();
    const isDemoMode = !tableId || tableId === 'demo';
    const [tableInfo, setTableInfo] = useState(null);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeCategory, setActiveCategory] = useState(null);
    const [cart, setCart] = useState([]);
    const [showCart, setShowCart] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Payment State
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [showPaymentModal, setShowPaymentModal] = useState(false);
    const [processingPayment, setProcessingPayment] = useState(false);

    // Product detail modal state
    const [selectedProduct, setSelectedProduct] = useState(null);
    const [showProductModal, setShowProductModal] = useState(false);
    const [productNote, setProductNote] = useState('');
    const [productQty, setProductQty] = useState(1);
    const [searchTerm, setSearchTerm] = useState('');

    // Order status tracking - using same structure as backend SessionResponse
    const [session, setSession] = useState(null); // Full session state from backend
    const [orderView, setOrderView] = useState('menu'); // 'menu' | 'pending' | 'confirmed' | 'rejected' | 'completed'
    const [actionLoading, setActionLoading] = useState(null); // itemId being processed

    // WebSocket: Handle real-time updates - UPDATE STATE DIRECTLY from events
    const handleSessionUpdate = useCallback((data) => {
        console.log('[WS Client] Session full update:', data);
        
        // Khi session COMPLETED, clear session để customer có thể gọi món mới
        if (data.status === 'COMPLETED') {
            console.log('[WS Client] Session COMPLETED - clearing session for new orders');
            setSession(null);
            setOrderView('completed');
            // Show success message briefly then reset to menu
            setTimeout(() => {
                setOrderView('menu');
            }, 5000); // Reset sau 5 giây
            return;
        }
        
        // Normalize data: WebSocket sends SessionResponse format, convert to unified format
        // Extract items from orders[0].items (SessionResponse format)
        const itemsFromOrders = data.orders?.[0]?.items || [];
        const normalizedData = {
            ...data,
            sessionId: data.sessionId || data.id,
            // Set items at root level for easy access
            items: itemsFromOrders,
            // Remove orders to avoid confusion
            orders: undefined,
        };
        
        setSession(normalizedData);

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

        // Backend gửi sessionData (full SessionResponse) để tránh race condition
        // Ưu tiên dùng sessionData nếu có
        if (event.sessionData) {
            console.log('[WS Client] Using sessionData from event:', event.sessionData);
            const data = event.sessionData;
            
            // Extract items from orders[0].items (SessionResponse format)
            const itemsFromOrders = data.orders?.[0]?.items || [];
            console.log('[WS Client] Items extracted:', itemsFromOrders.length, 'items');
            
            const normalizedData = {
                ...data,
                sessionId: data.sessionId || data.id,
                // Set items at root level, clear orders to avoid confusion
                items: itemsFromOrders,
                orders: undefined,
            };
            setSession(normalizedData);
            
            // Update view based on status
            if (data.status === 'ACTIVE') {
                setOrderView('confirmed');
            } else if (data.status === 'CANCELLED') {
                setOrderView('rejected');
            } else if (data.status === 'PENDING') {
                setOrderView('pending');
            } else if (data.status === 'COMPLETED') {
                setSession(null);
                setOrderView('completed');
                setTimeout(() => setOrderView('menu'), 5000);
            }
            return;
        }

        // Fallback: Update state manually from event (legacy)
        setSession(prev => {
            if (!prev) return prev;
            
            // Handle both CustomerOrderResponse (items at root) and SessionResponse (orders[0].items)
            const hasOrdersFormat = prev.orders && prev.orders.length > 0;
            const currentItems = hasOrdersFormat 
                ? [...(prev.orders[0].items || [])]
                : [...(prev.items || [])];

            let newItems = currentItems;

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

            // Return in the same format as received
            if (hasOrdersFormat) {
                return {
                    ...prev,
                    totalAmount: event.newTotalAmount,
                    orders: [{
                        ...prev.orders[0],
                        items: newItems
                    }]
                };
            } else {
                return {
                    ...prev,
                    totalAmount: event.newTotalAmount,
                    items: newItems
                };
            }
        });
    }, []);

    // Handle TABLE_TRANSFERRED event - bàn đã được chuyển sang session khác
    const handleTableTransferred = useCallback((data) => {
        console.log('[WS Client] Table transferred event:', data);
        // Reset về trạng thái mặc định - không còn session nào ở bàn này
        setSession(null);
        setOrderView('menu');
        setCart([]); // Clear cart nếu có
        // Hiển thị thông báo cho customer
        toast.info(data.message || 'Bàn đã được chuyển sang vị trí khác. Vui lòng quét QR để tiếp tục.');
    }, []);

    // State to store effective tenantId from tableInfo
    const [effectiveTenantId, setEffectiveTenantId] = useState(tenantId);

    // Subscribe to table topic for real-time updates (confirm order, payment, etc.)
    usePublicTableWebSocket(
        tableId,
        effectiveTenantId,
        handleSessionUpdate, // onSessionUpdate - receives full session when status changes
        handleItemEvent, // onItemEvent - receives item-level events
        handleTableTransferred // onTableTransferred - receives when table is detached from session
    );

    const fetchCurrentOrder = async (sessionId) => {
        try {
            console.log('[Customer] Fetching session:', sessionId);
            const data = await getCustomerOrderStatus(sessionId);
            console.log('[Customer] Session data received:', data);
            
            // Normalize data - extract items from orders if needed
            const itemsFromOrders = data.orders?.[0]?.items || [];
            const normalizedData = {
                ...data,
                sessionId: data.sessionId || data.id,
                items: data.items || itemsFromOrders,
                orders: undefined, // Remove to avoid confusion
            };
            setSession(normalizedData);

            if (data.status === 'ACTIVE') {
                console.log('[Customer] Session is ACTIVE, showing confirmed view');
                setOrderView('confirmed');
            } else if (data.status === 'CANCELLED') {
                console.log('[Customer] Session is CANCELLED, showing rejected view');
                setOrderView('rejected');
            } else if (data.status === 'PENDING') {
                console.log('[Customer] Session is PENDING, showing pending view');
                setOrderView('pending');
            } else if (data.status === 'COMPLETED') {
                console.log('[Customer] Session is COMPLETED, clearing session');
                setSession(null);
                setOrderView('completed');
                setTimeout(() => setOrderView('menu'), 5000);
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

        // Với scan QR bàn (/table/:tableId), phải gọi loadData() để load table + menu
        if (tenantId || tableId) {
            loadData();
        }

        // Reload table info khi user quay lại trang (để check session existing)
        const handleFocus = async () => {
            if (tableId || tenantId) {
                await loadData();
            }
        };

        window.addEventListener('focus', handleFocus);
        return () => window.removeEventListener('focus', handleFocus);
    }, [tableId, tenantId]);

    const loadPaymentMethods = async (tid) => {
        try {
            const methods = await getPublicPaymentMethods(tid);
            setPaymentMethods(methods || []);
        } catch (error) {
            console.error('Failed to load payment methods', error);
        }
    };

    const loadData = async () => {
        try {
            setLoading(true);

            let resolvedTenantId = tenantId;

            if (!isDemoMode && tableId) {
                // 1. Get table info (includes tenantId needed for subsequent requests)
                const info = await getTableInfo(tableId);
                setTableInfo(info);
                console.log('[Customer] Table info loaded:', info);

                // Set tenant_id from API info if URL param missing
                resolvedTenantId = tenantId || info.tenantId;

                // 2. Check if table has existing session → load it immediately
                if (info.sessionId) {
                    console.log('[Customer] Table has existing session:', info.sessionId);
                    await fetchCurrentOrder(info.sessionId);
                } else {
                    console.log('[Customer] No existing session for this table');
                }
            } else if (tenantId) {
                // Demo route (from /shops list) - no table info call
                const tenant = await getTenantDetail(tenantId);
                setTableInfo({
                    tenantName: tenant.name || 'Menu quán',
                    tableName: 'Xem menu demo',
                });
                console.log('[Customer] Demo mode tenant info loaded:', tenant);
                resolvedTenantId = tenantId;
            }

            if (resolvedTenantId) {
                localStorage.setItem('tenant_id', resolvedTenantId);
                setEffectiveTenantId(resolvedTenantId);
                loadPaymentMethods(resolvedTenantId);
            }

            // 3. Get public menu (tenant-specific)
            const menuData = await getPublicMenu(resolvedTenantId);
            console.log('[Customer] Menu data loaded:', menuData);
            const normalizedMenu = withDefaultCategory(menuData || []);
            setCategories(normalizedMenu);
            if (normalizedMenu.length > 0) {
                setActiveCategory(normalizedMenu[0].categoryId);
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
        if (isDemoMode) {
            return;
        }

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

    const openProductDetail = (product) => {
        setSelectedProduct(product);
        setProductQty(1);
        setProductNote('');
        setShowProductModal(true);
    };

    const closeProductDetail = () => {
        setSelectedProduct(null);
        setProductQty(1);
        setProductNote('');
        setShowProductModal(false);
    };

    const addToCart = (product, options = {}) => {
        if (isDemoMode) {
            toast.warning('Đây là chế độ xem menu demo, không thể đặt hàng.');
            return;
        }

        const quantity = Math.max(1, options.quantity || 1);
        const note = (options.note || '').trim();

        setCart(prev => {
            const existing = prev.find(item => item.productId === product.id && (item.note || '') === note);
            if (existing) {
                return prev.map(item =>
                    item.cartKey === existing.cartKey
                        ? { ...item, quantity: item.quantity + quantity }
                        : item
                );
            }

            return [...prev, {
                cartKey: `${product.id}-${note}-${Date.now()}`,
                productId: product.id,
                productName: product.name,
                price: product.price,
                imageUrl: product.thumbnailUrl,
                quantity,
                note,
            }];
        });
    };

    const addSelectedProductToCart = () => {
        if (!selectedProduct) {
            return;
        }
        addToCart(selectedProduct, { quantity: productQty, note: productNote });
        closeProductDetail();
    };

    const updateQuantity = (cartKey, delta) => {
        setCart(prev => {
            return prev.map(item => {
                if (item.cartKey === cartKey) {
                    return { ...item, quantity: Math.max(0, item.quantity + delta) };
                }
                return item;
            }).filter(item => item.quantity > 0);
        });
    };

    const updateCartNote = (cartKey, note) => {
        setCart(prev => prev.map(item => item.cartKey === cartKey ? { ...item, note } : item));
    };

    const totalAmount = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    const totalItems = cart.reduce((sum, item) => sum + item.quantity, 0);

    const handleSubmitOrder = async () => {
        if (cart.length === 0) return;

        try {
            setSubmitting(true);

            const items = cart.map(item => ({
                productId: item.productId,
                quantity: item.quantity,
                note: item.note || null,
            }));

            // Reload table info để check session mới nhất
            await reloadTableInfo();

            // Check if table already has active session
            if (tableInfo?.hasActiveSession && tableInfo?.sessionId) {
                // Add to existing session
                const result = await addCustomerItems(tableInfo.sessionId, {
                    tableId: tableId,
                    items
                });
                setSession(result);
                setOrderView(result.status === 'ACTIVE' ? 'confirmed' : 'pending');
            } else {
                // Create new pending session
                const result = await createCustomerOrder({
                    tableId: tableId,
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
                toast.info('Bàn này đang có đơn hàng chờ xác nhận. Đang tải đơn hàng...');
                await reloadTableInfo();
            } else {
                toast.error('Đặt món thất bại: ' + error.message);
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
            toast.warning('Không thể xóa món đã mang ra');
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
                toast.warning('Món đã được mang ra, không thể xóa');
                await fetchCurrentOrder(session.sessionId); // Reload to sync
            } else {
                toast.error(error.message);
            }
        } finally {
            setActionLoading(null);
        }
    };

    // Derived data - items đã được normalize ở root level từ handleSessionUpdate/handleItemEvent
    const orderItems = session?.items || [];
    const pendingItems = orderItems.filter(i => i.status === 'PENDING');
    const servedItems = orderItems.filter(i => i.status === 'SERVED');
    const sessionTotal = session?.totalAmount || 0;
    
    // Debug: Log session data khi có vấn đề
    if (session && orderItems.length === 0) {
        console.warn('[Customer] Session exists but no items found:', {
            sessionId: session.sessionId || session.id,
            status: session.status,
            itemsField: session.items,
            raw: session
        });
    }

    const handlePaymentSelect = async (method) => {
        try {
            setProcessingPayment(true);

            if (method.code === 'CASH') {
                await requestPayment(session.sessionId || session.id, tenantId);
                toast.success('Đã gửi yêu cầu thanh toán! Nhân viên sẽ đến ngay.');
                setShowPaymentModal(false);
            } else {
                // Online Payment (VNPAY, MOMO)
                const paymentData = {
                    orderId: session.sessionId || session.id,
                    paymentMethodCode: method.code,
                    amount: session.totalAmount // Optional context
                };

                // Gọi API lấy URL thanh toán
                const paymentUrl = await createPaymentUrl(paymentData, tenantId);

                // Redirect sang cổng thanh toán
                window.location.href = paymentUrl;
            }
        } catch (error) {
            console.error('Payment error:', error);
            toast.error('Lỗi: ' + (error.message || 'Không thể xử lý thanh toán'));
        } finally {
            setProcessingPayment(false);
        }
    };

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

    const activeCategoryData = categories.find(c => c.categoryId === activeCategory);
    const visibleProducts = useMemo(() => {
        const products = activeCategoryData?.products || [];
        const keyword = searchTerm.trim().toLowerCase();
        if (!keyword) {
            return products;
        }
        return products.filter((p) => {
            const name = String(p.name || '').toLowerCase();
            const desc = String(p.description || '').toLowerCase();
            return name.includes(keyword) || desc.includes(keyword);
        });
    }, [activeCategoryData?.products, searchTerm]);

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
                    <p>{tableInfo?.tableName || `Bàn ${tableId}`}</p>
                </div>
                <div className="customer-header-search">
                    <input
                        type="text"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Tìm món nhanh..."
                        className="search-input"
                    />
                </div>
            </header>

            {isDemoMode && (
                <div className="demo-banner">
                    <p>Chế độ xem menu demo: Bạn đang xem menu mà không cần bàn/đơn.</p>
                </div>
            )}

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
                                <span>Đơn hàng đang chờ xác nhận...</span>
                            </div>
                        )}
                        {session.status === 'ACTIVE' && (
                            <div className="status-content">
                                <span>Đơn hàng đã được xác nhận</span>
                            </div>
                        )}
                        {session.status === 'CANCELLED' && (
                            <div className="status-content">
                                <span>Đơn hàng bị từ chối</span>
                            </div>
                        )}
                        {session.status === 'COMPLETED' && (
                            <div className="status-content">
                                <span>Đã thanh toán thành công! Cảm ơn quý khách.</span>
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
                {visibleProducts.map(product => (
                    <div key={product.id} className="product-card" onClick={() => openProductDetail(product)}>
                        <div className="product-img">
                            {product.thumbnailUrl ? (
                                <img src={product.thumbnailUrl} alt={product.name} />
                            ) : (
                                <div className="no-img">No image</div>
                            )}
                        </div>
                        <div className="product-info">
                            <h3>{product.name}</h3>
                            <p className="price">{formatPrice(product.price)}</p>
                            {product.description && (
                                <p className="desc">{product.description}</p>
                            )}
                        </div>
                        <button
                            className="add-btn"
                            onClick={(e) => {
                                e.stopPropagation();
                                addToCart(product, { quantity: 1, note: '' });
                            }}
                        >
                            +
                        </button>
                    </div>
                ))}

                {visibleProducts.length === 0 && (
                    <div className="product-empty">Không tìm thấy món phù hợp</div>
                )}
            </main>

            {/* Product Detail Modal */}
            {showProductModal && selectedProduct && (
                <div className="product-detail-overlay" onClick={closeProductDetail}>
                    <div className="product-detail-modal" onClick={(e) => e.stopPropagation()}>
                        <div className="product-detail-header">
                            <h3>Chi tiết món</h3>
                            <button className="close-btn" onClick={closeProductDetail}>
                                Đóng
                            </button>
                        </div>
                        <div className="product-detail-content">
                            <div className="product-detail-img">
                                {selectedProduct.thumbnailUrl ? (
                                    <img src={selectedProduct.thumbnailUrl} alt={selectedProduct.name} />
                                ) : (
                                    <div className="no-img">No image</div>
                                )}
                            </div>
                            <h4>{selectedProduct.name}</h4>
                            <p className="detail-price">{formatPrice(selectedProduct.price)}</p>
                            <p className="detail-desc">{selectedProduct.description || 'Không có mô tả.'}</p>
                            <div className="detail-qty-row">
                                <span>Số lượng</span>
                                <div className="qty-control">
                                    <button type="button" onClick={() => setProductQty((q) => Math.max(1, q - 1))}>-</button>
                                    <span>{productQty}</span>
                                    <button type="button" onClick={() => setProductQty((q) => q + 1)}>+</button>
                                </div>
                            </div>
                            <label className="note-label" htmlFor="product-note">Ghi chú cho bếp</label>
                            <textarea
                                id="product-note"
                                className="note-input"
                                rows="3"
                                placeholder="Ví dụ: ít cay, không hành..."
                                value={productNote}
                                onChange={(e) => setProductNote(e.target.value)}
                            />
                        </div>
                        <div className="product-detail-actions">
                            <Button onClick={addSelectedProductToCart}>
                                Thêm vào giỏ
                            </Button>
                            <Button variant="secondary" onClick={closeProductDetail}>
                                Đóng
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            {/* Floating Action Button: Cart or Bill */}
            {(cart.length > 0 || (session && orderItems.length > 0)) && !isDemoMode && (
                <button
                    className={`cart-float-btn ${cart.length === 0 ? 'bill-mode' : ''}`}
                    onClick={() => setShowCart(true)}
                >
                    <div className="cart-icon">
                        <span>{cart.length > 0 ? 'Giỏ' : 'Đơn'}</span>
                        <span className="badge">
                            {cart.length > 0 ? totalItems : orderItems.length}
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
                            <h3>Đơn hàng của bạn</h3>
                            <button className="close-btn" onClick={() => setShowCart(false)}>
                                Đóng
                            </button>
                        </div>

                        <div className="cart-items">
                            {/* 1. New Items in Cart (Chưa gửi) */}
                            {cart.length > 0 && (
                                <div className="cart-section">
                                    <h4 className="cart-section-title text-primary">Món mới (Chưa gửi)</h4>
                                    {cart.map(item => (
                                        <div key={item.cartKey} className="cart-item">
                                            <div className="item-info">
                                                <h4>{item.productName}</h4>
                                                <span className="item-price">{formatPrice(item.price)}</span>
                                                <input
                                                    className="cart-note-input"
                                                    type="text"
                                                    value={item.note || ''}
                                                    onChange={(e) => updateCartNote(item.cartKey, e.target.value)}
                                                    placeholder="Ghi chú (tuỳ chọn)"
                                                />
                                            </div>
                                            <div className="qty-control">
                                                <button onClick={() => updateQuantity(item.cartKey, -1)}>
                                                    -
                                                </button>
                                                <span>{item.quantity}</span>
                                                <button onClick={() => updateQuantity(item.cartKey, 1)}>
                                                    +
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
                                                Xóa
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
                                            onClick={() => setShowPaymentModal(true)}
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
            {/* Payment Method Modal */}
            {showPaymentModal && (
                <div className="payment-modal-overlay">
                    <div className="payment-modal">
                        <h3>Chọn phương thức thanh toán</h3>
                        <button className="close-btn" onClick={() => setShowPaymentModal(false)}>
                            Đóng
                        </button>

                        <div className="payment-methods-list">
                            {paymentMethods.map(method => (
                                <button
                                    key={method.code}
                                    className="payment-method-btn"
                                    onClick={() => handlePaymentSelect(method)}
                                    disabled={processingPayment}
                                >
                                    <div className="method-info">
                                        <span className="method-name">{method.name}</span>
                                        <span className="method-desc">
                                            {method.code === 'CASH' ? 'Gọi nhân viên thanh toán' : 'Thanh toán online ngay'}
                                        </span>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
