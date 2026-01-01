import { api } from './client';

// Get payment methods for a tenant
export const getPaymentMethods = (tenantId) =>
    api.get(`/api/public/payment/methods/${tenantId}`, { skipTenant: true });

// Create payment URL (redirect to payment gateway)
export const createPaymentUrl = (orderId, paymentMethodCode) =>
    api.post('/api/public/payment/create-url', { orderId, paymentMethodCode }, { skipTenant: true });
