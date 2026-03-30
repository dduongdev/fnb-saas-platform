import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, Users, DollarSign, Table2, Eye, CreditCard, X, Bell, Plus, ArrowRightLeft, UserPlus } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Card, Loading, Empty, Input, StatusBadge, Modal, ModalFooter, Skeleton } from '../../components/common';
import { useToast } from '../../context/ToastContext';
import { usePendingSessionsWebSocket } from '../../hooks/useWebSocket';
import {
    getActiveSessions,
    getPendingSessions,
    getSessionHistory,
    confirmSession,
    rejectSession,
    paySession,
    openSession,
    attachTable,
    detachTable
} from '../../api/session';
import { getTables } from '../../api/pos';
import { formatPrice } from '../../utils/format';
import './SessionListPage.css';

export function SessionListPage() {
    const navigate = useNavigate();
    const toast = useToast();
    
    const [activeSessions, setActiveSessions] = useState([]);
    const [pendingSessions, setPendingSessions] = useState([]);
    const [historySessions, setHistorySessions] = useState([]);
    const [historyPage, setHistoryPage] = useState(0);
    const [historySize, setHistorySize] = useState(20);
    const [historyTotalPages, setHistoryTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState('active'); // 'active' | 'pending' | 'history'
    
    // Modals
    const [selectedSession, setSelectedSession] = useState(null);
    const [showDetailModal, setShowDetailModal] = useState(false);
    const [showRejectModal, setShowRejectModal] = useState(false);
    const [rejectReason, setRejectReason] = useState('');
    const [actionLoading, setActionLoading] = useState(false);
    
    // Open new session modal
    const [showOpenSessionModal, setShowOpenSessionModal] = useState(false);
    const [availableTables, setAvailableTables] = useState([]);
    const [selectedTableId, setSelectedTableId] = useState(null);
    const [openSessionLoading, setOpenSessionLoading] = useState(false);
    const [tablesLoading, setTablesLoading] = useState(false);

    // Attach table modal (thêm bàn vào phiên)
    const [showAttachModal, setShowAttachModal] = useState(false);
    const [attachSession, setAttachSession] = useState(null);
    const [attachTableId, setAttachTableId] = useState(null);
    const [attachLoading, setAttachLoading] = useState(false);

    // Transfer table modal (chuyển bàn)
    const [showTransferModal, setShowTransferModal] = useState(false);
    const [transferSession, setTransferSession] = useState(null);
    const [sourceTableId, setSourceTableId] = useState(null);
    const [targetTableId, setTargetTableId] = useState(null);
    const [transferLoading, setTransferLoading] = useState(false);

    useEffect(() => {
        loadSessions();
        loadHistory();
    }, []);

    useEffect(() => {
        if (activeTab === 'history') {
            loadHistory();
        }
    }, [activeTab, historyPage, historySize]);

    // WebSocket for real-time pending sessions
    const handlePendingUpdate = useCallback((data) => {
        console.log('[WS] Pending sessions updated:', data);
        setPendingSessions(data || []);
    }, []);

    usePendingSessionsWebSocket(handlePendingUpdate);

    const loadSessions = async () => {
        try {
            setLoading(true);
            const [active, pending] = await Promise.all([
                getActiveSessions(),
                getPendingSessions()
            ]);
            setActiveSessions(active || []);
            setPendingSessions(pending || []);
        } catch (error) {
            console.error('Failed to load sessions:', error);
            toast.error('Không thể tải danh sách phiên');
        } finally {
            setLoading(false);
        }
    };

    const loadHistory = async () => {
        try {
            setHistoryLoading(true);
            const data = await getSessionHistory(historyPage, historySize);
            setHistorySessions(data.content || []);
            setHistoryTotalPages(data.totalPages || 0);
        } catch (error) {
            console.error('Failed to load session history:', error);
            toast.error('Không thể tải lịch sử session');
        } finally {
            setHistoryLoading(false);
        }
    };

    const handleViewSession = (session) => {
        const tableId = session.tables && session.tables[0]?.id;
        if (tableId) {
            navigate(`/pos?table=${tableId}`);
        } else {
            navigate('/pos');
        }
    };

    const handleConfirmSession = async (session) => {
        try {
            setActionLoading(true);
            await confirmSession(session.sessionId);
            toast.success('Đã xác nhận đơn hàng!');
            await loadSessions();
        } catch (error) {
            toast.error(error.message);
        } finally {
            setActionLoading(false);
        }
    };

    const handleRejectSession = async () => {
        if (!selectedSession) return;
        
        try {
            setActionLoading(true);
            await rejectSession(selectedSession.sessionId, rejectReason || 'Không có lý do');
            toast.success('Đã từ chối đơn hàng');
            setShowRejectModal(false);
            setSelectedSession(null);
            setRejectReason('');
            await loadSessions();
        } catch (error) {
            toast.error(error.message);
        } finally {
            setActionLoading(false);
        }
    };

    // Load available tables when opening modal
    const handleOpenSessionModal = async () => {
        setShowOpenSessionModal(true);
        setTablesLoading(true);
        try {
            const tables = await getTables();
            // Filter only available tables (no active session)
            const available = (tables || []).filter(t => t.status === 'AVAILABLE' && !t.sessionId);
            setAvailableTables(available);
        } catch (error) {
            toast.error('Không thể tải danh sách bàn');
        } finally {
            setTablesLoading(false);
        }
    };

    const handleOpenSession = async () => {
        if (!selectedTableId) {
            toast.warning('Vui lòng chọn bàn');
            return;
        }

        try {
            setOpenSessionLoading(true);
            await openSession({
                tableId: selectedTableId,
                note: null
            });
            toast.success('Đã mở phiên mới!');
            setShowOpenSessionModal(false);
            resetOpenSessionForm();
            await loadSessions();
        } catch (error) {
            toast.error(error.message);
        } finally {
            setOpenSessionLoading(false);
        }
    };

    const resetOpenSessionForm = () => {
        setSelectedTableId(null);
        setAvailableTables([]);
    };

    // ==================== Attach Table (Thêm bàn) ====================
    const handleOpenAttachModal = async (session) => {
        setAttachSession(session);
        setShowAttachModal(true);
        setTablesLoading(true);
        try {
            const tables = await getTables();
            // Lọc bàn trống (không có session)
            const available = (tables || []).filter(t => !t.sessionId);
            setAvailableTables(available);
        } catch (error) {
            toast.error('Không thể tải danh sách bàn');
        } finally {
            setTablesLoading(false);
        }
    };

    const handleAttachTable = async () => {
        if (!attachSession || !attachTableId) {
            toast.warning('Vui lòng chọn bàn');
            return;
        }

        try {
            setAttachLoading(true);
            await attachTable(attachSession.sessionId, attachTableId);
            toast.success('Đã thêm bàn vào phiên!');
            setShowAttachModal(false);
            resetAttachForm();
            await loadSessions();
        } catch (error) {
            toast.error(error.message);
        } finally {
            setAttachLoading(false);
        }
    };

    const resetAttachForm = () => {
        setAttachSession(null);
        setAttachTableId(null);
        setAvailableTables([]);
    };

    // ==================== Transfer Table (Chuyển bàn) ====================
    const handleOpenTransferModal = async (session) => {
        // Kiểm tra phiên phải có ít nhất 1 bàn
        if (!session.tables || session.tables.length === 0) {
            toast.error('Phiên này không có bàn để chuyển');
            return;
        }

        setTransferSession(session);
        setSourceTableId(session.tables[0]?.id); // Mặc định chọn bàn đầu tiên
        setShowTransferModal(true);
        setTablesLoading(true);
        try {
            const tables = await getTables();
            // Lọc bàn trống (không có session)
            const available = (tables || []).filter(t => !t.sessionId);
            setAvailableTables(available);
        } catch (error) {
            toast.error('Không thể tải danh sách bàn');
        } finally {
            setTablesLoading(false);
        }
    };

    const handleTransferTable = async () => {
        if (!transferSession || !sourceTableId || !targetTableId) {
            toast.warning('Vui lòng chọn bàn nguồn và bàn đích');
            return;
        }

        // Kiểm tra phiên có >= 1 bàn sau khi chuyển
        if (transferSession.tables.length <= 1) {
            // Nếu chỉ có 1 bàn, thực hiện attach trước rồi detach sau
            try {
                setTransferLoading(true);
                await attachTable(transferSession.sessionId, targetTableId);
                await detachTable(transferSession.sessionId, sourceTableId);
                toast.success('Đã chuyển bàn thành công!');
                setShowTransferModal(false);
                resetTransferForm();
                await loadSessions();
            } catch (error) {
                toast.error(error.message);
            } finally {
                setTransferLoading(false);
            }
        } else {
            // Phiên có nhiều bàn, có thể detach bình thường
            try {
                setTransferLoading(true);
                await attachTable(transferSession.sessionId, targetTableId);
                await detachTable(transferSession.sessionId, sourceTableId);
                toast.success('Đã chuyển bàn thành công!');
                setShowTransferModal(false);
                resetTransferForm();
                await loadSessions();
            } catch (error) {
                toast.error(error.message);
            } finally {
                setTransferLoading(false);
            }
        }
    };

    const resetTransferForm = () => {
        setTransferSession(null);
        setSourceTableId(null);
        setTargetTableId(null);
        setAvailableTables([]);
    };

    const formatTime = (dateStr) => {
        if (!dateStr) return '--:--';
        const date = new Date(dateStr);
        return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    };

    const formatDuration = (startDateStr) => {
        if (!startDateStr) return '--';
        const start = new Date(startDateStr);
        const now = new Date();
        const diffMs = now - start;
        const diffMins = Math.floor(diffMs / 60000);
        
        if (diffMins < 60) {
            return `${diffMins} phút`;
        }
        const hours = Math.floor(diffMins / 60);
        const mins = diffMins % 60;
        return `${hours}h ${mins}p`;
    };

    const getStatusColor = (status) => {
        switch (status) {
            case 'ACTIVE': return 'success';
            case 'PENDING': return 'warning';
            case 'COMPLETED': return 'info';
            case 'CANCELLED': return 'danger';
            default: return 'default';
        }
    };

    const [historyLoading, setHistoryLoading] = useState(false);
    const [historyFilterStatus, setHistoryFilterStatus] = useState('');
    const [historySearchTerm, setHistorySearchTerm] = useState('');

    const currentSessions = activeTab === 'active'
        ? activeSessions
        : activeTab === 'pending'
            ? pendingSessions
            : historySessions;

    if (loading) {
        return (
            <PageLayout title="Quản lý phiên phục vụ">
                <div className="session-list-skeleton">
                    {Array.from({ length: 4 }, (_, i) => (
                        <div key={i} className="session-card-skeleton">
                            <Skeleton height="24px" width="40%" />
                            <Skeleton height="18px" width="60%" />
                            <Skeleton height="18px" width="30%" />
                        </div>
                    ))}
                </div>
            </PageLayout>
        );
    }

    return (
        <PageLayout
            title="Quản lý phiên phục vụ"
            actions={
                <div className="session-page-actions">
                    <Button onClick={handleOpenSessionModal}>
                        Mở phiên mới
                    </Button>
                    <Button onClick={loadSessions} variant="secondary">
                        Làm mới
                    </Button>
                </div>
            }
        >
            {/* Tabs */}
            <div className="session-tabs">
                <button
                    className={`session-tab ${activeTab === 'active' ? 'active' : ''}`}
                    onClick={() => setActiveTab('active')}
                >
                    <Users size={18} />
                    Đang phục vụ
                    {activeSessions.length > 0 && (
                        <span className="tab-badge">{activeSessions.length}</span>
                    )}
                </button>
                <button
                    className={`session-tab ${activeTab === 'pending' ? 'active' : ''}`}
                    onClick={() => setActiveTab('pending')}
                >
                    <Bell size={18} />
                    Chờ xác nhận
                    {pendingSessions.length > 0 && (
                        <span className="tab-badge warning">{pendingSessions.length}</span>
                    )}
                </button>
                <button
                    className={`session-tab ${activeTab === 'history' ? 'active' : ''}`}
                    onClick={() => setActiveTab('history')}
                >
                    <Clock size={18} />
                    Lịch sử
                </button>
            </div>

            {/* Session List */}
            {currentSessions.length === 0 ? (
                <Empty
                    icon={activeTab === 'active' ? Users : activeTab === 'pending' ? Bell : Clock}
                    message={activeTab === 'active' ? 'Không có phiên đang hoạt động' : activeTab === 'pending' ? 'Không có đơn chờ xác nhận' : 'Không có lịch sử'}
                    description={activeTab === 'active' 
                        ? 'Các phiên phục vụ sẽ hiển thị tại đây'
                        : activeTab === 'pending'
                            ? 'Đơn hàng từ khách quét QR sẽ hiển thị tại đây'
                            : 'Bạn có thể xem lại tất cả session đã dịch vụ ở đây'
                    }
                />
            ) : activeTab === 'history' ? (
                <div>
                    {historyLoading ? (
                        <Loading />
                    ) : (
                        <div className="session-history-panel">
                            <div className="session-history-actions">
                                <div className="session-history-filter-group">
                                    <Input
                                        placeholder="Tìm theo ID/Bàn/..."
                                        value={historySearchTerm}
                                        onChange={(e) => setHistorySearchTerm(e.target.value)}
                                    />
                                    <select
                                        value={historyFilterStatus}
                                        onChange={(e) => setHistoryFilterStatus(e.target.value)}
                                    >
                                        <option value="">Tất cả trạng thái</option>
                                        <option value="ACTIVE">ACTIVE</option>
                                        <option value="PENDING">PENDING</option>
                                        <option value="COMPLETED">COMPLETED</option>
                                        <option value="CANCELLED">CANCELLED</option>
                                    </select>
                                    <Button onClick={() => { setHistoryPage(0); loadHistory(); }}>Lọc</Button>
                                </div>

                                <div className="session-history-meta">
                                    <span>{historySessions.length} phiên lịch sử</span>
                                    <span>Trang {historyPage + 1} / {historyTotalPages || 1}</span>
                                </div>
                            </div>

                            <div className="session-history-table-wrapper">
                                <table className="session-history-table">
                                    <thead>
                                        <tr>
                                            <th>Session ID</th>
                                            <th>Trạng thái</th>
                                            <th>Bàn</th>
                                            <th>Bắt đầu</th>
                                            <th>Kết thúc</th>
                                            <th>Tổng</th>
                                            <th>Hành động</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {(historySessions.filter(item => {
                                            const matchStatus = !historyFilterStatus || item.status === historyFilterStatus;
                                            const text = historySearchTerm.trim().toLowerCase();
                                            const matchText = !text ||
                                                item.sessionId?.toString().includes(text) ||
                                                item.tables?.some(t => t.name.toLowerCase().includes(text));
                                            return matchStatus && matchText;
                                        }).length === 0) ? (
                                            <tr><td colSpan={7} style={{ textAlign: 'center' }}>Không có lịch sử</td></tr>
                                        ) : historySessions
                                            .filter(item => {
                                                const matchStatus = !historyFilterStatus || item.status === historyFilterStatus;
                                                const text = historySearchTerm.trim().toLowerCase();
                                                const matchText = !text ||
                                                    item.sessionId?.toString().includes(text) ||
                                                    item.tables?.some(t => t.name.toLowerCase().includes(text));
                                                return matchStatus && matchText;
                                            })
                                            .map((item) => (
                                                <tr key={item.sessionId} className={`status-${item.status.toLowerCase()}`}>
                                                    <td>{item.sessionId}</td>
                                                    <td className="status-cell">{item.status}</td>
                                                    <td>{item.tables?.map(t => t.name).join(', ') || '-'}</td>
                                                    <td>{item.startedAt ? new Date(item.startedAt).toLocaleString('vi-VN') : '-'}</td>
                                                    <td>{item.endedAt ? new Date(item.endedAt).toLocaleString('vi-VN') : '-'}</td>
                                                    <td>{item.totalAmount ? new Intl.NumberFormat('vi-VN').format(item.totalAmount) + 'đ' : '-'}</td>
                                                    <td>
                                                        <Button size="sm" variant="primary" onClick={() => navigate(`/sessions/${item.sessionId}`)}>Xem</Button>
                                                    </td>
                                                </tr>
                                            ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
                        <Button onClick={() => setHistoryPage(prev => Math.max(prev - 1, 0))} disabled={historyPage <= 0}>Trước</Button>
                        <span>Trang {historyPage + 1}/{historyTotalPages || 1}</span>
                        <Button onClick={() => setHistoryPage(prev => Math.min(prev + 1, Math.max(historyTotalPages - 1, 0)))} disabled={historyPage >= historyTotalPages - 1}>Sau</Button>
                        <select value={historySize} onChange={e => setHistorySize(Number(e.target.value))}>
                            <option value={10}>10</option>
                            <option value={20}>20</option>
                            <option value={50}>50</option>
                        </select>
                    </div>
                </div>
            ) : (
                <div className="session-grid">
                    {currentSessions.map(session => (
                        <Card key={session.sessionId} className="session-card">
                            <div className="session-card-header">
                                <div className="session-tables">
                                    <Table2 size={18} />
                                    <span>{session.tables?.map(t => t.name).join(', ') || 'Không rõ bàn'}</span>
                                </div>
                                <StatusBadge 
                                    status={session.status} 
                                    variant={getStatusColor(session.status)}
                                />
                            </div>

                            <div className="session-card-body">
                                <div className="session-info-row">
                                    <Clock size={16} />
                                    <span>Bắt đầu: {formatTime(session.startedAt)}</span>
                                    <span className="session-duration">({formatDuration(session.startedAt)})</span>
                                </div>


                                <div className="session-info-row">
                                    <DollarSign size={16} />
                                    <span className="session-total">{formatPrice(session.totalAmount || 0)}</span>
                                </div>

                                {/* Order items preview */}
                                {session.orders?.[0]?.items?.length > 0 && (
                                    <div className="session-items-preview">
                                        {session.orders[0].items.slice(0, 3).map((item, idx) => (
                                            <span key={idx} className="item-badge">
                                                {item.productName} x{item.quantity}
                                            </span>
                                        ))}
                                        {session.orders[0].items.length > 3 && (
                                            <span className="item-badge more">
                                                +{session.orders[0].items.length - 3} món
                                            </span>
                                        )}
                                    </div>
                                )}
                            </div>

                            <div className="session-card-actions">
                                {activeTab === 'active' ? (
                                    <>
                                        <Button
                                            size="sm"
                                            variant="secondary"
                                            onClick={() => handleViewSession(session)}
                                        >
                                            <Eye size={16} />
                                            Xem
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="secondary"
                                            onClick={() => handleOpenAttachModal(session)}
                                            title="Thêm bàn vào phiên"
                                        >
                                            <UserPlus size={16} />
                                            Thêm bàn
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="secondary"
                                            onClick={() => handleOpenTransferModal(session)}
                                            title="Chuyển bàn"
                                        >
                                            <ArrowRightLeft size={16} />
                                            Chuyển
                                        </Button>
                                    </>
                                ) : (
                                    <>
                                        <Button
                                            size="sm"
                                            variant="success"
                                            onClick={() => handleConfirmSession(session)}
                                            loading={actionLoading}
                                        >
                                            Xác nhận
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="danger"
                                            onClick={() => {
                                                setSelectedSession(session);
                                                setShowRejectModal(true);
                                            }}
                                        >
                                            Từ chối
                                        </Button>
                                    </>
                                )}
                            </div>
                        </Card>
                    ))}
                </div>
            )}

            {/* Reject Modal */}
            <Modal
                isOpen={showRejectModal}
                onClose={() => {
                    setShowRejectModal(false);
                    setSelectedSession(null);
                    setRejectReason('');
                }}
                title="Từ chối đơn hàng"
            >
                <div className="reject-modal-content">
                    <p style={{ marginBottom: '16px', color: 'var(--text-secondary)' }}>
                        Bàn: <strong>{selectedSession?.tables?.map(t => t.name).join(', ')}</strong>
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
                    <Button 
                        variant="secondary" 
                        onClick={() => {
                            setShowRejectModal(false);
                            setSelectedSession(null);
                            setRejectReason('');
                        }}
                    >
                        Hủy
                    </Button>
                    <Button 
                        variant="danger" 
                        onClick={handleRejectSession}
                        loading={actionLoading}
                    >
                        Từ chối đơn hàng
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Open New Session Modal */}
            <Modal
                isOpen={showOpenSessionModal}
                onClose={() => {
                    setShowOpenSessionModal(false);
                    resetOpenSessionForm();
                }}
                title="Mở phiên phục vụ mới"
                size="md"
            >
                <div className="open-session-modal-content">
                    {/* Step 1: Select table */}
                    <div className="form-group">
                        <label>Chọn bàn <span className="required">*</span></label>
                        {tablesLoading ? (
                            <div className="tables-loading">
                                <Skeleton height="40px" width="100%" />
                                <Skeleton height="40px" width="100%" />
                                <Skeleton height="40px" width="100%" />
                            </div>
                        ) : availableTables.length === 0 ? (
                            <div className="no-tables-available">
                                <Empty 
                                    message="Không có bàn trống" 
                                    description="Tất cả bàn đều đang được sử dụng"
                                />
                            </div>
                        ) : (
                            <div className="table-select-grid">
                                {availableTables.map(table => (
                                    <button
                                        key={table.id}
                                        className={`table-select-item ${selectedTableId === table.id ? 'selected' : ''}`}
                                        onClick={() => setSelectedTableId(table.id)}
                                    >
                                        <Table2 size={20} />
                                        <span className="table-name">{table.name}</span>
                                        {table.capacity && (
                                            <span className="table-capacity">{table.capacity} chỗ</span>
                                        )}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Confirmation text when table selected */}
                    {selectedTableId && (
                        <p className="confirm-text">
                            Xác nhận mở phiên cho bàn <strong>{availableTables.find(t => t.id === selectedTableId)?.name}</strong>?
                        </p>
                    )}
                </div>
                <ModalFooter>
                    <Button 
                        variant="secondary" 
                        onClick={() => {
                            setShowOpenSessionModal(false);
                            resetOpenSessionForm();
                        }}
                    >
                        Hủy
                    </Button>
                    <Button 
                        onClick={handleOpenSession}
                        loading={openSessionLoading}
                        disabled={!selectedTableId || availableTables.length === 0}
                    >
                        Mở phiên
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Attach Table Modal (Thêm bàn) */}
            <Modal
                isOpen={showAttachModal}
                onClose={() => {
                    setShowAttachModal(false);
                    resetAttachForm();
                }}
                title={`Thêm bàn vào phiên - ${attachSession?.tables?.map(t => t.name).join(', ') || ''}`}
                size="md"
            >
                <div className="open-session-modal-content">
                    <p className="modal-description">
                        Phiên hiện tại: <strong>{attachSession?.tables?.map(t => t.name).join(', ')}</strong>
                    </p>
                    <div className="form-group">
                        <label>Chọn bàn để thêm <span className="required">*</span></label>
                        {tablesLoading ? (
                            <div className="tables-loading">
                                <Skeleton height="40px" width="100%" />
                                <Skeleton height="40px" width="100%" />
                            </div>
                        ) : availableTables.length === 0 ? (
                            <div className="no-tables-available">
                                <Empty 
                                    message="Không có bàn trống" 
                                    description="Tất cả bàn đều đang được sử dụng"
                                />
                            </div>
                        ) : (
                            <div className="table-select-grid">
                                {availableTables.map(table => (
                                    <button
                                        key={table.id}
                                        className={`table-select-item ${attachTableId === table.id ? 'selected' : ''}`}
                                        onClick={() => setAttachTableId(table.id)}
                                    >
                                        <Table2 size={20} />
                                        <span className="table-name">{table.name}</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
                <ModalFooter>
                    <Button 
                        variant="secondary" 
                        onClick={() => {
                            setShowAttachModal(false);
                            resetAttachForm();
                        }}
                    >
                        Hủy
                    </Button>
                    <Button 
                        onClick={handleAttachTable}
                        loading={attachLoading}
                        disabled={!attachTableId || availableTables.length === 0}
                    >
                        Thêm bàn
                    </Button>
                </ModalFooter>
            </Modal>

            {/* Transfer Table Modal (Chuyển bàn) */}
            <Modal
                isOpen={showTransferModal}
                onClose={() => {
                    setShowTransferModal(false);
                    resetTransferForm();
                }}
                title="Chuyển bàn"
                size="md"
            >
                <div className="open-session-modal-content">
                    <div className="form-group">
                        <label>Bàn nguồn (bàn cần chuyển) <span className="required">*</span></label>
                        <div className="table-select-grid">
                            {transferSession?.tables?.map(table => (
                                <button
                                    key={table.id}
                                    className={`table-select-item ${sourceTableId === table.id ? 'selected' : ''}`}
                                    onClick={() => setSourceTableId(table.id)}
                                >
                                    <Table2 size={20} />
                                    <span className="table-name">{table.name}</span>
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="transfer-arrow">
                        <ArrowRightLeft size={24} />
                    </div>

                    <div className="form-group">
                        <label>Bàn đích (bàn trống) <span className="required">*</span></label>
                        {tablesLoading ? (
                            <div className="tables-loading">
                                <Skeleton height="40px" width="100%" />
                                <Skeleton height="40px" width="100%" />
                            </div>
                        ) : availableTables.length === 0 ? (
                            <div className="no-tables-available">
                                <Empty 
                                    message="Không có bàn trống" 
                                    description="Tất cả bàn đều đang được sử dụng"
                                />
                            </div>
                        ) : (
                            <div className="table-select-grid">
                                {availableTables.map(table => (
                                    <button
                                        key={table.id}
                                        className={`table-select-item ${targetTableId === table.id ? 'selected' : ''}`}
                                        onClick={() => setTargetTableId(table.id)}
                                    >
                                        <Table2 size={20} />
                                        <span className="table-name">{table.name}</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {sourceTableId && targetTableId && (
                        <p className="confirm-text">
                            Chuyển từ <strong>{transferSession?.tables?.find(t => t.id === sourceTableId)?.name}</strong> sang <strong>{availableTables.find(t => t.id === targetTableId)?.name}</strong>?
                        </p>
                    )}
                </div>
                <ModalFooter>
                    <Button 
                        variant="secondary" 
                        onClick={() => {
                            setShowTransferModal(false);
                            resetTransferForm();
                        }}
                    >
                        Hủy
                    </Button>
                    <Button 
                        onClick={handleTransferTable}
                        loading={transferLoading}
                        disabled={!sourceTableId || !targetTableId || availableTables.length === 0}
                    >
                        Chuyển bàn
                    </Button>
                </ModalFooter>
            </Modal>
        </PageLayout>
    );
}
