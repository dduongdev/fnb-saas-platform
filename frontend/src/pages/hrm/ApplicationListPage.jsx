import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Check, X, FileText } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Card, Loading, Empty, StatusBadge, Modal, ModalFooter } from '../../components/common';
import { getJobPostDetail, getApplications, processApplication } from '../../api/hrm';
import './ApplicationListPage.css';

export function ApplicationListPage() {
    const { jobId } = useParams();
    const navigate = useNavigate();
    const [job, setJob] = useState(null);
    const [applications, setApplications] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedApp, setSelectedApp] = useState(null);
    const [processing, setProcessing] = useState(false);

    useEffect(() => {
        if (jobId) {
            loadData();
        }
    }, [jobId]);

    const loadData = async () => {
        try {
            setLoading(true);
            const [jobData, appsData] = await Promise.all([
                getJobPostDetail(jobId),
                getApplications(jobId)
            ]);
            setJob(jobData);
            setApplications(appsData || []);
        } catch (error) {
            console.error('Failed to load data:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleProcess = async (applicationId, status) => {
        try {
            setProcessing(true);
            await processApplication(applicationId, status);
            await loadData();
            setSelectedApp(null);
        } catch (error) {
            alert(error.message);
        } finally {
            setProcessing(false);
        }
    };

    const formatDate = (date) => {
        if (!date) return '-';
        return new Date(date).toLocaleDateString('vi-VN');
    };

    if (loading) {
        return (
            <PageLayout title="Đơn ứng tuyển">
                <Loading />
            </PageLayout>
        );
    }

    return (
        <PageLayout
            title={`Đơn ứng tuyển: ${job?.title || ''}`}
            actions={
                <Button variant="secondary" onClick={() => navigate('/jobs')}>
                    <ArrowLeft size={18} />
                    Quay lại
                </Button>
            }
        >
            {applications.length === 0 ? (
                <Empty
                    icon={FileText}
                    message="Chưa có đơn ứng tuyển"
                    description="Khi có người ứng tuyển, đơn sẽ hiển thị tại đây"
                />
            ) : (
                <Card padding={false}>
                    <table className="application-table">
                        <thead>
                            <tr>
                                <th>Ứng viên</th>
                                <th>Thư ứng tuyển</th>
                                <th>Ngày ứng tuyển</th>
                                <th>Trạng thái</th>
                                <th>Hành động</th>
                            </tr>
                        </thead>
                        <tbody>
                            {applications.map(app => (
                                <tr key={app.id}>
                                    <td className="applicant-name">
                                        {app.applicantName || app.applicantUsername || 'Ứng viên'}
                                    </td>
                                    <td className="cover-letter">
                                        {app.coverLetter ? (
                                            <button
                                                className="view-letter-btn"
                                                onClick={() => setSelectedApp(app)}
                                            >
                                                Xem thư
                                            </button>
                                        ) : (
                                            <span className="no-letter">Không có</span>
                                        )}
                                    </td>
                                    <td>{formatDate(app.appliedAt || app.createdAt)}</td>
                                    <td>
                                        <StatusBadge status={app.status} />
                                    </td>
                                    <td>
                                        {app.status === 'PENDING' && (
                                            <div className="application-actions">
                                                <button
                                                    className="action-btn action-btn-success"
                                                    onClick={() => handleProcess(app.id, 'APPROVED')}
                                                    disabled={processing}
                                                    title="Duyệt"
                                                >
                                                    <Check size={16} />
                                                </button>
                                                <button
                                                    className="action-btn action-btn-danger"
                                                    onClick={() => handleProcess(app.id, 'REJECTED')}
                                                    disabled={processing}
                                                    title="Từ chối"
                                                >
                                                    <X size={16} />
                                                </button>
                                            </div>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </Card>
            )}

            {/* Cover Letter Modal */}
            <Modal
                isOpen={!!selectedApp}
                onClose={() => setSelectedApp(null)}
                title="Thư ứng tuyển"
            >
                {selectedApp && (
                    <>
                        <div className="letter-header">
                            <strong>{selectedApp.applicantName || selectedApp.applicantUsername}</strong>
                            <span className="letter-date">{formatDate(selectedApp.appliedAt || selectedApp.createdAt)}</span>
                        </div>
                        <div className="letter-content">
                            {selectedApp.coverLetter || 'Không có nội dung'}
                        </div>

                        {selectedApp.status === 'PENDING' && (
                            <ModalFooter>
                                <Button
                                    variant="danger"
                                    onClick={() => handleProcess(selectedApp.id, 'REJECTED')}
                                    loading={processing}
                                >
                                    <X size={16} />
                                    Từ chối
                                </Button>
                                <Button
                                    onClick={() => handleProcess(selectedApp.id, 'APPROVED')}
                                    loading={processing}
                                >
                                    <Check size={16} />
                                    Duyệt nhận
                                </Button>
                            </ModalFooter>
                        )}
                    </>
                )}
            </Modal>
        </PageLayout>
    );
}
