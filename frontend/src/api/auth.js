import { api } from './client';

// Upload avatar
export const uploadAvatar = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.upload('/api/profile/avatar', formData, { skipTenant: true });
};

// Login with internal auth
export const loginWithCredentials = (credentials) => {
    return api.post('/api/auth/login', credentials, { skipTenant: true });
};

// Register new user
export const registerUser = (payload) => {
    return api.post('/api/auth/register', payload, { skipTenant: true });
};

// Get current access key info
export const getAccessKeyInfo = () => {
    return api.get('/api/auth/access-key-info', { skipTenant: true });
};
