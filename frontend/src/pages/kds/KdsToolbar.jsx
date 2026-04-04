/**
 * KDS Toolbar Component
 *
 * Cung cấp các công cụ filter & search:
 * - Filter theo kitchen area
 * - Search theo tên bàn
 * - Toggle view modes
 *
 * @author FNB Team
 * @version 1.0
 */

import React, { useState, useMemo } from 'react';

export default function KdsToolbar({
  sessions = [],
  onFilterChange = () => {},
  onSearchChange = () => {},
  showLogout = false,
  onLogout = () => {},
}) {
  // ...existing code...
  const [searchTerm, setSearchTerm] = useState('');

  // ...existing code...

  const handleSearchChange = (e) => {
    const term = e.target.value;
    setSearchTerm(term);
    onSearchChange(term);
  };

  return (
    <div className="kds-toolbar">
      {/* Đã xóa filter khu vực bếp */}

      <div className="kds-toolbar-section">
        <label className="kds-toolbar-label">Tìm bàn:</label>
        <input
          type="text"
          className="kds-toolbar-input"
          placeholder="Nhập số bàn..."
          value={searchTerm}
          onChange={handleSearchChange}
        />
      </div>

      <div className="kds-toolbar-section">
        <div className="kds-toolbar-stats">
          <span className="kds-toolbar-stat">
            <span className="kds-stat-dot"></span>
            Hoạt động
          </span>
          <span className="kds-toolbar-stat">
            <span className="kds-stat-dot completed"></span>
            Hoàn thành
          </span>
        </div>
      </div>

      {showLogout && (
        <div className="kds-toolbar-section kds-toolbar-logout">
          <button className="kds-logout-button" type="button" onClick={onLogout}>
            Đăng xuất
          </button>
        </div>
      )}
    </div>
  );
}
