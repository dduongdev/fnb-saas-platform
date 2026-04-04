/**
 * KDS Order Item Card Component
 *
 * Hiển thị thông tin của một món ăn trong session.
 * Trạng thái được phân biệt bằng màu sắc:
 * - PENDING: Màu vàng/cam (đang chờ/nấu)
 * - SERVED: Màu xanh + gạch ngang (đã hoàn thành)
 *
 * Features:
 * - Elapsed time tracking
 * - Quantity display
 * - Notes/special instructions
 * - Original table reference
 * - Urgency-based animations
 *
 * @author FNB Team
 * @version 1.0
 */

import React, { useMemo } from 'react';

export default function KdsOrderItemCard({ item }) {
  if (!item) return null;

  const isPending = item.status === 'PENDING';
  const isServed = item.status === 'SERVED';

  // Calculate elapsed time since created
  const elapsedTime = useMemo(() => {
    if (!item.createdAt) return '0m';

    const created = new Date(item.createdAt);
    const now = new Date();
    const diffMinutes = Math.floor((now - created) / 60000);

    if (diffMinutes < 1) return '< 1m';
    if (diffMinutes < 60) return `${diffMinutes}m`;

    const hours = Math.floor(diffMinutes / 60);
    return `${hours}h+`;
  }, [item.createdAt]);

  // Determine urgency level based on elapsed time
  const getUrgency = () => {
    if (!item.createdAt) return 'normal';

    const created = new Date(item.createdAt);
    const now = new Date();
    const diffMinutes = Math.floor((now - created) / 60000);

    if (diffMinutes > 20) return 'critical'; // Red
    if (diffMinutes > 10) return 'warning'; // Orange
    return 'normal'; // Yellow
  };

  const urgency = isPending ? getUrgency() : 'normal';

  return (
    <div
      className={`kds-order-item-card ${isPending ? 'pending' : ''} ${isServed ? 'served' : ''} urgency-${urgency}`}
    >
      {/* Priority Indicator */}
      {isPending && (
        <div className={`kds-urgency-indicator urgency-${urgency}`} title="Độ ưu tiên"></div>
      )}

      {/* Main Content */}
      <div className="kds-item-content">
        {/* Product Name & Quantity */}
        <div className="kds-item-header">
          <h4 className="kds-product-name">{item.productName}</h4>
          <span className="kds-quantity-badge">×{item.quantity}</span>
        </div>

        {/* Notes/Special Instructions */}
        {item.notes && (
          <div className="kds-item-notes">
            <span className="kds-notes-icon">📝</span>
            <p>{item.notes}</p>
          </div>
        )}

        {/* Original Table Reference */}
        {item.originalTableName && (
          <div className="kds-item-reference">
            <span className="kds-ref-icon">📍</span>
            <span className="kds-ref-text">Từ: {item.originalTableName}</span>
          </div>
        )}
      </div>

      {/* Footer with Time & Status */}
      <div className="kds-item-footer">
        <div className="kds-item-timing">
          <span className={`kds-item-time ${isPending ? `pending urgency-${urgency}` : 'served'}`}>
            {elapsedTime}
          </span>
        </div>
        {isServed && <span className="kds-served-check">✓</span>}
      </div>
    </div>
  );
}
