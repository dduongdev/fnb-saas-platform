import { useState, useEffect } from 'react';
import { PageLayout } from '../../components/layout';
import { Card, Button, Input, Modal } from '../../components/common';
import { useTenant } from '../../context/TenantContext';
import { useToast } from '../../context/ToastContext';
import { getAccessKeys, createAccessKey, revokeAccessKey, getAccessKeyRoles } from '../../api/tenant';
import './AccessKeySettingsPage.css';

export function AccessKeySettingsPage() {
    const { tenant } = useTenant();
    const toast = useToast();
    const [keys, setKeys] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newName, setNewName] = useState('');
    const [newRole, setNewRole] = useState('WAITER');
    const [supportedRoles, setSupportedRoles] = useState([]);

    useEffect(() => {
        const loadRoles = async () => {
            if (!tenant?.id) return;

            try {
                console.log('[AccessKey] Loading roles from backend');
                const data = await getAccessKeyRoles(tenant.id);
                console.log('[AccessKey] Received roles:', data);

                if (data?.length > 0) {
                    setSupportedRoles(data.map(role => ({
                        value: role,
                        label: role
                            .replace('_', ' ')
                            .toLocaleLowerCase()
                            .replace(/^(.)/, v => v.toUpperCase())
                    })));
                    setNewRole(data[0]);
                }
            } catch (error) {
                console.error('Lỗi khi tải role access key:', error);
                // fallback role list giữ nguyên
            }
        };

        const loadAll = async () => {
            if (!tenant?.id) return;
            await loadRoles();
            await fetchKeys();
        };

        loadAll();
    }, [tenant?.id]);

    const fetchKeys = async () => {
        try {
            const data = await getAccessKeys(tenant.id);
            setKeys(data);
        } catch (error) {
            toast.error(error.message || 'Lỗi khi tải danh sách khoá');
        } finally {
            setLoading(false);
        }
    };



    const handleCreateKey = async (e) => {
        e.preventDefault();
        if (!newName.trim()) return;

        try {
            await createAccessKey(tenant.id, {
                name: newName,
                role: newRole
            });
            toast.success('Tạo khoá truy cập thành công!');
            setShowCreateModal(false);
            setNewName('');
            fetchKeys();
        } catch (error) {
            toast.error(error.message || 'Lỗi khi tạo khoá');
        }
    };

    const handleRevokeKey = async (keyId) => {
        if (!window.confirm('Bạn có chắc muốn xóa khoá này? Nhân viên sử dụng khoá này sẽ bị đăng xuất.')) {
            return;
        }

        try {
            await revokeAccessKey(tenant.id, keyId);
            toast.success('Đã xóa khoá!');
            fetchKeys();
        } catch (error) {
            toast.error(error.message || 'Lỗi khi xóa khoá');
        }
    };

    const handleCopy = (keyString) => {
        navigator.clipboard.writeText(keyString);
        toast.success('Đã copy khoá vào bộ nhớ đệm');
    };

    return (
        <PageLayout
            title="Quản lý nhân viên (Access Keys)"
            actions={
                <Button onClick={() => setShowCreateModal(true)}>
                    + Tạo khoá mới
                </Button>
            }
        >
            <div className="access-keys-intro">
                <p>Quản lý khoá truy cập của nhân viên để bảo mật hệ thống POS. Bạn có thể tạo khoá mới, sao chép và thu hồi khi cần thiết.</p>
            </div>

            <div className="access-keys-container">
                <Card title="Danh sách khoá hiện tại">
                    {loading ? (
                        <div>Đang tải...</div>
                    ) : keys.length === 0 ? (
                        <div className="empty-state">Chưa có khoá truy cập nào</div>
                    ) : (
                        <div className="keys-list">
                            {keys.map(key => (
                                <div key={key.id} className={`key-item ${!key.isActive ? 'inactive' : ''}`}>
                                    <div className="key-info">
                                        <h4>{key.name}</h4>
                                        <div className="key-meta">
                                            <div className={`key-status ${key.isActive ? 'active' : 'inactive'}`}>
                                                {key.isActive ? 'Đang hoạt động' : 'Đã xóa'}
                                            </div>
                                            <div className="key-role">
                                                {key.role}
                                            </div>
                                        </div>
                                        <div className="key-string-box">
                                            <code>{key.keyString}</code>
                                            {key.isActive && (
                                                <Button
                                                    variant="outline"
                                                    size="small"
                                                    onClick={() => handleCopy(key.keyString)}
                                                >
                                                    Sao chép
                                                </Button>
                                            )}
                                        </div>
                                    </div>
                                    <div className="key-actions">
                                        {key.isActive && (
                                            <Button
                                                variant="danger"
                                                onClick={() => handleRevokeKey(key.id)}
                                            >
                                                Xóa
                                            </Button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </Card>
            </div>

            <Modal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                title="Tạo khoá nhân viên mới"
            >
                <form onSubmit={handleCreateKey}>
                    <div className="form-group">
                        <Input
                            label="Tên nhận diện (VD: Ca Sáng, Nhân viên A)"
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            required
                            autoFocus
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="role-select" className="input-label">Chọn role</label>
                        <select
                            id="role-select"
                            value={newRole}
                            onChange={(e) => setNewRole(e.target.value)}
                            className="select-input"
                        >
                            {supportedRoles.map(role => (
                                <option key={role.value} value={role.value}>{role.label}</option>
                            ))}
                        </select>
                    </div>
                    <div className="form-actions">
                        <Button type="button" variant="secondary" onClick={() => setShowCreateModal(false)}>
                            Hủy
                        </Button>
                        <Button type="submit">
                            Tạo Khoá Mới
                        </Button>
                    </div>
                </form>
            </Modal>
        </PageLayout>
    );
}
