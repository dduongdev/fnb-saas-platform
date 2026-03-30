import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { PageLayout } from '../../components/layout';
import { Card, Button, Loading, StatusBadge } from '../../components/common';
import { useToast } from '../../context/ToastContext';
import { getSession } from '../../api/session';

export function SessionDetailPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const data = await getSession(sessionId);
        setSession(data);
      } catch (error) {
        toast.error('Không tìm thấy session');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [sessionId, toast]);

  if (loading) return <Loading />;
  if (!session) return <Card>Không tìm thấy session</Card>;

  return (
    <PageLayout title={`Chi tiết session #${sessionId}`}>
      <Card>
        <div className="session-detail-header">
          <div>
            <h2>Chi tiết session #{sessionId}</h2>
            <StatusBadge status={session.status} variant={
              session.status === 'ACTIVE' ? 'success' :
              session.status === 'PENDING' ? 'warning' :
              session.status === 'COMPLETED' ? 'info' :
              session.status === 'CANCELLED' ? 'danger' : 'default'
            } />
          </div>
        </div>

        <div className="session-detail-grid">
          <div className="detail-item"><strong>Bàn:</strong> {session.tables?.map(t => t.name).join(', ') || '-'}</div>
          <div className="detail-item"><strong>Bắt đầu:</strong> {session.startedAt ? new Date(session.startedAt).toLocaleString('vi-VN') : '-'}</div>
          <div className="detail-item"><strong>Kết thúc:</strong> {session.endedAt ? new Date(session.endedAt).toLocaleString('vi-VN') : '-'}</div>
          <div className="detail-item"><strong>Tổng:</strong> {session.totalAmount ? new Intl.NumberFormat('vi-VN').format(session.totalAmount) + 'đ' : '-'}</div>
        </div>

        <h4>Orders</h4>
        {session.orders?.length === 0 ? <p>Không có đơn</p> : (
          session.orders.map(order => (
            <Card key={order.orderId} style={{ marginBottom: 12 }}>
              <div className="order-summary">
                <div><strong>Order ID:</strong> {order.orderId}</div>
                <div><strong>Trạng thái:</strong> {order.status}</div>
                <div><strong>Tổng:</strong> {order.totalAmount ? new Intl.NumberFormat('vi-VN').format(order.totalAmount) + 'đ' : '-'}</div>
              </div>

              <div className="order-items-table-wrapper">
                <table className="order-items-table">
                  <thead>
                    <tr>
                      <th>Tên món</th>
                      <th>Số lượng</th>
                      <th>Giá</th>
                      <th>Trạng thái</th>
                      <th>Ghi chú</th>
                      <th>Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.items?.length === 0 ? (
                      <tr><td colSpan={6} style={{ textAlign: 'center' }}>Không có item</td></tr>
                    ) : order.items.map(item => (
                      <tr key={item.id}>
                        <td>{item.productName}</td>
                        <td>{item.quantity}</td>
                        <td>{item.price ? new Intl.NumberFormat('vi-VN').format(item.price) + 'đ' : '-'}</td>
                        <td>{item.status}</td>
                        <td>{item.note || '-'}</td>
                        <td>{(item.total) ? new Intl.NumberFormat('vi-VN').format(item.total) + 'đ' : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          ))
        )}
      </Card>
    </PageLayout>
  );
}
