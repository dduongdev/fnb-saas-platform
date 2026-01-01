import { api } from './client';

// Get revenue report
export const getRevenueReport = (from, to) => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return api.get(`/api/reports/revenue?${params.toString()}`);
};

// Get top products
export const getTopProducts = (from, to, limit = 5) => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    params.append('limit', limit);
    return api.get(`/api/reports/top-products?${params.toString()}`);
};

// Get peak hours
export const getPeakHours = (from, to) => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return api.get(`/api/reports/peak-hours?${params.toString()}`);
};
