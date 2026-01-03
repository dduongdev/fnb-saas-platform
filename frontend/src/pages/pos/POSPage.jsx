import { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Plus, Trash2, ArrowRightLeft, Users, Split, Bell, Check, X, Clock, Minus, Coffee, CheckCircle } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Card, Loading, Empty, Modal, ModalFooter, Select, ConfirmModal, Skeleton, CardSkeleton } from '../../components/common';
import { useToast } from '../../context/ToastContext';
import { getTables, getPublicMenu } from '../../api/pos';
import { removeSessionItem, updateSessionItem, getSession, serveItem } from '../../api/session';
import {
    getOrCreateSessionByTable,
    openSession,
    addItemsToSession,
    paySession,
    cancelSession,
    transferTable,
    attachTable,
    detachTable,
    getPendingSessions,
    confirmSession,
    rejectSession
} from '../../api/session';
import { useSessionWebSocket, useSessionByIdWebSocket, usePendingSessionsWebSocket, useMultipleSessionsWebSocket } from '../../hooks/useWebSocket';
import './POSPage.css';

export function POSPage() {
    const toast = useToast();
    const [searchParams] = useSearchParams();
    const initialTableId = searchParams.get('table') ? parseInt(searchParams.get('table')) : null;

    const [tables, setTables] = useState([]);
    const [selectedTableId, setSelectedTableId] = useState(initialTableId);
    const [menu, setMenu] = useState([]);
    const [session, setSession] = useState(null);  // Session thay vì order
    const [loading, setLoading] = useState(true);
    const [sessionLoading, setSessionLoading] = useState(false);
    const [selectedCategory, setSelectedCategory] = useState(null);

    // Loading states for optimistic updates
    const [addingItemId, setAddingItemId] = useState(null);
    const [removingItemId, setRemovingItemId] = useState(null);
    const [updatingItemId, setUpdatingItemId] = useState(null);
    const [servingItemId, setServingItemId] = useState(null);

    // Flag to prevent concurrent loadSession calls
    const loadingSessionRef = useRef(false);

    // Pending sessions state
    const [pendingSessions, setPendingSessions] = useState([]);
    const [showPendingPanel, setShowPendingPanel] = useState(false);
    const [rejectModalSession, setRejectModalSession] = useState(null);
    const [rejectReason, setRejectReason] = useState('');
    const pendingPollingRef = useRef(null);

    // Modals
    const [showPaymentModal, setShowPaymentModal] = useState(false);
    const [showCancelModal, setShowCancelModal] = useState(false);
    const [showTransferModal, setShowTransferModal] = useState(false);
    const [showMergeModal, setShowMergeModal] = useState(false);
    const [showOpenTableModal, setShowOpenTableModal] = useState(false);

    // Add item modal (chọn số lượng)
    const [showAddItemModal, setShowAddItemModal] = useState(false);
    const [selectedProduct, setSelectedProduct] = useState(null);
    const [addQuantity, setAddQuantity] = useState(1);
    const [addNote, setAddNote] = useState('');

    const [paymentLoading, setPaymentLoading] = useState(false);
    const [transferLoading, setTransferLoading] = useState(false);
    const [openTableLoading, setOpenTableLoading] = useState(false);
    const [targetTableId, setTargetTableId] = useState('');
    const [mergeTableIds, setMergeTableIds] = useState([]);
    const [guestCount, setGuestCount] = useState('');
    const [sessionNote, setSessionNote] = useState('');

    useEffect(() => {
        loadInitialData();
    }, []);

    useEffect(() => {
        if (selectedTableId) {
            loadSession(selectedTableId);
        }
    }, [selectedTableId]);

    // WebSocket real-time updates (US-26)
    const handleSessionUpdate = useCallback((data) => {
        console.log('[WS] Session updated:', data);
        setSession(data);
    }, []);

    // Handle item events - backend send toàn bộ session data qua event
    // Không gọi loadSession để tránh race condition
    const handleItemEvent = useCallback((event) => {
        console.log('[WS] Item event:', event.type, event);
        // Backend đã send toàn bộ session data trong event, dùng nó trực tiếp
        if (event.sessionData) {
            setSession(event.sessionData);
        }
    }, []);

    const handlePendingUpdate = useCallback((data) => {
        console.log('[WS POS] Pending sessions updated:', data);
        console.log('[WS POS] Previous count:', pendingSessions.length, 'New count:', data?.length || 0);
        
        // Log chi tiết từng session
        if (data && data.length > 0) {
            data.forEach((sess, idx) => {
                const totalItems = sess.orders?.reduce((sum, ord) => sum + (ord.items?.length || 0), 0) || 0;
                console.log(`[WS POS] Session #${idx + 1}:`, {
                    id: sess.sessionId || sess.id,
                    tableNames: sess.tableNames,
                    status: sess.status,
                    totalItems,
                    total: sess.total
                });
            });
        }
        
        setPendingSessions(data || []);
    }, [pendingSessions.length]);

    // Subscribe to WebSocket for real-time updates (with event handler)
    // Subscribe by tableId for table-based sessions
    useSessionWebSocket(selectedTableId, handleSessionUpdate, handleItemEvent);
    // Subscribe by sessionId for current viewing session
    useSessionByIdWebSocket(session?.id, handleSessionUpdate, handleItemEvent);
    // Subscribe to pending sessions list updates
    usePendingSessionsWebSocket(handlePendingUpdate);
    // Subscribe to ALL pending sessions for realtime item events
    const pendingSessionIds = pendingSessions.map(ps => ps.sessionId || ps.id).filter(Boolean);
    console.log('[POS] Pending session IDs for WS subscription:', pendingSessionIds, 'from', pendingSessions.length, 'sessions');
    useMultipleSessionsWebSocket(pendingSessionIds, handleSessionUpdate, handleItemEvent);

    // Fallback polling for pending sessions (in case WebSocket fails)
    useEffect(() => {
        loadPendingSessions();
        pendingPollingRef.current = setInterval(loadPendingSessions, 30000); // 30s fallback

        return () => {
            if (pendingPollingRef.current) {
                clearInterval(pendingPollingRef.current);
            }
        };
    }, []);

    const loadPendingSessions = async () => {
        try {
            console.log('[POS] Loading pending sessions...');
            const data = await getPendingSessions();
            console.log('[POS] Loaded pending sessions:', data?.length || 0, 'sessions', data);
            const prev = pendingSessions.length;
            setPendingSessions(data || []);
            // Show toast if new pending order arrives
            if (data && data.length > prev && prev > 0) {
                toast.info('Có đơn hàng mới chờ xác nhận!');
            }
        } catch (error) {
            console.error('[POS] Failed to load pending sessions:', error);
        }
    };

    const loadInitialData = async () => {
        try {
            const [tablesData, menuData] = await Promise.all([
                getTables(),
                getPublicMenu()
            ]);
            setTables(tablesData || []);
            setMenu(menuData || []);

            if (menuData && menuData.length > 0) {
                setSelectedCategory(menuData[0].categoryId);
            }

            // Nếu có tableId từ URL params, ưu tiên dùng nó
            if (initialTableId && tablesData.some(t => t.id === initialTableId)) {
                setSelectedTableId(initialTableId);
            } else if (tablesData && tablesData.length > 0) {
                setSelectedTableId(tablesData[0].id);
            }
        } catch (error) {
            console.error('Failed to load data:', error);
            toast.error('Không thể tải dữ liệu: ' + error.message);
        } finally {
            setLoading(false);
        }
    };

    const loadSession = async (tableId, tablesData = null) => {
        // Tránh gọi loadSession đồng thời (race condition)
        if (loadingSessionRef.current) {
            console.log('[loadSession] Already loading, skipping...');
            return;
        }

        loadingSessionRef.current = true;
        try {
            setSessionLoading(true);

            // Nếu đã có session, reload trực tiếp từ sessionId để đảm bảo data mới nhất
            if (session?.sessionId) {
                try {
                    const sessionData = await getSession(session.sessionId);
                    setSession(sessionData);
                    return;
                } catch (err) {
                    // Session có thể đã bị đóng/hủy, tiếp tục fetch từ table
                    console.log('Session may be closed, fetching from table...');
                }
            }

            // Fetch fresh table data từ API để có session info mới nhất
            const freshTables = tablesData || await getTables();
            const table = freshTables.find(t => t.id === tableId);

            if (table?.sessionId) {
                const sessionData = await getSession(table.sessionId);
                setSession(sessionData);
            } else {
                // Bàn trống - không có session
                setSession(null);
            }

            // Update tables state với fresh data
            if (!tablesData) {
                setTables(freshTables);
            }
        } catch (error) {
            console.error('Failed to load session:', error);
            setSession(null);
        } finally {
            setSessionLoading(false);
            loadingSessionRef.current = false;
        }
    };

    // Mở bàn mới (tạo session)
    const handleOpenTable = async () => {
        if (!selectedTableId) return;

        try {
            setOpenTableLoading(true);
            const sessionData = await openSession({
                tableId: selectedTableId,
                guestCount: null,
                note: null
            });
            setSession(sessionData);
            setShowOpenTableModal(false);
            toast.success('Đã mở bàn thành công');

            // Reload tables để cập nhật trạng thái
            const tablesData = await getTables();
            setTables(tablesData || []);
        } catch (error) {
            toast.error(error.message);
        } finally {
            setOpenTableLoading(false);
        }
    };

    // Mở modal chọn số lượng trước khi thêm món
    const openAddItemModal = (product) => {
        if (!selectedTableId) {
            toast.warning('Vui lòng chọn bàn trước');
            return;
        }
        if (!session) {
            toast.warning('Vui lòng mở bàn trước khi thêm món');
            setShowOpenTableModal(true);
            return;
        }

        setSelectedProduct(product);
        setAddQuantity(1);
        setAddNote('');
        setShowAddItemModal(true);
    };

    // Thêm món - KHÔNG dùng optimistic update để tránh bug
    const handleAddItem = async () => {
        if (!selectedProduct || addQuantity < 1) return;

        try {
            setAddingItemId(selectedProduct.id);
            await addItemsToSession(session.sessionId, [{
                productId: selectedProduct.id,
                quantity: addQuantity,
                note: addNote || null
            }]);
            toast.success(`Đã thêm "${selectedProduct.name}" x${addQuantity}`);
            setShowAddItemModal(false);
            setSelectedProduct(null);
            // Reload để đồng bộ với server
            await loadSession(selectedTableId);
        } catch (error) {
            console.error('Failed to add item:', error);
            toast.error(error.message);
        } finally {
            setAddingItemId(null);
        }
    };

    const handleRemoveItem = async (itemId) => {
        if (!session) return;

        // Check item status trước khi xóa
        const item = session.orders?.[0]?.items?.find(i => i.id === itemId);
        if (!item) return;

        if (item.status !== 'PENDING') {
            toast.error('Không thể xóa món đã mang ra');
            return;
        }

        try {
            setRemovingItemId(itemId);
            await removeSessionItem(session.sessionId, itemId);
            toast.success('Đã xóa món');
            // Reload để đồng bộ với server
            await loadSession(selectedTableId);
        } catch (error) {
            if (error.status === 409) {
                toast.error('Món đã được mang ra, không thể xóa');
            } else {
                toast.error(error.message);
            }
            // Reload để đồng bộ state
            await loadSession(selectedTableId);
        } finally {
            setRemovingItemId(null);
        }
    };

    const handleUpdateQuantity = async (itemId, currentQuantity, delta) => {
        if (!session) return;

        // Check item status
        const item = session.orders?.[0]?.items?.find(i => i.id === itemId);
        if (!item || item.status !== 'PENDING') {
            toast.error('Không thể sửa món đã mang ra');
            return;
        }

        const newQuantity = currentQuantity + delta;

        if (newQuantity < 1) {
            await handleRemoveItem(itemId);
            return;
        }

        try {
            setUpdatingItemId(itemId);
            await updateSessionItem(session.sessionId, itemId, newQuantity);
            toast.success('Đã cập nhật số lượng');
            // Reload để đồng bộ
            await loadSession(selectedTableId);
        } catch (error) {
            if (error.status === 409) {
                toast.error('Món đã được mang ra, không thể sửa');
            } else {
                toast.error(error.message);
            }
            await loadSession(selectedTableId);
        } finally {
            setUpdatingItemId(null);
        }
    };

    // Đánh dấu món đã mang ra (SERVE)
    const handleServeItem = async (itemId) => {
        if (!session) return;

        const item = session.orders?.[0]?.items?.find(i => i.id === itemId);
        if (!item || item.status !== 'PENDING') {
            toast.info('Món này đã được mang ra');
            return;
        }

        try {
            setServingItemId(itemId);
            await serveItem(session.sessionId, itemId);
            toast.success(`Đã mang ra: ${item.productName}`);
            // Reload để đồng bộ
            await loadSession(selectedTableId);
        } catch (error) {
            if (error.status === 409) {
                toast.info('Món đã được mang ra rồi');
            } else {
                toast.error(error.message);
            }
            await loadSession(selectedTableId);
        } finally {
            setServingItemId(null);
        }
    };



    const handlePayCash = async () => {
        if (!session) return;

        try {
            setPaymentLoading(true);
            await paySession(session.sessionId, 'CASH');
            setShowPaymentModal(false);
            setSession(null);
            toast.success('Thanh toán thành công!');

            // Reload tables to update status
            const tablesData = await getTables();
            setTables(tablesData || []);

            if (selectedTableId) {
                await loadSession(selectedTableId);
            }
        } catch (error) {
            toast.error(error.message);
        } finally {
            setPaymentLoading(false);
        }
    };

    const handleConfirmCancel = async () => {
        if (!session) return;

        try {
            await cancelSession(session.sessionId);
            // Clear session state immediately
            setSession(null);
            toast.success('Đã hủy phiên phục vụ');
            setShowCancelModal(false);

            // Reload tables to update status
            const tablesData = await getTables();
            setTables(tablesData || []);

            // Force reload from table (not from cached session)
            if (selectedTableId) {
                const table = tablesData.find(t => t.id === selectedTableId);
                if (table?.sessionId) {
                    const sessionData = await getSession(table.sessionId);
                    setSession(sessionData);
                } else {
                    // Table has no session - ensure state is null
                    setSession(null);
                }
            }
        } catch (error) {
            toast.error(error.message);
        }
    };

    const handleTransferSession = async () => {
        if (!session || !targetTableId) return;

        try {
            setTransferLoading(true);

            // 1. Attach bàn mới trước
            await attachTable(session.sessionId, parseInt(targetTableId));

            // 2. Detach tất cả các bàn cũ hiện có trong session
            if (session.tables && session.tables.length > 0) {
                // Sử dụng loop để đảm bảo thứ tự và handle lỗi từng cái nếu cần
                // Lưu ý: session.tables là state cũ, chính là những bàn cần xóa
                for (const table of session.tables) {
                    try {
                        await detachTable(session.sessionId, table.id);
                    } catch (err) {
                        console.error(`Failed to detach table ${table.id}:`, err);
                        // Có thể log nhưng không block process, hoặc throw tùy nghiệp vụ
                        // Ở đây ta tiếp tục để cố gắng detach hết
                    }
                }
            }

            toast.success('Đã chuyển sang bàn mới');
            setShowTransferModal(false);
            setTargetTableId('');

            // Reload data
            const tablesData = await getTables();
            setTables(tablesData || []);

            // Select the target table
            setSelectedTableId(parseInt(targetTableId));
        } catch (error) {
            toast.error(error.message);
        } finally {
            setTransferLoading(false);
        }
    };

    const handleMergeTables = async () => {
        if (!session || mergeTableIds.length === 0) return;

        try {
            // Gộp từng bàn vào session
            for (const tableId of mergeTableIds) {
                await attachTable(session.sessionId, tableId);
            }
            toast.success('Đã gộp bàn thành công');
            setShowMergeModal(false);
            setMergeTableIds([]);

            const tablesData = await getTables();
            setTables(tablesData || []);
            await loadSession(selectedTableId);
        } catch (error) {
            toast.error(error.message);
        }
    };

    const handleReleaseTable = async (tableId) => {
        if (!session) return;

        try {
            await detachTable(session.sessionId, tableId);
            toast.success('Đã tách bàn');

            const tablesData = await getTables();
            setTables(tablesData || []);
            await loadSession(selectedTableId);
        } catch (error) {
            toast.error(error.message);
        }
    };

    const handleConfirmPendingSession = async (sessionId) => {
        try {
            await confirmSession(sessionId);
            toast.success('Đã xác nhận đơn hàng!');
            await loadPendingSessions();
            const tablesData = await getTables();
            setTables(tablesData || []);
            // Reload current session if it's the one we just confirmed
            if (selectedTableId) {
                await loadSession(selectedTableId);
            }
        } catch (error) {
            toast.error(error.message);
        }
    };

    const handleRejectPendingSession = async () => {
        if (!rejectModalSession) return;

        try {
            await rejectSession(rejectModalSession.sessionId, rejectReason || 'Không có lý do');
            toast.success('Đã từ chối đơn hàng');
            setRejectModalSession(null);
            setRejectReason('');
            await loadPendingSessions();
            const tablesData = await getTables();
            setTables(tablesData || []);
        } catch (error) {
            toast.error(error.message);
        }
    };

    const formatPrice = (price) => {
        return new Intl.NumberFormat('vi-VN').format(price) + 'đ';
    };

    // Lọc bỏ món hết hàng (OUT_OF_STOCK)
    const currentCategoryProducts = (menu.find(cat => cat.categoryId === selectedCategory)?.products || [])
        .filter(product => product.status !== 'OUT_OF_STOCK');

    // Lấy order chính từ session
    const currentOrder = session?.orders?.[0];
    const orderItems = currentOrder?.items || [];
    const totalAmount = session?.totalAmount || 0;

    if (loading) return <PageLayout title="Bán hàng"><Loading /></PageLayout>;

    return (
        <PageLayout title="Bán hàng">
            <div className="pos-container">
                {/* Pending Sessions Notification */}
                {pendingSessions.length > 0 && (
                    <div className="pending-notification">
                        <button
                            className="pending-notification-btn"
                            onClick={() => setShowPendingPanel(!showPendingPanel)}
                        >
                            <Bell size={20} />
                            <span className="pending-badge">{pendingSessions.length}</span>
                            <span>Đơn chờ xác nhận</span>
                        </button>

                        {showPendingPanel && (
                            <div className="pending-panel">
                                <div className="pending-panel-header">
                                    <h4>Đơn hàng chờ xác nhận</h4>
                                    <button onClick={() => setShowPendingPanel(false)}>×</button>
                                </div>
                                <div className="pending-panel-list">
                                    {pendingSessions.map(ps => (
                                        <div key={ps.sessionId} className="pending-item">
                                            <div className="pending-item-info">
                                                <div className="pending-item-table">
                                                    <Clock size={14} />
                                                    {ps.tables?.map(t => t.name).join(', ') || 'Không rõ bàn'}
                                                </div>
                                                <div className="pending-item-summary">
                                                    {ps.orders?.[0]?.items?.length || 0} món · {formatPrice(ps.totalAmount || 0)}
                                                </div>
                                                {ps.orders?.[0]?.items && (
                                                    <div className="pending-item-products">
                                                        {ps.orders[0].items.slice(0, 3).map((item, idx) => (
                                                            <span key={idx}>{item.productName} x{item.quantity}</span>
                                                        ))}
                                                        {ps.orders[0].items.length > 3 && (
                                                            <span>... và {ps.orders[0].items.length - 3} món khác</span>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                            <div className="pending-item-actions">
                                                <button
                                                    className="pending-action-btn confirm"
                                                    onClick={() => handleConfirmPendingSession(ps.sessionId)}
                                                    title="Xác nhận"
                                                >
                                                    <Check size={16} />
                                                </button>
                                                <button
                                                    className="pending-action-btn reject"
                                                    onClick={() => setRejectModalSession(ps)}
                                                    title="Từ chối"
                                                >
                                                    <X size={16} />
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}
                {/* Left: Menu */}
                <div className="pos-menu">
                    <Card className="pos-menu-card">
                        <div className="pos-table-select">
                            <Select
                                label="Chọn bàn"
                                value={selectedTableId || ''}
                                onChange={(e) => setSelectedTableId(parseInt(e.target.value))}
                                options={tables.map(t => ({
                                    value: t.id,
                                    label: `${t.name}${t.status === 'OCCUPIED' ? ' (Có khách)' : ''}`
                                }))}
                            />
                        </div>

                        <div className="pos-categories">
                            {menu.map(cat => (
                                <button
                                    key={cat.categoryId}
                                    className={`pos-category-tab ${selectedCategory === cat.categoryId ? 'active' : ''}`}
                                    onClick={() => setSelectedCategory(cat.categoryId)}
                                >
                                    {cat.categoryName}
                                </button>
                            ))}
                        </div>

                        <div className="pos-products">
                            {loading ? (
                                <div className="pos-products-skeleton">
                                    {Array.from({ length: 8 }, (_, i) => (
                                        <div key={i} className="pos-product-skeleton">
                                            <Skeleton variant="rect" height="100px" className="pos-product-skeleton-image" />
                                            <div className="pos-product-skeleton-info">
                                                <Skeleton height="18px" width="80%" />
                                                <Skeleton height="14px" width="50%" />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ) : currentCategoryProducts.length === 0 ? (
                                <Empty message="Không có sản phẩm" />
                            ) : (
                                currentCategoryProducts.map(product => (
                                    <div
                                        key={product.id}
                                        className={`pos-product-card ${addingItemId === product.id ? 'adding' : ''}`}
                                        onClick={() => openAddItemModal(product)}
                                    >
                                        {product.thumbnailUrl ? (
                                            <img src={product.thumbnailUrl} alt={product.name} className="pos-product-image" />
                                        ) : (
                                            <div className="pos-product-no-image">
                                                <Coffee size={32} />
                                            </div>
                                        )}
                                        <div className="pos-product-info">
                                            <span className="pos-product-name">{product.name}</span>
                                            <span className="pos-product-price">{formatPrice(product.price)}</span>
                                        </div>
                                        {addingItemId === product.id && (
                                            <div className="pos-product-adding-overlay">
                                                <span className="pos-product-adding-icon">+</span>
                                            </div>
                                        )}
                                    </div>
                                ))
                            )}
                        </div>
                    </Card>
                </div>

                {/* Right: Order */}
                <div className="pos-order">
                    <Card className="pos-order-card">
                        <h3 className="pos-order-title">
                            Đơn hàng - {tables.find(t => t.id === selectedTableId)?.name || 'Chưa chọn bàn'}
                            {session?.tables?.length > 1 && (
                                <span className="pos-merged-badge">
                                    Gộp {session.tables.length} bàn
                                </span>
                            )}
                        </h3>

                        {/* Hiển thị các bàn trong session */}
                        {session?.tables?.length > 1 && (
                            <div className="pos-session-tables">
                                {session.tables.map(t => (
                                    <span key={t.id} className="pos-session-table-badge">
                                        {t.name}
                                        {session.tables.length > 1 && (
                                            <button
                                                className="pos-session-table-remove"
                                                onClick={() => handleReleaseTable(t.id)}
                                                title="Tách bàn này"
                                            >
                                                ×
                                            </button>
                                        )}
                                    </span>
                                ))}
                            </div>
                        )}

                        {sessionLoading ? (
                            <div className="pos-order-skeleton">
                                {Array.from({ length: 3 }, (_, i) => (
                                    <div key={i} className="pos-order-item-skeleton">
                                        <Skeleton height="24px" width="60%" />
                                        <Skeleton height="18px" width="30%" />
                                    </div>
                                ))}
                                <div className="pos-order-total-skeleton">
                                    <Skeleton height="28px" width="40%" />
                                    <Skeleton height="28px" width="30%" />
                                </div>
                            </div>
                        ) : session ? (
                            <>
                                {orderItems.length > 0 ? (
                                    <>
                                        <div className="pos-order-items">
                                            {orderItems.map(item => (
                                                <div
                                                    key={item.id}
                                                    className={`pos-order-item ${removingItemId === item.id ? 'removing' : ''} ${updatingItemId === item.id ? 'updating' : ''}`}
                                                >
                                                    <div className="pos-order-item-info">
                                                        <span className="pos-order-item-name">{item.productName}</span>
                                                        <div className="pos-order-item-meta">
                                                            <span className="pos-order-item-price">{formatPrice(item.price)}</span>
                                                            {item.createdAt && (
                                                                <span className="pos-order-item-time">
                                                                    <Clock size={12} />
                                                                    {new Date(item.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <div className="pos-order-item-actions">
                                                        {/* Quantity controls - only for PENDING items */}
                                                        {item.status === 'PENDING' && (
                                                            <button
                                                                className="pos-order-item-qty-btn"
                                                                onClick={() => handleUpdateQuantity(item.id, item.quantity, -1)}
                                                                title="Giảm số lượng"
                                                                disabled={updatingItemId === item.id || removingItemId === item.id}
                                                            >
                                                                <Minus size={14} />
                                                            </button>
                                                        )}
                                                        <span className={`pos-order-item-qty ${updatingItemId === item.id ? 'updating' : ''}`}>
                                                            x{item.quantity}
                                                        </span>
                                                        {item.status === 'PENDING' && (
                                                            <button
                                                                className="pos-order-item-qty-btn"
                                                                onClick={() => handleUpdateQuantity(item.id, item.quantity, 1)}
                                                                title="Tăng số lượng"
                                                                disabled={updatingItemId === item.id || removingItemId === item.id}
                                                            >
                                                                <Plus size={14} />
                                                            </button>
                                                        )}
                                                        {item.status === 'PENDING' && (
                                                            <button
                                                                className="pos-order-item-serve"
                                                                onClick={() => handleServeItem(item.id)}
                                                                title="Đánh dấu đã mang ra"
                                                                disabled={servingItemId === item.id}
                                                            >
                                                                {servingItemId === item.id ? (
                                                                    <span className="pos-order-item-serving">...</span>
                                                                ) : (
                                                                    <CheckCircle size={16} />
                                                                )}
                                                            </button>
                                                        )}
                                                        {item.status === 'PENDING' && (
                                                            <button
                                                                className="pos-order-item-remove"
                                                                onClick={() => handleRemoveItem(item.id)}
                                                                title="Xóa món"
                                                                disabled={removingItemId === item.id}
                                                            >
                                                                <Trash2 size={16} />
                                                            </button>
                                                        )}
                                                        {item.status === 'SERVED' && (
                                                            <span className="pos-order-item-status served">
                                                                <Check size={14} /> Đã mang ra
                                                            </span>
                                                        )}
                                                    </div>
                                                    <div className="pos-order-item-subtotal">
                                                        {formatPrice(item.total)}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>

                                        <div className="pos-order-total">
                                            <span>Tổng cộng</span>
                                            <span className="pos-order-total-amount">{formatPrice(totalAmount)}</span>
                                        </div>
                                    </>
                                ) : (
                                    <Empty message="Chưa có món nào" description="Click vào món bên trái để thêm vào đơn" />
                                )}

                                <div className="pos-order-actions">
                                    <Button
                                        variant="secondary"
                                        size="sm"
                                        onClick={() => setShowMergeModal(true)}
                                        title="Gộp thêm bàn"
                                    >
                                        <Users size={16} />
                                        Gộp bàn
                                    </Button>
                                    <Button
                                        variant="secondary"
                                        size="sm"
                                        onClick={() => setShowTransferModal(true)}
                                    >
                                        <ArrowRightLeft size={16} />
                                        Chuyển bàn
                                    </Button>

                                    {/* Hủy đơn: Hiện khi không có item nào HOẶC tất cả item đều PENDING */}
                                    {(orderItems.length === 0 || orderItems.every(item => item.status === 'PENDING')) && (
                                        <Button
                                            variant="danger"
                                            size="lg"
                                            className="pos-btn-cancel"
                                            onClick={() => setShowCancelModal(true)}
                                        >
                                            Hủy đơn
                                        </Button>
                                    )}

                                    {/* Thanh toán: Chỉ hiện khi có ít nhất 1 item là SERVED */}
                                    {orderItems.some(item => item.status === 'SERVED') && (
                                        <Button
                                            size="lg"
                                            className="pos-btn-pay"
                                            onClick={() => setShowPaymentModal(true)}
                                        >
                                            Thanh toán
                                        </Button>
                                    )}
                                </div>
                            </>
                        ) : !session && selectedTableId ? (
                            // Bàn trống - hiển thị nút mở bàn
                            <div className="pos-empty-table">
                                <Empty
                                    message="Bàn đang trống"
                                    description="Nhấn nút bên dưới để mở bàn"
                                />
                                <Button
                                    size="lg"
                                    className="pos-btn-open-table"
                                    onClick={() => setShowOpenTableModal(true)}
                                >
                                    <Plus size={20} />
                                    Mở bàn
                                </Button>
                            </div>
                        ) : (
                            <Empty message="Chưa chọn bàn" description="Chọn một bàn bên trái để bắt đầu" />
                        )}
                    </Card>
                </div>
            </div>

            {/* Payment Modal */}
            <Modal
                isOpen={showPaymentModal}
                onClose={() => setShowPaymentModal(false)}
                title="Xác nhận thanh toán"
            >
                <div className="payment-modal-content">
                    <p className="payment-total-label">Tổng tiền thanh toán:</p>
                    <p className="payment-total-amount">{formatPrice(totalAmount)}</p>
                </div>
                <ModalFooter>
                    <Button variant="secondary" onClick={() => setShowPaymentModal(false)}>
                        Hủy
                    </Button>
                    <Button onClick={handlePayCash} loading={paymentLoading}>
                        Thanh toán tiền mặt
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Cancel Confirmation */}
            <ConfirmModal
                isOpen={showCancelModal}
                onClose={() => setShowCancelModal(false)}
                onConfirm={handleConfirmCancel}
                title="Hủy phiên phục vụ"
                message="Bạn có chắc muốn hủy phiên này?"
                description="Hành động này sẽ xóa toàn bộ món đang chọn và giải phóng bàn."
                variant="danger"
                confirmText="Xác nhận hủy"
            />

            {/* Transfer Session Modal */}
            <Modal
                isOpen={showTransferModal}
                onClose={() => setShowTransferModal(false)}
                title="Chuyển sang bàn khác"
            >
                <div className="transfer-modal-content">
                    <p style={{ marginBottom: '16px', color: 'var(--text-secondary)' }}>
                        Chọn bàn đích để chuyển phiên hiện tại
                    </p>
                    <Select
                        label="Bàn đích"
                        value={targetTableId}
                        onChange={(e) => setTargetTableId(e.target.value)}
                        placeholder="Chọn bàn..."
                        options={tables
                            .filter(t => !session?.tables?.some(st => st.id === t.id) && t.status === 'AVAILABLE')
                            .map(t => ({ value: t.id, label: t.name }))
                        }
                        required
                    />
                    {tables.filter(t => !session?.tables?.some(st => st.id === t.id) && t.status === 'AVAILABLE').length === 0 && (
                        <p style={{ color: 'var(--warning)', fontSize: '14px', marginTop: '8px' }}>
                            Không có bàn trống.
                        </p>
                    )}
                </div>
                <ModalFooter>
                    <Button variant="secondary" onClick={() => setShowTransferModal(false)}>
                        Hủy
                    </Button>
                    <Button
                        onClick={handleTransferSession}
                        loading={transferLoading}
                        disabled={!targetTableId}
                    >
                        Chuyển bàn
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Merge Tables Modal */}
            <Modal
                isOpen={showMergeModal}
                onClose={() => { setShowMergeModal(false); setMergeTableIds([]); }}
                title="Gộp thêm bàn"
            >
                <div className="merge-modal-content">
                    <p style={{ marginBottom: '16px', color: 'var(--text-secondary)' }}>
                        Chọn các bàn muốn gộp vào phiên hiện tại
                    </p>
                    <div className="merge-table-options">
                        {tables
                            .filter(t => !session?.tables?.some(st => st.id === t.id) && t.status === 'AVAILABLE')
                            .map(t => (
                                <label key={t.id} className="merge-table-checkbox">
                                    <input
                                        type="checkbox"
                                        checked={mergeTableIds.includes(t.id)}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                setMergeTableIds([...mergeTableIds, t.id]);
                                            } else {
                                                setMergeTableIds(mergeTableIds.filter(id => id !== t.id));
                                            }
                                        }}
                                    />
                                    <span>{t.name}</span>
                                </label>
                            ))}
                    </div>
                    {tables.filter(t => !session?.tables?.some(st => st.id === t.id) && t.status === 'AVAILABLE').length === 0 && (
                        <p style={{ color: 'var(--warning)', fontSize: '14px', marginTop: '8px' }}>
                            Không có bàn trống để gộp.
                        </p>
                    )}
                </div>
                <ModalFooter>
                    <Button variant="secondary" onClick={() => { setShowMergeModal(false); setMergeTableIds([]); }}>
                        Hủy
                    </Button>
                    <Button
                        onClick={handleMergeTables}
                        disabled={mergeTableIds.length === 0}
                    >
                        Gộp {mergeTableIds.length > 0 ? `(${mergeTableIds.length} bàn)` : ''}
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Add Item Modal - Chọn số lượng */}
            <Modal
                isOpen={showAddItemModal}
                onClose={() => { setShowAddItemModal(false); setSelectedProduct(null); }}
                title="Thêm món"
                size="sm"
            >
                {selectedProduct && (
                    <div className="add-item-modal-content">
                        <div className="add-item-product">
                            {selectedProduct.thumbnailUrl ? (
                                <img src={selectedProduct.thumbnailUrl} alt={selectedProduct.name} className="add-item-image" />
                            ) : (
                                <div className="add-item-no-image">
                                    <Coffee size={40} />
                                </div>
                            )}
                            <div className="add-item-info">
                                <h4>{selectedProduct.name}</h4>
                                <span className="add-item-price">{formatPrice(selectedProduct.price)}</span>
                            </div>
                        </div>

                        <div className="add-item-quantity">
                            <label>Số lượng</label>
                            <div className="quantity-stepper">
                                <button
                                    className="qty-btn"
                                    onClick={() => setAddQuantity(Math.max(1, addQuantity - 1))}
                                    disabled={addQuantity <= 1}
                                >
                                    <Minus size={18} />
                                </button>
                                <span className="qty-value">{addQuantity}</span>
                                <button
                                    className="qty-btn"
                                    onClick={() => setAddQuantity(addQuantity + 1)}
                                >
                                    <Plus size={18} />
                                </button>
                            </div>
                        </div>

                        <div className="add-item-note">
                            <label>Ghi chú (tùy chọn)</label>
                            <input
                                type="text"
                                className="form-input"
                                value={addNote}
                                onChange={(e) => setAddNote(e.target.value)}
                                placeholder="Ít đá, không đường..."
                            />
                        </div>

                        <div className="add-item-total">
                            <span>Thành tiền:</span>
                            <span className="total-value">{formatPrice(selectedProduct.price * addQuantity)}</span>
                        </div>
                    </div>
                )}
                <ModalFooter>
                    <Button
                        variant="secondary"
                        onClick={() => { setShowAddItemModal(false); setSelectedProduct(null); }}
                    >
                        Hủy
                    </Button>
                    <Button
                        onClick={handleAddItem}
                        loading={!!addingItemId}
                        disabled={addQuantity < 1}
                    >
                        Thêm vào đơn
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Reject Pending Session Modal */}
            <Modal
                isOpen={!!rejectModalSession}
                onClose={() => { setRejectModalSession(null); setRejectReason(''); }}
                title="Từ chối đơn hàng"
            >
                <div className="reject-modal-content">
                    <p style={{ marginBottom: '16px', color: 'var(--text-secondary)' }}>
                        Bàn: <strong>{rejectModalSession?.tables?.map(t => t.name).join(', ')}</strong>
                    </p>
                    <div className="form-group">
                        <label>Lý do từ chối</label>
                        <textarea
                            className="form-textarea"
                            value={rejectReason}
                            onChange={(e) => setRejectReason(e.target.value)}
                            placeholder="Nhập lý do từ chối (tùy chọn)..."
                            rows={3}
                        />
                    </div>
                </div>
                <ModalFooter>
                    <Button variant="secondary" onClick={() => { setRejectModalSession(null); setRejectReason(''); }}>
                        Hủy
                    </Button>
                    <Button variant="danger" onClick={handleRejectPendingSession}>
                        Từ chối đơn hàng
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Open Table Modal */}
            <Modal
                isOpen={showOpenTableModal}
                onClose={() => setShowOpenTableModal(false)}
                title={`Mở bàn - ${tables.find(t => t.id === selectedTableId)?.name || ''}`}
                size="sm"
            >
                <div className="open-table-modal-content">
                    <p style={{ textAlign: 'center', color: 'var(--text-secondary)', margin: 'var(--space-4) 0' }}>
                        Xác nhận mở bàn để bắt đầu phục vụ khách?
                    </p>
                </div>
                <ModalFooter>
                    <Button
                        variant="secondary"
                        onClick={() => setShowOpenTableModal(false)}
                    >
                        Hủy
                    </Button>
                    <Button
                        onClick={handleOpenTable}
                        loading={openTableLoading}
                    >
                        Mở bàn
                    </Button>
                </ModalFooter>
            </Modal>
        </PageLayout>
    );
}
