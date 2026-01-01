import { api } from './client';

/**
 * Session API - Quản lý phiên phục vụ (REFACTORED - Session-based model)
 * 
 * CRITICAL: Backend đã được refactor, loại bỏ merge/split/transfer phức tạp.
 * Chỉ còn: Attach Table (gộp bàn) và Detach Table (tách bàn).
 */

// ==================== Staff Session Management ====================

/**
 * Mở bàn (tạo session mới với status ACTIVE)
 * @param {Object} params
 * @param {number} params.tableId - ID bàn
 * @param {number} [params.guestCount] - Số khách (optional)
 * @param {string} [params.note] - Ghi chú (optional)
 * @returns {Promise<SessionResponse>}
 */
export const openSession = ({ tableId, guestCount = null, note = null }) =>
    api.post('/api/pos/sessions', { tableId, guestCount, note });

/**
 * Lấy session theo ID
 * @param {number} sessionId
 * @returns {Promise<SessionResponse>}
 */
export const getSession = (sessionId) =>
    api.get(`/api/pos/sessions/${sessionId}`);

/**
 * Lấy session theo tableId (auto-create nếu chưa có)
 * @param {number} tableId
 * @returns {Promise<SessionResponse>}
 */
export const getOrCreateSessionByTable = (tableId) =>
    api.get(`/api/pos/sessions/table/${tableId}`);

/**
 * Thêm món vào session
 * @param {number} sessionId
 * @param {Array} items - [{productId, quantity, note?}]
 * @returns {Promise<string>}
 */
export const addItemsToSession = (sessionId, items) =>
    api.post(`/api/pos/sessions/${sessionId}/items`, {
        items: items.map(item => ({
            productId: item.productId,
            quantity: item.quantity || 1,
            note: item.note || null
        }))
    });

/**
 * Xóa món khỏi session
 * @param {number} sessionId
 * @param {number} itemId - ID của order item cần xóa
 * @returns {Promise<string>}
 */
export const removeSessionItem = (sessionId, itemId) =>
    api.delete(`/api/pos/sessions/${sessionId}/items/${itemId}`);

/**
 * Cập nhật số lượng món (US-14)
 * @param {number} sessionId
 * @param {number} itemId
 * @param {number} quantity - Số lượng mới (>= 1)
 * @returns {Promise<string>}
 */
export const updateSessionItem = (sessionId, itemId, quantity) =>
    api.patch(`/api/pos/sessions/${sessionId}/items/${itemId}`, { quantity });

/**
 * Đánh dấu món đã mang ra (SERVE)
 * Chỉ cho phép với món có status PENDING.
 * @param {number} sessionId
 * @param {number} itemId
 * @returns {Promise<string>}
 * @throws {Error} 409 nếu món không ở trạng thái PENDING
 */
export const serveItem = (sessionId, itemId) =>
    api.post(`/api/pos/sessions/${sessionId}/items/${itemId}/serve`);


/**
 * Gộp bàn (Attach table vào session)
 * @param {number} sessionId
 * @param {number} tableId - ID bàn cần gộp
 * @returns {Promise<string>}
 */
export const attachTable = (sessionId, tableId) =>
    api.post(`/api/pos/sessions/${sessionId}/tables`, { tableId });

/**
 * Tách bàn (Detach table khỏi session)
 * @param {number} sessionId
 * @param {number} tableId - ID bàn cần tách
 * @returns {Promise<string>}
 */
export const detachTable = (sessionId, tableId) =>
    api.delete(`/api/pos/sessions/${sessionId}/tables/${tableId}`);

/**
 * Chuyển bàn (Composite operation: Attach new + Detach old)
 * @param {number} sessionId
 * @param {number} newTableId - ID bàn mới
 * @param {number} oldTableId - ID bàn cũ
 * @returns {Promise<void>}
 */
export const transferTable = async (sessionId, newTableId, oldTableId) => {
    await attachTable(sessionId, newTableId);
    await detachTable(sessionId, oldTableId);
};

/**
 * Thanh toán và đóng session
 * @param {number} sessionId
 * @param {string} method - CASH, VNPAY
 * @returns {Promise<InvoiceDto|{paymentUrl: string}>}
 */
export const paySession = (sessionId, method) =>
    api.post(`/api/pos/sessions/${sessionId}/pay`, { method });

/**
 * Hủy session
 * @param {number} sessionId
 * @param {string} [reason] - Lý do hủy
 * @returns {Promise<string>}
 */
export const cancelSession = (sessionId, reason = null) =>
    api.post(`/api/pos/sessions/${sessionId}/cancel`, { reason });

// ==================== SESSION LIST APIs ====================

/**
 * Lấy danh sách session đang active
 * @returns {Promise<SessionResponse[]>}
 */
export const getActiveSessions = () =>
    api.get('/api/pos/sessions/active');

// ==================== PENDING SESSION APIs ====================

/**
 * Lấy danh sách session đang chờ xác nhận
 * Dùng cho nhân viên xem các order từ khách quét QR
 * @returns {Promise<SessionResponse[]>}
 */
export const getPendingSessions = () =>
    api.get('/api/pos/sessions/pending');

/**
 * Xác nhận session (PENDING → ACTIVE)
 * @param {number} sessionId
 * @returns {Promise<SessionResponse>}
 */
export const confirmSession = (sessionId) =>
    api.post(`/api/pos/sessions/${sessionId}/confirm`);

/**
 * Từ chối session (PENDING → CANCELLED)
 * @param {number} sessionId
 * @param {string} [reason] - Lý do từ chối
 * @returns {Promise<string>}
 */
export const rejectSession = (sessionId, reason = null) =>
    api.post(`/api/pos/sessions/${sessionId}/reject`, { reason });

// ==================== CUSTOMER ORDER APIs (Public) ====================

/**
 * Khách đặt món qua QR - tạo pending session
 * @param {Object} params
 * @param {number} params.tableId - ID bàn
 * @param {Array} params.items - [{productId, quantity, note?}]
 * @param {string} [params.customerNote] - Ghi chú của khách
 * @returns {Promise<CustomerOrderResponse>}
 */
export const createCustomerOrder = ({ tableId, items, customerNote = null }) =>
    api.post('/api/pos/public/sessions', { tableId, items, customerNote });

/**
 * Khách kiểm tra trạng thái order
 * @param {number} sessionId
 * @returns {Promise<CustomerOrderResponse>}
 */
export const getCustomerOrderStatus = (sessionId) =>
    api.get(`/api/pos/public/sessions/${sessionId}`);

/**
 * Khách thêm món vào session đang active
 * @param {number} sessionId
 * @param {Object} params
 * @param {number} params.tableId
 * @param {Array} params.items - [{productId, quantity, note?}]
 * @returns {Promise<CustomerOrderResponse>}
 */
export const addCustomerItems = (sessionId, { tableId, items }) =>
    api.post(`/api/pos/public/sessions/${sessionId}/items`, { tableId, items });
