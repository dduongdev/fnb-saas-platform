import { useState, useEffect, useCallback } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { 
    Plus, Minus, Trash2, Check, Clock, Coffee, 
    ShoppingBag, CreditCard, ChevronLeft, ChevronRight,
    UtensilsCrossed
} from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Card, Loading, Empty, Modal, ModalFooter, Skeleton } from '../../components/common';
import { useToast } from '../../context/ToastContext';
import { getPublicMenu } from '../../api/pos';
import { 
    getSession, 
    addItemsToSession, 
    removeSessionItem, 
    updateSessionItem,
    serveItem,
    paySession
} from '../../api/session';
import { useSessionByIdWebSocket } from '../../hooks/useWebSocket';
import { formatPrice } from '../../utils/format';
import './OrderSessionPage.css';

/**
 * OrderSessionPage - Trang gọi món chung cho khách và nhân viên
 * 
 * URL: /session/:sessionId?role=staff|customer
 * 
 * Nghiệp vụ:
 * - Mỗi lần thêm món = tạo 1 OrderItem MỚI (không cộng dồn)
 * - PENDING: có thể sửa số lượng, xóa
 * - SERVED: chỉ đọc
 * - Realtime sync qua WebSocket
 */
export function OrderSessionPage() {
    const { sessionId } = useParams();
    const [searchParams] = useSearchParams();
    const isStaff = searchParams.get('role') === 'staff';
    
    const toast = useToast();
    
    // Data states
    const [session, setSession] = useState(null);
    const [menu, setMenu] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedCategory, setSelectedCategory] = useState(null);
    
    // UI states
    const [showAddItemModal, setShowAddItemModal] = useState(false);
    const [selectedProduct, setSelectedProduct] = useState(null);
    const [addQuantity, setAddQuantity] = useState(1);
    const [addNote, setAddNote] = useState('');
    const [actionLoading, setActionLoading] = useState(null); // itemId being processed
    const [showPaymentModal, setShowPaymentModal] = useState(false);
    const [paymentLoading, setPaymentLoading] = useState(false);

    // Load initial data
    useEffect(() => {
        loadData();
    }, [sessionId]);

    // WebSocket handlers
    const handleSessionUpdate = useCallback((data) => {
        console.log('[WS] Session full update:', data);
        setSession(data);
    }, []);

    const handleItemEvent = useCallback((event) => {
        console.log('[WS] Item event:', event);
        
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

    // Subscribe to WebSocket
    useSessionByIdWebSocket(sessionId ? parseInt(sessionId) : null, handleSessionUpdate, handleItemEvent);

    const loadData = async () => {
        try {
            setLoading(true);
            const [sessionData, menuData] = await Promise.all([
                getSession(sessionId),
                getPublicMenu()
            ]);
            
            setSession(sessionData);
            setMenu(menuData || []);
            
            if (menuData && menuData.length > 0) {
                setSelectedCategory(menuData[0].categoryId);
            }
        } catch (error) {
            console.error('Failed to load data:', error);
            toast.error('Không thể tải dữ liệu: ' + error.message);
        } finally {
            setLoading(false);
        }
    };

    // Mở modal thêm món với stepper số lượng
    const openAddItemModal = (product) => {
        setSelectedProduct(product);
        setAddQuantity(1);
        setAddNote('');
        setShowAddItemModal(true);
    };

    // Thêm món - luôn tạo OrderItem MỚI
    const handleAddItem = async () => {
        if (!selectedProduct || addQuantity < 1) return;

        try {
            setActionLoading('add');
            await addItemsToSession(session.sessionId, [{
                productId: selectedProduct.id,
                quantity: addQuantity,
                note: addNote || null
            }]);
            toast.success(`Đã thêm "${selectedProduct.name}" x${addQuantity}`);
            setShowAddItemModal(false);
            setSelectedProduct(null);
        } catch (error) {
            toast.error(error.message);
            // Reload on error
            await loadData();
        } finally {
            setActionLoading(null);
        }
    };

    // Xóa món - chỉ PENDING
    const handleRemoveItem = async (item) => {
        if (item.status !== 'PENDING') {
            toast.error('Không thể xóa món đã mang ra');
            return;
        }

        try {
            setActionLoading(item.id);
            await removeSessionItem(session.sessionId, item.id);
            toast.success('Đã xóa món');
        } catch (error) {
            if (error.status === 409) {
                toast.error('Món đã được mang ra, không thể xóa');
                await loadData(); // Reload to sync state
            } else {
                toast.error(error.message);
            }
        } finally {
            setActionLoading(null);
        }
    };

    // Cập nhật số lượng - chỉ PENDING
    const handleUpdateQuantity = async (item, delta) => {
        if (item.status !== 'PENDING') {
            toast.error('Không thể sửa món đã mang ra');
            return;
        }

        const newQuantity = item.quantity + delta;
        if (newQuantity < 1) {
            await handleRemoveItem(item);
            return;
        }

        try {
            setActionLoading(item.id);
            await updateSessionItem(session.sessionId, item.id, newQuantity);
            toast.success('Đã cập nhật số lượng');
        } catch (error) {
            if (error.status === 409) {
                toast.error('Món đã được mang ra, không thể sửa');
                await loadData();
            } else {
                toast.error(error.message);
            }
        } finally {
            setActionLoading(null);
        }
    };

    // Đánh dấu món đã mang ra - chỉ staff
    const handleServeItem = async (item) => {
        if (!isStaff) return;
        if (item.status !== 'PENDING') {
            toast.info('Món này đã được mang ra');
            return;
        }

        try {
            setActionLoading(item.id);
            await serveItem(session.sessionId, item.id);
            toast.success(`Đã mang ra: ${item.productName}`);
        } catch (error) {
            if (error.status === 409) {
                toast.info('Món đã được mang ra rồi');
                await loadData();
            } else {
                toast.error(error.message);
            }
        } finally {
            setActionLoading(null);
        }
    };

    // Thanh toán
    const handlePayment = async (method) => {
        try {
            setPaymentLoading(true);
            await paySession(session.sessionId, method);
            toast.success('Thanh toán thành công!');
            setShowPaymentModal(false);
            // Redirect or show success
        } catch (error) {
            toast.error(error.message);
        } finally {
            setPaymentLoading(false);
        }
    };

    // Derived data
    const currentCategoryProducts = menu.find(cat => cat.categoryId === selectedCategory)?.products || [];
    const orderItems = session?.orders?.[0]?.items || [];
    const pendingItems = orderItems.filter(i => i.status === 'PENDING');
    const servedItems = orderItems.filter(i => i.status === 'SERVED');
    const totalAmount = session?.totalAmount || 0;
    const tableName = session?.tables?.map(t => t.name).join(', ') || 'N/A';

    if (loading) {
        return (
            <PageLayout title="Gọi món">
                <div className="order-session-loading">
                    <Loading />
                </div>
            </PageLayout>
        );
    }

    if (!session) {
        return (
            <PageLayout title="Gọi món">
                <Empty 
                    icon={UtensilsCrossed}
                    message="Không tìm thấy phiên" 
                    description="Phiên này không tồn tại hoặc đã kết thúc"
                />
            </PageLayout>
        );
    }

    return (
        <div className="order-session-page">
            {/* Header */}
            <header className="order-session-header">
                <div className="header-info">
                    <h1>{tableName}</h1>
                    <span className={`session-status ${session.status.toLowerCase()}`}>
                        {session.status === 'ACTIVE' ? 'Đang phục vụ' : session.status}
                    </span>
                </div>
                <div className="header-total">
                    <span className="total-label">Tổng tiền</span>
                    <span className="total-amount">{formatPrice(totalAmount)}</span>
                </div>
            </header>

            {/* Main Content */}
            <div className="order-session-content">
                {/* Left: Menu */}
                <section className="menu-section">
                    {/* Category Tabs */}
                    <div className="category-tabs">
                        {menu.map(cat => (
                            <button
                                key={cat.categoryId}
                                className={`category-tab ${selectedCategory === cat.categoryId ? 'active' : ''}`}
                                onClick={() => setSelectedCategory(cat.categoryId)}
                            >
                                {cat.categoryName}
                            </button>
                        ))}
                    </div>

                    {/* Product Grid */}
                    <div className="product-grid">
                        {currentCategoryProducts.length === 0 ? (
                            <Empty 
                                icon={Coffee}
                                message="Không có món" 
                                description="Danh mục này chưa có món nào"
                            />
                        ) : (
                            currentCategoryProducts.map(product => (
                                <button
                                    key={product.id}
                                    className="product-card"
                                    onClick={() => openAddItemModal(product)}
                                    disabled={product.status === 'OUT_OF_STOCK'}
                                >
                                    <div className="product-image">
                                        {product.thumbnailUrl ? (
                                            <img src={product.thumbnailUrl} alt={product.name} />
                                        ) : (
                                            <Coffee size={32} />
                                        )}
                                        {product.status === 'OUT_OF_STOCK' && (
                                            <div className="out-of-stock-overlay">Hết hàng</div>
                                        )}
                                    </div>
                                    <div className="product-info">
                                        <h4>{product.name}</h4>
                                        <p className="product-price">{formatPrice(product.price)}</p>
                                    </div>
                                </button>
                            ))
                        )}
                    </div>
                </section>

                {/* Right: Order Items */}
                <section className="order-section">
                    <div className="order-header">
                        <ShoppingBag size={20} />
                        <h2>Đơn hàng</h2>
                        <span className="item-count">{orderItems.length} món</span>
                    </div>

                    <div className="order-items-container">
                        {orderItems.length === 0 ? (
                            <div className="empty-order">
                                <UtensilsCrossed size={48} />
                                <p>Chưa có món nào</p>
                                <span>Chọn món từ menu bên trái</span>
                            </div>
                        ) : (
                            <>
                                {/* Pending Items */}
                                {pendingItems.length > 0 && (
                                    <div className="items-group">
                                        <div className="group-header pending">
                                            <Clock size={16} />
                                            <span>Chờ mang ra ({pendingItems.length})</span>
                                        </div>
                                        {pendingItems.map(item => (
                                            <OrderItemRow
                                                key={item.id}
                                                item={item}
                                                isStaff={isStaff}
                                                loading={actionLoading === item.id}
                                                onUpdateQuantity={(delta) => handleUpdateQuantity(item, delta)}
                                                onRemove={() => handleRemoveItem(item)}
                                                onServe={() => handleServeItem(item)}
                                            />
                                        ))}
                                    </div>
                                )}

                                {/* Served Items */}
                                {servedItems.length > 0 && (
                                    <div className="items-group">
                                        <div className="group-header served">
                                            <Check size={16} />
                                            <span>Đã mang ra ({servedItems.length})</span>
                                        </div>
                                        {servedItems.map(item => (
                                            <OrderItemRow
                                                key={item.id}
                                                item={item}
                                                isStaff={isStaff}
                                                loading={false}
                                                readOnly
                                            />
                                        ))}
                                    </div>
                                )}
                            </>
                        )}
                    </div>

                    {/* Order Footer */}
                    <div className="order-footer">
                        <div className="order-summary">
                            <span>Tổng cộng</span>
                            <span className="summary-total">{formatPrice(totalAmount)}</span>
                        </div>
                        {isStaff && (
                            <Button 
                                className="pay-button"
                                onClick={() => setShowPaymentModal(true)}
                                disabled={orderItems.length === 0}
                            >
                                <CreditCard size={18} />
                                Thanh toán
                            </Button>
                        )}
                    </div>
                </section>
            </div>

            {/* Add Item Modal */}
            <Modal
                isOpen={showAddItemModal}
                onClose={() => setShowAddItemModal(false)}
                title="Thêm món"
                size="sm"
            >
                {selectedProduct && (
                    <div className="add-item-modal">
                        <div className="product-preview">
                            {selectedProduct.thumbnailUrl ? (
                                <img src={selectedProduct.thumbnailUrl} alt={selectedProduct.name} />
                            ) : (
                                <div className="no-image"><Coffee size={40} /></div>
                            )}
                            <div className="product-details">
                                <h3>{selectedProduct.name}</h3>
                                <p className="price">{formatPrice(selectedProduct.price)}</p>
                            </div>
                        </div>

                        <div className="quantity-selector">
                            <label>Số lượng</label>
                            <div className="quantity-controls">
                                <button 
                                    onClick={() => setAddQuantity(q => Math.max(1, q - 1))}
                                    disabled={addQuantity <= 1}
                                >
                                    <Minus size={20} />
                                </button>
                                <span className="quantity-value">{addQuantity}</span>
                                <button onClick={() => setAddQuantity(q => q + 1)}>
                                    <Plus size={20} />
                                </button>
                            </div>
                        </div>

                        <div className="note-input">
                            <label>Ghi chú (tuỳ chọn)</label>
                            <textarea
                                value={addNote}
                                onChange={(e) => setAddNote(e.target.value)}
                                placeholder="Ví dụ: Ít đá, không đường..."
                                rows={2}
                            />
                        </div>

                        <div className="item-total">
                            <span>Thành tiền</span>
                            <span>{formatPrice(selectedProduct.price * addQuantity)}</span>
                        </div>
                    </div>
                )}
                <ModalFooter>
                    <Button variant="secondary" onClick={() => setShowAddItemModal(false)}>
                        Hủy
                    </Button>
                    <Button 
                        onClick={handleAddItem}
                        loading={actionLoading === 'add'}
                    >
                        <Plus size={18} />
                        Thêm vào đơn
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Payment Modal */}
            <Modal
                isOpen={showPaymentModal}
                onClose={() => setShowPaymentModal(false)}
                title="Thanh toán"
                size="sm"
            >
                <div className="payment-modal">
                    <div className="payment-summary">
                        <span>Tổng tiền thanh toán</span>
                        <span className="payment-total">{formatPrice(totalAmount)}</span>
                    </div>
                    <div className="payment-methods">
                        <Button 
                            className="payment-method-btn"
                            onClick={() => handlePayment('CASH')}
                            loading={paymentLoading}
                        >
                            💵 Tiền mặt
                        </Button>
                        <Button 
                            className="payment-method-btn"
                            variant="secondary"
                            onClick={() => handlePayment('VNPAY')}
                            loading={paymentLoading}
                        >
                            🏦 VNPay
                        </Button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}

/**
 * OrderItemRow Component
 */
function OrderItemRow({ item, isStaff, loading, readOnly, onUpdateQuantity, onRemove, onServe }) {
    const isPending = item.status === 'PENDING';

    return (
        <div className={`order-item-row ${readOnly ? 'read-only' : ''} ${loading ? 'loading' : ''}`}>
            <div className="item-image">
                {item.productImage ? (
                    <img src={item.productImage} alt={item.productName} />
                ) : (
                    <Coffee size={20} />
                )}
            </div>
            
            <div className="item-details">
                <h4>{item.productName}</h4>
                {item.note && <p className="item-note">{item.note}</p>}
                <p className="item-price">{formatPrice(item.price)} × {item.quantity}</p>
            </div>

            <div className="item-total">
                {formatPrice(item.total)}
            </div>

            {!readOnly && isPending && (
                <div className="item-actions">
                    {/* Quantity controls */}
                    <div className="quantity-mini">
                        <button 
                            onClick={() => onUpdateQuantity(-1)}
                            disabled={loading}
                        >
                            <Minus size={14} />
                        </button>
                        <span>{item.quantity}</span>
                        <button 
                            onClick={() => onUpdateQuantity(1)}
                            disabled={loading}
                        >
                            <Plus size={14} />
                        </button>
                    </div>

                    {/* Delete button */}
                    <button 
                        className="delete-btn"
                        onClick={onRemove}
                        disabled={loading}
                        title="Xóa món"
                    >
                        <Trash2 size={16} />
                    </button>

                    {/* Serve button (staff only) */}
                    {isStaff && (
                        <button 
                            className="serve-btn"
                            onClick={onServe}
                            disabled={loading}
                            title="Đánh dấu đã mang ra"
                        >
                            <Check size={16} />
                        </button>
                    )}
                </div>
            )}

            {readOnly && (
                <div className="served-badge">
                    <Check size={14} />
                    Đã mang
                </div>
            )}
        </div>
    );
}
