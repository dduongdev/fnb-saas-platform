import { api, uploadFile } from './client';

// Get my tenants (user's own shops)
export const getMyTenants = () => api.get('/api/tenants/me', { skipTenant: true });

// Get tenant detail (authenticated)
export const getTenantDetail = (id) => api.get(`/api/tenants/${id}`, { skipTenant: true });

// Get public tenant detail (unauthenticated, safe DTO)
export const getPublicTenantDetail = (id) => api.get(`/api/public/tenants/${id}`, { skipTenant: true });

// Get payment config (owner only)
export const getPaymentConfig = (id) => api.get(`/api/tenants/${id}/payment-config`, { skipTenant: true });

// Create new tenant
export const createTenant = (name, address, logo) => {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('address', address);
    if (logo) {
        formData.append('logo', logo);
    }
    return uploadFile('/api/tenants', formData, { skipTenant: true });
};

// Update tenant
export const updateTenant = (id, name, address, logo) => {
    const formData = new FormData();
    if (name) formData.append('name', name);
    if (address) formData.append('address', address);
    if (logo) formData.append('logo', logo);

    const token = localStorage.getItem('access_token');
    const tenantId = localStorage.getItem('tenant_id');
    const headers = {
        'Authorization': `Bearer ${token}`,
    };
    if (tenantId) {
        headers['X-Tenant-ID'] = tenantId;
    }

    return fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8081'}/api/tenants/${id}`, {
        method: 'PUT',
        headers,
        body: formData,
    }).then(res => res.json()).then(data => {
        if (data.code !== 200) throw new Error(data.message);
        return data.data;
    });
};

// Update tenant status
export const updateTenantStatus = (id, isActive) =>
    api.patch(`/api/tenants/${id}/status?isActive=${isActive}`);

// Update payment config
export const updatePaymentConfig = (id, config) =>
    api.put(`/api/tenants/${id}/payment-config`, config);

// Get public tenants list
export const getPublicTenants = (page = 0, size = 10) =>
    api.get(`/api/public/tenants?page=${page}&size=${size}`, { skipTenant: true });

// --- Access Keys ---
export const getAccessKeys = (tenantId) => 
    api.get(`/api/tenants/${tenantId}/access-keys`);

export const createAccessKey = (tenantId, data) => 
    api.post(`/api/tenants/${tenantId}/access-keys`, data);

export const revokeAccessKey = (tenantId, keyId) => 
    api.delete(`/api/tenants/${tenantId}/access-keys/${keyId}`);

export const getAccessKeyRoles = (tenantId) =>
    api.get(`/api/tenants/${tenantId}/access-keys/roles`);

export const getPosActionAudit = ({ page = 0, size = 20, action, userId, accessKeyId, targetType }) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (action) params.set('action', action);
    if (userId) params.set('userId', userId);
    if (accessKeyId) params.set('accessKeyId', accessKeyId);
    if (targetType) params.set('targetType', targetType);
    return api.get(`/api/pos/audit?${params.toString()}`);
};
