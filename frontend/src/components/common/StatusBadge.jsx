import './StatusBadge.css';

const statusConfig = {
    // Order status
    OPEN: { label: 'Đang phục vụ', variant: 'info' },
    WAITING_PAYMENT: { label: 'Chờ thanh toán', variant: 'warning' },
    COMPLETED: { label: 'Hoàn thành', variant: 'success' },
    CANCELLED: { label: 'Đã hủy', variant: 'danger' },

    // Table status
    AVAILABLE: { label: 'Trống', variant: 'success' },
    EMPTY: { label: 'Trống', variant: 'success' },  // backward compat
    OCCUPIED: { label: 'Có khách', variant: 'warning' },
    RESERVED: { label: 'Đã đặt', variant: 'info' },
    SERVING: { label: 'Có khách', variant: 'warning' },  // backward compat

    // Product status (AVAILABLE already defined above for Table - use same style)
    OUT_OF_STOCK: { label: 'Hết hàng', variant: 'warning' },
    HIDDEN: { label: 'Đang ẩn', variant: 'muted' },

    // Session status
    PENDING: { label: 'Chờ xác nhận', variant: 'warning' },
    ACTIVE: { label: 'Đang phục vụ', variant: 'info' },

    // Application status
    APPROVED: { label: 'Đã duyệt', variant: 'success' },
    REJECTED: { label: 'Từ chối', variant: 'danger' },

    // Employee role
    MANAGER: { label: 'Quản lý', variant: 'info' },
    STAFF: { label: 'Nhân viên', variant: 'muted' },
    
    // Employee status
    RESIGNED: { label: 'Đã nghỉ', variant: 'muted' },
};

export function StatusBadge({ status, customLabel }) {
    const config = statusConfig[status] || { label: status, variant: 'muted' };

    return (
        <span className={`status-badge status-${config.variant}`}>
            {customLabel || config.label}
        </span>
    );
}
