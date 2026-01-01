/**
 * Format currency in VND
 */
export function formatPrice(price) {
    return new Intl.NumberFormat('vi-VN').format(price) + 'đ';
}

/**
 * Format date to Vietnamese locale
 */
export function formatDate(date) {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleDateString('vi-VN');
}

/**
 * Format datetime to Vietnamese locale
 */
export function formatDateTime(date) {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleString('vi-VN');
}

/**
 * Format time (hours:minutes)
 */
export function formatTime(date) {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}
