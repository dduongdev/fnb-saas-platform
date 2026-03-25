import { api } from './client';

// Sync user after Keycloak login
export const syncUser = () => api.post('/api/auth/sync', null, { skipTenant: true });

// Upload avatar
export const uploadAvatar = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.upload('/api/profile/avatar', formData, { skipTenant: true });
};

// Login with Keycloak
export const loginWithKeycloak = (username, password) => {
    return api.post('/api/auth/login', { username, password });
};
