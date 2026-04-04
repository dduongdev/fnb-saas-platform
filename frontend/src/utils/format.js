/**
 * Format currency in VND
 */
export function formatPrice(price) {
    return new Intl.NumberFormat('vi-VN').format(price) + 'đ';
}

const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';

function toDate(value) {
    if (value === null || value === undefined || value === '') return null;
    if (typeof value === 'number') return new Date(value);
    if (typeof value === 'string' && /^\d+$/.test(value)) return new Date(Number(value));
    return new Date(value);
}

/**
 * Format date to Vietnamese locale
 */
export function formatDate(date) {
    if (!date) return '';
    const d = toDate(date);
    if (!d || Number.isNaN(d.getTime())) return '';
    return d.toLocaleDateString('vi-VN', { timeZone: VN_TIME_ZONE });
}

/**
 * Format datetime to Vietnamese locale
 */
export function formatDateTime(date) {
    if (!date) return '';
    const d = toDate(date);
    if (!d || Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN', {
        timeZone: VN_TIME_ZONE,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    });
}

/**
 * Format time (hours:minutes)
 */
export function formatTime(date) {
    if (!date) return '';
    const d = toDate(date);
    if (!d || Number.isNaN(d.getTime())) return '';
    return d.toLocaleTimeString('vi-VN', {
        timeZone: VN_TIME_ZONE,
        hour: '2-digit',
        minute: '2-digit',
    });
}
