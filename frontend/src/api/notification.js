import { api } from './client';

/**
 * Notification API - Quản lý thông báo cho chủ quán/nhân viên
 */

/**
 * Lấy danh sách notifications với phân trang
 * @param {number} page - Số trang (mặc định 0)
 * @param {number} size - Số items mỗi trang (mặc định 20)
 * @returns {Promise<{content: Array, totalElements: number, totalPages: number}>}
 */
export const getNotifications = (page = 0, size = 20) =>
    api.get(`/api/notifications?page=${page}&size=${size}`);

/**
 * Lấy notifications chưa đọc
 * @returns {Promise<Array>}
 */
export const getUnreadNotifications = () =>
    api.get('/api/notifications/unread');

/**
 * Đếm số notifications chưa đọc
 * @returns {Promise<number>}
 */
export const getUnreadCount = () =>
    api.get('/api/notifications/unread/count');

/**
 * Lấy notifications gần đây (cho dropdown header)
 * @param {number} limit - Số lượng tối đa (mặc định 10)
 * @returns {Promise<Array>}
 */
export const getRecentNotifications = (limit = 10) =>
    api.get(`/api/notifications/recent?limit=${limit}`);

/**
 * Đánh dấu notification đã đọc
 * @param {number} id - ID của notification
 * @returns {Promise<string>}
 */
export const markNotificationAsRead = (id) =>
    api.post(`/api/notifications/${id}/read`);

/**
 * Đánh dấu tất cả notifications đã đọc
 * @returns {Promise<string>}
 */
export const markAllNotificationsAsRead = () =>
    api.post('/api/notifications/read-all');
