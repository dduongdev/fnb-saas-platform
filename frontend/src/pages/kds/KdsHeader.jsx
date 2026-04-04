/**
 * KDS Header Component
 *
 * Hiển thị thông tin tổng quan: số phiên, số món đang chờ, trạng thái kết nối.
 *
 * @author FNB Team
 * @version 1.0
 */

import React, { useMemo } from 'react';

export default function KdsHeader({
  sessionCount = 0,
  totalPendingItems = 0,
  totalServedItems = 0,
  avgWaitTime = 0,
  connected = false,
  error = null,
  sessions = [],
  showLogout = false,
  onLogout = () => {},
}) {
  // Calculate comprehensive stats
  const stats = useMemo(() => {
    if (!sessions.length) {
      return {
        activeCount: 0,
        pendingCount: 0,
        servedCount: 0,
        maxWaitTime: 0,
        avgWait: 0,
      };
    }

    const pending = sessions.reduce((sum, s) => sum + (s.pendingItemCount || 0), 0);
    const served = sessions.reduce((sum, s) => sum + (s.items?.filter((i) => i.status === 'SERVED').length || 0), 0);
    const maxWait = Math.max(...sessions.map((s) => s.totalMinutesWaited || 0));
    const avgWait = sessions.reduce((sum, s) => sum + (s.totalMinutesWaited || 0), 0) / sessions.length;

    return {
      activeCount: sessions.length,
      pendingCount: pending,
      servedCount: served,
      maxWaitTime: maxWait,
      avgWait: Math.round(avgWait),
    };
  }, [sessions]);

  const formatTime = (minutes) => {
    if (minutes < 1) return '< 1m';
    if (minutes < 60) return `${minutes}m`;
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours}h ${mins}m`;
  };

  const currentTime = new Date().toLocaleTimeString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });

  return (
    <div className="kds-header">
      <div className="kds-header-top">
        <div className="kds-header-title">
          <h1>🍳 Kitchen Display System</h1>
          <span className="kds-header-time">{currentTime}</span>
        </div>
        <div className="kds-header-actions">
          {showLogout && (
            <button className="kds-logout-button" type="button" onClick={onLogout}>
              Đăng xuất
            </button>
          )}
          <div className={`kds-connection-status ${connected ? 'connected' : 'disconnected'}`}>
            <span className="kds-status-dot"></span>
            {connected ? 'Kết nối' : 'Mất kết nối'}
          </div>
        </div>
      </div>

      <div className="kds-header-stats">
        <div className="kds-stat-item">
          <span className="kds-stat-icon">📊</span>
          <div className="kds-stat-content">
            <span className="kds-stat-label">Phiên Hoạt Động</span>
            <span className="kds-stat-value">{stats.activeCount}</span>
          </div>
        </div>

        <div className="kds-stat-item">
          <span className="kds-stat-icon">⏳</span>
          <div className="kds-stat-content">
            <span className="kds-stat-label">Món Chờ</span>
            <span className="kds-stat-value pending">{stats.pendingCount}</span>
          </div>
        </div>

        <div className="kds-stat-item">
          <span className="kds-stat-icon">✓</span>
          <div className="kds-stat-content">
            <span className="kds-stat-label">Đã Hoàn Thành</span>
            <span className="kds-stat-value completed">{stats.servedCount}</span>
          </div>
        </div>

        <div className="kds-stat-item">
          <span className="kds-stat-icon">⏱️</span>
          <div className="kds-stat-content">
            <span className="kds-stat-label">Thời Gian Chờ Trung Bình</span>
            <span className="kds-stat-value">{formatTime(stats.avgWait)}</span>
          </div>
        </div>

        <div className="kds-stat-item">
          <span className="kds-stat-icon">🔴</span>
          <div className="kds-stat-content">
            <span className="kds-stat-label">Thời Gian Chờ Tối Đa</span>
            <span className="kds-stat-value critical">{formatTime(stats.maxWaitTime)}</span>
          </div>
        </div>
      </div>

      {error && (
        <div className="kds-header-error">
          <span>⚠️ {error}</span>
        </div>
      )}
    </div>
  );
}
