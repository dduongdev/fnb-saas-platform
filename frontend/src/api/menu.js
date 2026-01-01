import { api } from './client';

// Categories
export const getCategories = () => api.get('/api/categories');

export const createCategory = (name, order = 1) =>
    api.post(`/api/categories?name=${encodeURIComponent(name)}&order=${order}`, null);

export const deleteCategory = (id) => api.delete(`/api/categories/${id}`);

// Products
export const getProducts = (params = {}) => {
    const searchParams = new URLSearchParams();
    if (params.categoryId) searchParams.append('categoryId', params.categoryId);
    if (params.status) searchParams.append('status', params.status);
    if (params.keyword) searchParams.append('name', params.keyword); // Map keyword to name param
    if (params.page !== undefined) searchParams.append('page', params.page);
    if (params.size !== undefined) searchParams.append('size', params.size);
    const queryString = searchParams.toString();
    return api.get(`/api/products${queryString ? `?${queryString}` : ''}`);
};

export const getProductDetail = (id) => api.get(`/api/products/${id}`);

export const createProduct = (data) => {
    const formData = new FormData();
    formData.append('categoryId', data.categoryId);
    formData.append('name', data.name);
    formData.append('price', data.price);
    if (data.description) formData.append('description', data.description);
    if (data.images && data.images.length > 0) {
        data.images.forEach(img => formData.append('images', img));
    }
    return api.upload('/api/products', formData);
};

export const updateProduct = (id, data) => {
    const params = new URLSearchParams();
    if (data.name) params.append('name', data.name);
    if (data.price) params.append('price', data.price);
    if (data.description !== undefined) params.append('description', data.description);
    if (data.categoryId) params.append('categoryId', data.categoryId);
    if (data.status) params.append('status', data.status);

    const token = localStorage.getItem('access_token');
    const tenantId = localStorage.getItem('tenant_id');

    return fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8081'}/api/products/${id}?${params.toString()}`, {
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${token}`,
            'X-Tenant-ID': tenantId,
        },
    }).then(async res => {
        const data = await res.json();
        if (data.code !== 200) throw new Error(data.message);
        return data.data;
    });
};

export const deleteProduct = (id) => api.delete(`/api/products/${id}`);

export const updateProductImages = (id, images) => {
    const formData = new FormData();
    images.forEach(img => formData.append('images', img));
    return api.upload(`/api/products/${id}/images`, formData);
};

export const deleteProductImage = (imageId) => api.delete(`/api/products/images/${imageId}`);
