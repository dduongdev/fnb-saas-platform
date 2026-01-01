import { api } from './client';

// Lấy danh sách phương thức thanh toán của quán (Public)
export const getPublicPaymentMethods = async (tenantId) => {
    // API này đã có sẵn trong PaymentController: @GetMapping("/methods/{tenantId}")
    const response = await api.get(`/api/public/payment/methods/${tenantId}`);
    return response;
};

// Gửi yêu cầu thanh toán / Gọi bill (Public)
export const requestPayment = async (sessionId, tenantId) => {
    // Cần truyền tenantId vào header để backend biết gửi noti cho tenant nào
    const headers = tenantId ? { 'X-Tenant-ID': tenantId } : {};
    const response = await api.post(
        `/api/pos/public/sessions/${sessionId}/request-payment`,
        {},
        { headers }
    );
    return response;
};

// Tạo URL thanh toán Online (VNPAY/MOMO)
export const createPaymentUrl = async (paymentData, tenantId) => {
    // paymentData: { orderId, paymentMethodCode }
    const headers = tenantId ? { 'X-Tenant-ID': tenantId } : {};
    const response = await api.post(
        '/api/public/payment/create-url',
        paymentData,
        { headers }
    );
    return response;
};
