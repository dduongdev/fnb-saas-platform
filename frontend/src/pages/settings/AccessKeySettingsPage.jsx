import { useState, useEffect } from 'react';
import { PageLayout } from '../../components/layout';
import { Card, Button, Input, Modal, Badge } from '../../components/common';
import { useTenant } from '../../context/TenantContext';
import { useToast } from '../../context/ToastContext';
import { getAccessKeys, createAccessKey, revokeAccessKey } from '../../api/tenant';
import { Copy, Trash2, Key, UserCheck } from 'lucide-react';
import './AccessKeySettingsPage.css';

export function AccessKeySettingsPage() {
    const { tenant } = useTenant();
    const toast = useToast();
    const [keys, setKeys] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newName, setNewName] = useState('');

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

    useEffect(() => {
        if (tenant?.id) {
            fetchKeys();
        }
    }, [tenant?.id]);

    const handleCreateKey = async (e) => {
        e.preventDefault();
        if (!newName.trim()) return;

        try {
            await createAccessKey(tenant.id, {
                name: newName,
                role: 'WAITER'
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
        if (!window.confirm('Bạn có chắc muốn vô hiệu hoá khoá này? Nhân viên sử dụng khoá này sẽ bị đăng xuất.')) {
            return;
        }

        try {
            await revokeAccessKey(tenant.id, keyId);
            toast.success('Đã vô hiệu hoá khoá!');
            fetchKeys();
        } catch (error) {
            toast.error(error.message || 'Lỗi khi vô hiệu hoá khoá');
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
                    + Tạo khoá Waiter mới
                </Button>
            }
        >
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
                                            <Badge variant={key.isActive ? "success" : "danger"}>
                                                {key.isActive ? 'Đang hoạt động' : 'Đã vô hiệu hoá'}
                                            </Badge>
                                            <Badge variant="warning">
                                                <UserCheck size={12} /> {key.role}
                                            </Badge>
                                        </div>
                                        <div className="key-string-box">
                                            <code>{key.keyString}</code>
                                            {key.isActive && (
                                                <Button
                                                    variant="secondary"
                                                    size="small"
                                                    onClick={() => handleCopy(key.keyString)}
                                                    icon={<Copy size={16} />}
                                                >
                                                    Copy
                                                </Button>
                                            )}
                                        </div>
                                    </div>
                                    <div className="key-actions">
                                        {key.isActive && (
                                            <Button
                                                variant="danger"
                                                icon={<Trash2 size={16} />}
                                                onClick={() => handleRevokeKey(key.id)}
                                            >
                                                Vô hiệu hoá
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
