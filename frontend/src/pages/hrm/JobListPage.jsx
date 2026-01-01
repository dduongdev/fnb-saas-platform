import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Pencil, Trash2, Users, Briefcase } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Card, Loading, Empty, Modal, ModalFooter, Input, Textarea, StatusBadge } from '../../components/common';
import { getJobPosts, createJobPost, updateJobPost, deleteJobPost } from '../../api/hrm';
import './JobListPage.css';

export function JobListPage() {
    const navigate = useNavigate();
    const [jobs, setJobs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editingJob, setEditingJob] = useState(null);

    const [form, setForm] = useState({
        title: '',
        description: '',
        isActive: true
    });
    const [formLoading, setFormLoading] = useState(false);
    const [formError, setFormError] = useState('');

    useEffect(() => {
        loadJobs();
    }, []);

    const loadJobs = async () => {
        try {
            setLoading(true);
            const data = await getJobPosts();
            setJobs(data?.content || data || []);
        } catch (error) {
            console.error('Failed to load jobs:', error);
        } finally {
            setLoading(false);
        }
    };

    const resetForm = () => {
        setForm({ title: '', description: '', isActive: true });
        setFormError('');
        setEditingJob(null);
    };

    const handleOpenCreate = () => {
        resetForm();
        setShowModal(true);
    };

    const handleOpenEdit = (job) => {
        setEditingJob(job);
        setForm({
            title: job.title,
            description: job.description || '',
            isActive: job.isActive
        });
        setShowModal(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setFormError('');

        if (!form.title.trim()) {
            setFormError('Vui lòng nhập tiêu đề');
            return;
        }

        try {
            setFormLoading(true);

            if (editingJob) {
                await updateJobPost(editingJob.id, form);
            } else {
                await createJobPost(form);
            }

            await loadJobs();
            setShowModal(false);
            resetForm();
        } catch (error) {
            setFormError(error.message);
        } finally {
            setFormLoading(false);
        }
    };

    const handleDelete = async (job) => {
        if (!confirm(`Xóa tin tuyển dụng "${job.title}"?`)) return;

        try {
            await deleteJobPost(job.id);
            await loadJobs();
        } catch (error) {
            alert(error.message);
        }
    };

    const handleViewApplications = (job) => {
        navigate(`/jobs/${job.id}/applications`);
    };

    const formatDate = (date) => {
        if (!date) return '-';
        return new Date(date).toLocaleDateString('vi-VN');
    };

    if (loading) {
        return (
            <PageLayout title="Quản lý tuyển dụng">
                <Loading />
            </PageLayout>
        );
    }

    return (
        <PageLayout
            title="Quản lý tuyển dụng"
            actions={
                <Button onClick={handleOpenCreate}>
                    <Plus size={18} />
                    Thêm tin
                </Button>
            }
        >
            {jobs.length === 0 ? (
                <Empty
                    icon={Briefcase}
                    message="Chưa có tin tuyển dụng"
                    description="Đăng tin tuyển dụng để tìm nhân viên"
                    action={
                        <Button onClick={handleOpenCreate}>
                            <Plus size={18} />
                            Thêm tin đầu tiên
                        </Button>
                    }
                />
            ) : (
                <Card padding={false}>
                    <table className="job-table">
                        <thead>
                            <tr>
                                <th>Tiêu đề</th>
                                <th>Trạng thái</th>
                                <th>Ngày tạo</th>
                                <th>Hành động</th>
                            </tr>
                        </thead>
                        <tbody>
                            {jobs.map(job => (
                                <tr key={job.id}>
                                    <td className="job-title">{job.title}</td>
                                    <td>
                                        <StatusBadge
                                            status={job.isActive ? 'ACTIVE' : 'HIDDEN'}
                                            customLabel={job.isActive ? 'Đang hiển thị' : 'Đã ẩn'}
                                        />
                                    </td>
                                    <td>{formatDate(job.createdAt)}</td>
                                    <td>
                                        <div className="job-actions">
                                            <button
                                                className="action-btn"
                                                onClick={() => handleViewApplications(job)}
                                                title="Xem đơn ứng tuyển"
                                            >
                                                <Users size={16} />
                                            </button>
                                            <button
                                                className="action-btn"
                                                onClick={() => handleOpenEdit(job)}
                                                title="Sửa"
                                            >
                                                <Pencil size={16} />
                                            </button>
                                            <button
                                                className="action-btn action-btn-danger"
                                                onClick={() => handleDelete(job)}
                                                title="Xóa"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </Card>
            )}

            {/* Create/Edit Modal */}
            <Modal
                isOpen={showModal}
                onClose={() => { setShowModal(false); resetForm(); }}
                title={editingJob ? 'Sửa tin tuyển dụng' : 'Thêm tin tuyển dụng'}
            >
                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <Input
                            label="Tiêu đề"
                            placeholder="VD: Tuyển nhân viên phục vụ"
                            value={form.title}
                            onChange={(e) => setForm({ ...form, title: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <Textarea
                            label="Mô tả công việc"
                            placeholder="Mô tả chi tiết yêu cầu, quyền lợi..."
                            value={form.description}
                            onChange={(e) => setForm({ ...form, description: e.target.value })}
                            rows={5}
                        />
                    </div>
                    <div className="form-group">
                        <label className="checkbox-label">
                            <input
                                type="checkbox"
                                checked={form.isActive}
                                onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                            />
                            <span>Hiển thị tin này</span>
                        </label>
                    </div>

                    {formError && <p className="form-error">{formError}</p>}

                    <ModalFooter>
                        <Button variant="secondary" onClick={() => { setShowModal(false); resetForm(); }}>
                            Hủy
                        </Button>
                        <Button type="submit" loading={formLoading}>
                            {editingJob ? 'Lưu thay đổi' : 'Thêm tin'}
                        </Button>
                    </ModalFooter>
                </form>
            </Modal>
        </PageLayout>
    );
}
