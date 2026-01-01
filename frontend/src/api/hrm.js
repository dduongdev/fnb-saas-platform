import { api } from './client';

// Staff
export const getStaff = (page = 0, size = 10) =>
    api.get(`/api/staff?page=${page}&size=${size}`);

export const removeStaff = (id) => api.delete(`/api/staff/${id}`);

// Job Posts
export const getJobPosts = (page = 0, size = 10) =>
    api.get(`/api/hrm/jobs?page=${page}&size=${size}`);

export const getJobPostDetail = (id) => api.get(`/api/hrm/jobs/${id}`);

export const createJobPost = (data) => api.post('/api/hrm/jobs', data);

export const updateJobPost = (id, data) => api.put(`/api/hrm/jobs/${id}`, data);

export const deleteJobPost = (id) => api.delete(`/api/hrm/jobs/${id}`);

// Public Jobs (no auth)
export const getPublicJobs = (page = 0, size = 10) =>
    api.get(`/api/public/jobs?page=${page}&size=${size}`, { skipTenant: true });

// Recruitment
export const applyToJob = (jobId, coverLetter) =>
    api.post('/api/recruitment/apply', { jobId, coverLetter }, { skipTenant: true });

export const getApplications = (jobId) =>
    api.get(`/api/recruitment/jobs/${jobId}/applications`);

export const processApplication = (id, status) =>
    api.patch(`/api/recruitment/applications/${id}`, { status });
