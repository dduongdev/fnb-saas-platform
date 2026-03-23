import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Grid, Trash2, QrCode } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Card, Loading, Empty, Modal, ModalFooter, Input, StatusBadge, ConfirmModal } from '../../components/common';
import { getTables, createTable, deleteTable } from '../../api/pos';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import './TableGridPage.css';

export function TableGridPage() {
    const navigate = useNavigate();
    const { user } = useAuth();
    const toast = useToast();
    const [tables, setTables] = useState([]);
    
    const isWaitstaff = user?.isWaitstaff;
    const [loading, setLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newTableName, setNewTableName] = useState('');
    const [createLoading, setCreateLoading] = useState(false);

    // Delete table state
    const [deleteTableId, setDeleteTableId] = useState(null);

    // QR modal state
    const [qrTable, setQrTable] = useState(null);

    useEffect(() => {
        loadTables();
    }, []);

    const loadTables = async () => {
        try {
            setLoading(true);
            const data = await getTables();
            setTables(data || []);
        } catch (error) {
            console.error('Failed to load tables:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleCreateTable = async (e) => {
        e.preventDefault();
        if (!newTableName.trim()) return;

        try {
            setCreateLoading(true);
            await createTable(newTableName);
            await loadTables();
            setShowCreateModal(false);
            setNewTableName('');
            toast.success('Đã thêm bàn mới');
        } catch (error) {
            console.error('Failed to create table:', error);
            toast.error(error.message);
        } finally {
            setCreateLoading(false);
        }
    };

    const handleTableClick = (table) => {
        // Navigate to POS
        navigate(`/pos?table=${table.id}`);
    };

    const handleDeleteTable = async () => {
        if (!deleteTableId) return;

        try {
            await deleteTable(deleteTableId);
            toast.success('Đã xóa bàn');
            setDeleteTableId(null);
            await loadTables();
        } catch (error) {
            console.error('Failed to delete table:', error);
            toast.error(error.message);
        }
    };

    // Lấy các bàn cùng session với bàn hiện tại
    const getOtherTablesInSession = (table) => {
        if (!table.sessionId) return [];
        return tables.filter(
            t => t.id !== table.id && t.sessionId === table.sessionId
        );
    };

    if (loading) {
        return (
            <PageLayout title="Sơ đồ bàn">
                <Loading />
            </PageLayout>
        );
    }

    return (
        <PageLayout
            title="Sơ đồ bàn"
            actions={
                !isWaitstaff && (
                    <Button onClick={() => setShowCreateModal(true)}>
                        Thêm bàn
                    </Button>
                )
            }
        >
            {tables.length === 0 ? (
                <Empty
                    icon={Grid}
                    message="Chưa có bàn nào"
                    description="Thêm bàn để bắt đầu bán hàng"
                    action={
                        !isWaitstaff && (
                            <Button onClick={() => setShowCreateModal(true)}>
                                Thêm bàn đầu tiên
                            </Button>
                        )
                    }
                />
            ) : (
                <div className="table-grid">
                    {tables.map(table => {
                        const otherTablesInSession = getOtherTablesInSession(table);
                        const hasSession = table.sessionId != null;
                        const isInMergedGroup = otherTablesInSession.length > 0;

                        return (
                            <Card
                                key={table.id}
                                className={`table-card table-${table.status.toLowerCase()}`}
                                onClick={() => handleTableClick(table)}
                            >
                                <div className="table-card-header">
                                    <h3 className="table-card-name">{table.name}</h3>
                                    <StatusBadge status={table.status} />
                                </div>

                                {hasSession && (
                                    <div className="table-card-session">
                                        <span className="table-card-session-label">Đang phục vụ</span>
                                    </div>
                                )}

                                {isInMergedGroup && (
                                    <div className="table-card-merged">
                                        <span className="table-card-merged-label">
                                            Gộp với: {otherTablesInSession.map(t => t.name).join(', ')}
                                        </span>
                                    </div>
                                )}

                                {/* Delete button for empty tables */}
                                {!hasSession && !isWaitstaff && (
                                    <button
                                        className="table-card-delete"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleDeleteClick(table.id);
                                        }}
                                        title="Xoá bàn"
                                    >
                                        <Trash2 size={16} />
                                    </button>
                                )}

                                {/* QR Code button */}
                                <button
                                    className="table-card-qr"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setQrTable(table);
                                    }}
                                    title="Xem mã QR"
                                >
                                    <QrCode size={16} />
                                </button>
                            </Card>
                        );
                    })}
                </div>
            )}

            {/* Create Table Modal */}
            <Modal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                title="Thêm bàn mới"
                size="sm"
            >
                <form onSubmit={handleCreateTable}>
                    <Input
                        label="Tên bàn"
                        placeholder="VD: Bàn 01, Bàn VIP"
                        value={newTableName}
                        onChange={(e) => setNewTableName(e.target.value)}
                        required
                    />
                    <ModalFooter>
                        <Button variant="secondary" onClick={() => setShowCreateModal(false)}>
                            Hủy
                        </Button>
                        <Button type="submit" loading={createLoading}>
                            Thêm bàn
                        </Button>
                    </ModalFooter>
                </form>
            </Modal>

            {/* Delete Table Confirmation Modal */}
            <ConfirmModal
                isOpen={deleteTableId !== null}
                onClose={() => setDeleteTableId(null)}
                onConfirm={handleDeleteTable}
                title="Xóa bàn"
                message={`Bạn có chắc chắn muốn xóa bàn "${tables.find(t => t.id === deleteTableId)?.name || ''}" không?`}
                confirmText="Xóa"
                confirmVariant="danger"
            />

            {/* QR Code Modal */}
            <Modal
                isOpen={qrTable !== null}
                onClose={() => setQrTable(null)}
                title={`Mã QR - ${qrTable?.name || ''}`}
                size="sm"
            >
                <div className="qr-modal-content">
                    {qrTable?.qrCodeUrl ? (
                        <>
                            <img 
                                src={qrTable.qrCodeUrl} 
                                alt={`QR Code cho ${qrTable.name}`}
                                className="qr-image"
                            />
                            <p className="qr-description">
                                Khách hàng quét mã này để xem menu và gọi món
                            </p>
                            <Button
                                variant="primary"
                                onClick={() => {
                                    const link = document.createElement('a');
                                    link.href = qrTable.qrCodeUrl;
                                    link.download = `QR-${qrTable.name}.png`;
                                    link.click();
                                    toast.success('Đã tải mã QR');
                                }}
                            >
                                <QrCode size={16} />
                                Tải mã QR
                            </Button>
                        </>
                    ) : (
                        <p className="qr-no-code">Chưa có mã QR cho bàn này</p>
                    )}
                </div>
            </Modal>
        </PageLayout>
    );
}
