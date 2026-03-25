import { api, uploadFile } from './client';

// Get my tenants (user's own shops)
export const getMyTenants = () => api.get('/api/tenants/me', { skipTenant: true });

// Get tenant detail
export const getTenantDetail = (id) => api.get(`/api/tenants/${id}`, { skipTenant: true });

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
    return fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8081'}/api/tenants/${id}`, {
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${token}`,
        },
        body: formData,
    }).then(res => res.json()).then(data => {
        if (data.code !== 200) throw new Error(data.message);
        return data.data;
    });
};

// Update tenant status
export const updateTenantStatus = (id, isActive) =>
    api.patch(`/api/tenants/${id}/status?isActive=${isActive}`, null, { skipTenant: true });

// Update payment config
export const updatePaymentConfig = (id, config) =>
    api.put(`/api/tenants/${id}/payment-config`, config, { skipTenant: true });

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
