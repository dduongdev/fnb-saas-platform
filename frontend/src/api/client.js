const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8081';

/**
 * Base API request function
 * Automatically adds Authorization and X-Tenant-ID headers
 */
export async function apiRequest(endpoint, options = {}) {
  const token = localStorage.getItem('access_token');
  const tenantId = localStorage.getItem('tenant_id');

  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  if (tenantId && !options.skipTenant) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  const data = await response.json();

  if (data.code !== 200) {
    throw new Error(data.message || 'Đã có lỗi xảy ra');
  }

  return data.data;
}

/**
 * Upload file with multipart/form-data
 * Note: Don't set Content-Type header, browser will set it with boundary
 */
export async function uploadFile(endpoint, formData, options = {}) {
  const token = localStorage.getItem('access_token');
  const tenantId = localStorage.getItem('tenant_id');

  const headers = {};

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  if (tenantId && !options.skipTenant) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers,
    body: formData,
  });

  const data = await response.json();

  if (data.code !== 200) {
    throw new Error(data.message || 'Upload thất bại');
  }

  return data.data;
}

// Shorthand methods
export const api = {
  get: (url, options) => apiRequest(url, { method: 'GET', ...options }),
  post: (url, body, options) => apiRequest(url, {
    method: 'POST',
    body: JSON.stringify(body),
    ...options
  }),
  put: (url, body, options) => apiRequest(url, {
    method: 'PUT',
    body: JSON.stringify(body),
    ...options
  }),
  patch: (url, body, options) => apiRequest(url, {
    method: 'PATCH',
    body: JSON.stringify(body),
    ...options
  }),
  delete: (url, options) => apiRequest(url, { method: 'DELETE', ...options }),
  upload: uploadFile,
};

export default api;
