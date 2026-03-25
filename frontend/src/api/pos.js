import { api } from './client';

// ==================== Tables ====================
export const getTables = () => api.get('/api/pos/tables');

export const createTable = (name) =>
    api.post(`/api/pos/tables?name=${encodeURIComponent(name)}`, null);

export const deleteTable = (id) => api.delete(`/api/pos/tables/${id}`);

// ==================== Public/Customer APIs (no auth required) ====================
export const getPublicMenu = (tenantId) => {
    const url = tenantId
        ? `/api/pos/public/menu?tenantId=${encodeURIComponent(tenantId)}`
        : '/api/pos/public/menu';
    return api.get(url);
};

export const getTableInfo = (tableId) =>
    api.get(`/api/pos/public/info/${tableId}`, { skipTenant: true });
