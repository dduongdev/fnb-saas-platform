import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageLayout } from '../../components/layout';
import { Card, Button, Loading, Input } from '../../components/common';
import { getSessionHistory, getSession } from '../../api/session';
import { formatDateTime } from '../../utils/format';
import './SessionListPage.css';

export function SessionHistoryPage() {
  const navigate = useNavigate();
  const [history, setHistory] = useState([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState('');

  const loadHistory = async (p = page, s = size) => {
    setLoading(true);
    try {
      const data = await getSessionHistory(p, s);
      setHistory(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (error) {
      console.error('Không thể tải lịch sử session', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHistory();
  }, [page, size]);

  const openSession = async (sessionId) => {
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
      console.error('Không thể mở session details', err);
      navigate(`/sessions/${sessionId}`);
    }
  };

  return (
    <PageLayout title="Lịch sử Session">
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <div>
            <label>Trạng thái: </label>
            <Input
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
              placeholder="ACTIVE, COMPLETED, CANCELLED, PENDING"
              style={{ width: 220 }}
            />
            <Button style={{ marginLeft: 8 }} onClick={() => loadHistory(0, size)}>Lọc</Button>
          </div>
        </div>

        {loading ? <Loading /> : (
          <table className="session-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th>Session ID</th>
                <th>Status</th>
                <th>Bàn</th>
                <th>Thời gian bắt đầu</th>
                <th>Thời gian kết thúc</th>
                <th>Tổng</th>
                <th>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {history.length === 0 ? (
                <tr><td colSpan={7} style={{ textAlign: 'center' }}>Không có lịch sử</td></tr>
              ) : history
                  .filter(item => !filterStatus || item.status === filterStatus)
                  .map((item) => (
                    <tr key={item.sessionId}>
                      <td>{item.sessionId}</td>
                      <td>{item.status}</td>
                      <td>{item.tables?.map(t => t.name).join(', ') || '-'}</td>
                      <td>{item.startedAt ? formatDateTime(item.startedAt) : '-'}</td>
                      <td>{item.endedAt ? formatDateTime(item.endedAt) : '-'}</td>
                      <td>{item.totalAmount ? new Intl.NumberFormat('vi-VN').format(item.totalAmount) + 'đ' : '-'}</td>
                      <td>
                        <Button size="sm" variant="primary" onClick={() => openSession(item.sessionId)}>
                          Xem
                        </Button>
                      </td>
                    </tr>
                  ))}
            </tbody>
          </table>
        )}

        <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
          <Button onClick={() => setPage(prev => Math.max(prev - 1, 0))} disabled={page <= 0}>Trước</Button>
          <span>Trang {page + 1}/{totalPages}</span>
          <Button onClick={() => setPage(prev => Math.min(prev + 1, totalPages - 1))} disabled={page >= totalPages - 1}>Sau</Button>
          <select value={size} onChange={e => setSize(Number(e.target.value))}>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
          </select>
        </div>
      </Card>
    </PageLayout>
  );
}
