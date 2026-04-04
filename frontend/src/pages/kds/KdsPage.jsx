/**
 * KDS Main Page Component
 *
 * Hiển thị Kitchen Display System với column-based layout.
 * Mỗi column đại diện cho một Session (phiên order).
 *
 * Features:
 * - Real-time session updates via WebSocket
 * - Filter by kitchen area
 * - Search by table name
 * - Responsive grid layout
 *
 * @author FNB Team
 * @version 1.0
 */

import React, { useEffect, useState, useMemo } from 'react';
import { useKds } from '../../context/KdsContext';
import KdsHeader from './KdsHeader';
import KdsToolbar from './KdsToolbar';
import KdsSessionColumn from './KdsSessionColumn';
import './KdsPage.css';

export default function KdsPage() {
  const { sessions, error, loading, connected } = useKds();
  const [filteredSessions, setFilteredSessions] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');

  // Filter and search sessions
  useEffect(() => {
    let result = [...sessions];

    // ...đã xóa filter khu vực bếp...

    // Search by table name
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      result = result.filter((s) =>
        s.tableNames.toLowerCase().includes(term)
      );
    }

    // Sort: active sessions first, then by wait time descending
    result.sort((a, b) => {
      const aPending = a.pendingItemCount || 0;
      const bPending = b.pendingItemCount || 0;

      if (aPending !== bPending) {
        return bPending - aPending; // More pending items first
      }

      return (b.totalMinutesWaited || 0) - (a.totalMinutesWaited || 0); // Longer wait first
    });

    setFilteredSessions(result);
  }, [sessions, searchTerm]);

  const stats = useMemo(() => {
    return {
      totalPending: sessions.reduce((sum, s) => sum + (s.pendingItemCount || 0), 0),
      totalServed: sessions.reduce(
        (sum, s) => sum + (s.items?.filter((i) => i.status === 'SERVED').length || 0),
        0
      ),
    };
  }, [sessions]);

  // Đã xóa handleFilterChange cho khu vực bếp

  const handleSearchChange = (term) => {
    setSearchTerm(term);
  };

  return (
    <div className="kds-page">
      {/* Header */}
      <KdsHeader
        sessionCount={sessions.length}
        totalPendingItems={stats.totalPending}
        totalServedItems={stats.totalServed}
        connected={connected}
        error={error}
        sessions={sessions}
      />

      {/* Toolbar */}
      <KdsToolbar
        sessions={sessions}
        onSearchChange={handleSearchChange}
      />

      {/* Main Content */}
      <div className="kds-container">
        {error && (
          <div className="kds-error-banner">
            <p>{error}</p>
          </div>
        )}

        {!connected && (
          <div className="kds-warning-banner">
            <p>⚠️ Đang kết nối tới máy chủ...</p>
          </div>
        )}

        {filteredSessions.length === 0 ? (
          <div className="kds-empty-state">
            <p>
              {sessions.length === 0
                ? '🍽️ Không có phiên order nào đang hoạt động'
                : '🔍 Không tìm thấy phiên order phù hợp'}
            </p>
            <p className="kds-empty-state-subtext">
              {sessions.length === 0
                ? 'Khách sẽ tự động xuất hiện ở đây khi order'
                : 'Thử thay đổi bộ lọc hoặc từ tìm kiếm'}
            </p>
          </div>
        ) : (
          <div className="kds-columns-container">
            {filteredSessions.map((session) => (
              <KdsSessionColumn key={session.sessionId} session={session} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
