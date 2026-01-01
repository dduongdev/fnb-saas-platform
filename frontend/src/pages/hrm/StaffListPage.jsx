import { useState, useEffect } from 'react';
import { Trash2, Users } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Card, Loading, Empty, StatusBadge } from '../../components/common';
import { getStaff, removeStaff } from '../../api/hrm';
import './StaffListPage.css';

export function StaffListPage() {
    const [staff, setStaff] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadStaff();
    }, []);

    const loadStaff = async () => {
        try {
            setLoading(true);
            const data = await getStaff();
            setStaff(data?.content || data || []);
        } catch (error) {
            console.error('Failed to load staff:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleRemove = async (employee) => {
        if (!confirm(`Xóa nhân viên "${employee.fullName || employee.username}"?`)) return;

        try {
            await removeStaff(employee.id);
            await loadStaff();
        } catch (error) {
            alert(error.message);
        }
    };

    if (loading) {
        return (
            <PageLayout title="Quản lý nhân viên">
                <Loading />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Quản lý nhân viên">
            {staff.length === 0 ? (
                <Empty
                    icon={Users}
                    message="Chưa có nhân viên"
                    description="Nhân viên sẽ xuất hiện khi bạn duyệt đơn ứng tuyển"
                />
            ) : (
                <Card padding={false}>
                    <table className="staff-table">
                        <thead>
                            <tr>
                                <th>Họ tên</th>
                                <th>Username</th>
                                <th>Vai trò</th>
                                <th>Trạng thái</th>
                                <th>Ngày vào làm</th>
                                <th>Hành động</th>
                            </tr>
                        </thead>
                        <tbody>
                            {staff.map(employee => (
                                <tr key={employee.id}>
                                    <td className="staff-name">{employee.fullName || '-'}</td>
                                    <td>{employee.username}</td>
                                    <td><StatusBadge status={employee.role} /></td>
                                    <td><StatusBadge status={employee.status} /></td>
                                    <td>{employee.joinedAt || '-'}</td>
                                    <td>
                                        <button
                                            className="action-btn action-btn-danger"
                                            onClick={() => handleRemove(employee)}
                                            title="Xóa nhân viên"
                                        >
                                            <Trash2 size={16} />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </Card>
            )}
        </PageLayout>
    );
}
