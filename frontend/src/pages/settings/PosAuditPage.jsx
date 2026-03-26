import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, RotateCcw, ChevronLeft, ChevronRight } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Card, Input, Button, Loading } from '../../components/common';
import { getPosActionAudit } from '../../api/tenant';
import { getSession } from '../../api/session';
import './PosAuditPage.css';

export function PosAuditPage() {
    const [auditRecords, setAuditRecords] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(20);
    const [totalPages, setTotalPages] = useState(0);
    const [filters, setFilters] = useState({ action: '', userId: '', accessKeyId: '', targetType: '' });

    const loadData = async (pageNumber = page, pageSize = size) => {
        setLoading(true);
        try {
            const params = {
                page: pageNumber,
                size: pageSize,
                action: filters.action || undefined,
                userId: filters.userId || undefined,
                accessKeyId: filters.accessKeyId || undefined,
                targetType: filters.targetType || undefined,
            };
            const pageData = await getPosActionAudit(params);
            if (pageData) {
                setAuditRecords(pageData.content || []);
                setTotalPages(pageData.totalPages || 0);
            }
        } catch (error) {
            console.error('Không tải được audit', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData(page, size);
    }, [page, size]);

    const handleSearch = () => {
        setPage(0);
        loadData(0, size);
    };

    const gotoPrevPage = () => setPage((p) => Math.max(p - 1, 0));
    const gotoNextPage = () => setPage((p) => Math.min(p + 1, totalPages - 1));

    const navigate = useNavigate();

    const handleGoToSession = async (sessionId) => {
        if (!sessionId) return;
        try {
            const session = await getSession(sessionId);
            if (!session) return;
            if (session.status === 'ACTIVE' || session.status === 'PENDING') {
                const tableId = session.tables?.[0]?.id;
                navigate(`/pos${tableId ? `?table=${tableId}` : ''}`);
            } else {
                navigate(`/sessions/${sessionId}`);
            }
        } catch (err) {
            console.error('Không mở được session', err);
            navigate(`/sessions/${sessionId}`);
        }
    };

    return (
        <PageLayout title="Audit hành động POS">
            <Card className="audit-filters-card">
                <div className="pos-audit-filters">
                    <Input
                        label="Action"
                        value={filters.action}
                        onChange={(e) => setFilters({ ...filters, action: e.target.value })}
                    />
                    <Input
                        label="User ID"
                        value={filters.userId}
                        onChange={(e) => setFilters({ ...filters, userId: e.target.value })}
                    />
                    <Input
                        label="Access Key"
                        value={filters.accessKeyId}
                        onChange={(e) => setFilters({ ...filters, accessKeyId: e.target.value })}
                    />
                    <Input
                        label="Target Type"
                        value={filters.targetType}
                        onChange={(e) => setFilters({ ...filters, targetType: e.target.value })}
                    />
                    <Button variant="primary" size="sm" onClick={handleSearch}>
                        <Search size={16} /> Tìm
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => {
                        setFilters({ action: '', userId: '', accessKeyId: '', targetType: '' });
                        setPage(0);
                        loadData(0, size);
                    }}>
                        <RotateCcw size={16} /> Làm mới
                    </Button>
                </div>
            </Card>

            <Card className="audit-table-card">
                {loading ? (
                    <Loading />
                ) : (
                    <>
                        <table className="audit-table">
                            <thead>
                                <tr>
                                    <th>Thời gian</th>
                                    <th>Action</th>
                                    <th>User/Access Key</th>
                                    <th>Role</th>
                                    <th>Target</th>
                                    <th>Session</th>
                                    <th>Số tiền</th>
                                    <th>Ghi chú</th>
                                </tr>
                            </thead>
                            <tbody>
                                {auditRecords.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} style={{ textAlign: 'center' }}>Không có dữ liệu</td>
                                    </tr>
                                ) :
                                    auditRecords.map(record => {
                                        const userDisplay = record.userType === 'OWNER'
                                            ? `Owner: ${record.userId || '-'}`
                                            : record.userType === 'ACCESS_KEY'
                                                ? `AccessKey: ${record.userId || record.accessKeyId || '-'}`
                                                : (record.userId || '-');

                                        const roleDisplay = record.userType === 'ACCESS_KEY' ? (record.accessKeyRole || '-') : '-';

                                        return (
                                            <tr key={record.id}>
                                                <td>{new Date(record.createdAt).toLocaleString('vi-VN')}</td>
                                                <td>{record.action}</td>
                                                <td>{userDisplay}</td>
                                                <td>{roleDisplay}</td>
                                                <td>{record.targetType} / {record.targetId}</td>
                                                <td>
                                                    {record.sessionId ? (
                                                        <Button variant="link" onClick={() => handleGoToSession(record.sessionId)}>
                                                            Xem session {record.sessionId}
                                                        </Button>
                                                    ) : (
                                                        '-'
                                                    )}
                                                </td>
                                                <td>{record.amount ? new Intl.NumberFormat('vi-VN').format(record.amount) + 'đ' : '-'}</td>
                                                <td>{record.note}</td>
                                            </tr>
                                        );
                                    })
                                }
                            </tbody>
                        </table>

                        <div className="audit-pagination">
                            <Button onClick={gotoPrevPage} disabled={page <= 0}>
                                <ChevronLeft size={16} /> Trước
                            </Button>
                            <span>Trang {page + 1} / {totalPages}</span>
                            <Button onClick={gotoNextPage} disabled={page >= totalPages - 1}>
                                Sau <ChevronRight size={16} />
                            </Button>
                            <div className="audit-page-size">
                                <span>Kích thước</span>
                                <select value={size} onChange={(e) => setSize(Number(e.target.value))}>
                                    <option value={10}>10</option>
                                    <option value={20}>20</option>
                                    <option value={50}>50</option>
                                </select>
                            </div>
                        </div>
                    </>
                )}
            </Card>
        </PageLayout>
    );
}
