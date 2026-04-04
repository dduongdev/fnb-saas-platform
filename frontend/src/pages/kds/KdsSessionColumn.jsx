/**
 * KDS Session Column Component
 *
 * Hiển thị một cột đại diện cho một Session (phiên order).
 * Chứa:
 * - Header: Danh sách số bàn + Status badge
 * - Stats: Số món chờ, đã hoàn thành
 * - Body: Danh sách các món ăn
 * - Footer: Tổng thời gian chờ
 *
 * @author FNB Team
 * @version 1.0
 */

import React, { useMemo, useState, useEffect } from 'react';
import KdsOrderItemCard from './KdsOrderItemCard';

export default function KdsSessionColumn({ session }) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const interval = setInterval(() => setNow(new Date()), 30000); // update mỗi 30s
    return () => clearInterval(interval);
  }, []);
  if (!session) return null;

  // Tính toán thống kê
  const stats = useMemo(() => {
    const pendingItems = session.items.filter((item) => item.status === 'PENDING');
    const servedItems = session.items.filter((item) => item.status === 'SERVED');

    return {
      totalItems: session.items.length,
      pendingCount: pendingItems.length,
      servedCount: servedItems.length,
    };
  }, [session.items]);

  // Determine session status
  const getSessionStatus = () => {
    if (stats.pendingCount === 0) {
      return { status: 'COMPLETED', label: 'Hoàn thành', color: 'completed' };
    }
    return { status: 'ACTIVE', label: 'Hoạt động', color: 'active' };
  };

  const sessionStatus = getSessionStatus();

  // Normalize sessionId for display/shorten safely
  const shortSessionId = useMemo(() => {
    if (!session.sessionId && session.sessionId !== 0) return null;
    const idString = String(session.sessionId);
    return idString.length <= 8 ? idString : idString.slice(0, 8);
  }, [session.sessionId]);

  // Tính lại thời gian chờ dựa trên thời gian thực
  const getElapsedMinutes = () => {
    if (!session.sessionCreatedAt) return session.totalMinutesWaited || 0;
    const created = new Date(session.sessionCreatedAt);
    const diffMs = now - created;
    return Math.max(0, Math.floor(diffMs / 60000));
  };

  const formatWaitTime = (minutes) => {
    if (minutes === 0) return 'Vừa tạo';
    if (minutes < 1) return '< 1 phút';
    if (minutes < 60) return `${minutes} phút`;
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours}h ${mins}m`;
  };

  // Get visual urgency level
  const getUrgencyLevel = () => {
    const waitTime = getElapsedMinutes();
    if (waitTime > 30) return 'critical';
    if (waitTime > 15) return 'warning';
    return 'normal';
  };

  const urgency = getUrgencyLevel();

  return (
    <div className={`kds-session-column urgency-${urgency}`}>
      {/* Header with Status */}
      <div className="kds-column-header">
        <div className="kds-header-content">
          <div className="kds-table-names">{session.tableNames}</div>
          <span className={`kds-status-badge ${sessionStatus.color}`}>
            {sessionStatus.label}
          </span>
        </div>
        <div className="kds-header-badges">
          <span className="badge-pending">{stats.pendingCount}</span>
          <span className="badge-served">{stats.servedCount}</span>
        </div>
      </div>

      {/* Stats Bar */}
      <div className="kds-column-stats">
        <div className="kds-stat-bar">
          <span className="kds-stat-label">Chờ:</span>
          <span className="kds-stat-count">{stats.pendingCount}</span>
        </div>
        <div className="kds-stat-bar">
          <span className="kds-stat-label">Xong:</span>
          <span className="kds-stat-count">{stats.servedCount}</span>
        </div>
        <div className="kds-stat-bar">
          <span className="kds-stat-label">Tổng:</span>
          <span className="kds-stat-count">{stats.totalItems}</span>
        </div>
      </div>

      {/* Body - Items List */}
      <div className="kds-column-body">
        {session.items.length === 0 ? (
          <div className="kds-column-empty">
            <p>Không có món ăn</p>
          </div>
        ) : (
          // Separate pending and served items
          <>
            {/* Pending Items */}
            <div className="kds-items-group pending-items">
              {session.items
                .filter((item) => item.status === 'PENDING')
                .map((item) => (
                  <KdsOrderItemCard key={item.itemId} item={item} />
                ))}
            </div>

            {/* Served Items */}
            {session.items.filter((item) => item.status === 'SERVED').length > 0 && (
              <div className="kds-items-group served-items">
                <div className="kds-group-separator">Đã hoàn thành</div>
                {session.items
                  .filter((item) => item.status === 'SERVED')
                  .map((item) => (
                    <KdsOrderItemCard key={item.itemId} item={item} />
                  ))}
              </div>
            )}
          </>
        )}
      </div>

      {/* Footer */}
      <div className={`kds-column-footer urgency-${urgency}`}>
        <span className={`kds-wait-time urgency-${urgency}`}>
          ⏱️ {formatWaitTime(getElapsedMinutes())}
        </span>
        <span className="kds-footer-detail">
          {shortSessionId ? `#${shortSessionId}` : ''}
        </span>
      </div>
    </div>
  );
}
