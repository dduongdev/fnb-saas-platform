import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, Plus, LogOut } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import { createTenant } from '../../api/tenant';
import { Button, Card, Loading, Empty, Modal, ModalFooter, Input } from '../../components/common';
import './SelectTenantPage.css';

export function SelectTenantPage() {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const { tenants, loading, selectTenant, reloadTenants } = useTenant();

    const [showCreateModal, setShowCreateModal] = useState(false);
    const [createForm, setCreateForm] = useState({ name: '', address: '' });
    const [createLoading, setCreateLoading] = useState(false);
    const [createError, setCreateError] = useState('');

    const handleSelectTenant = async (tenantId) => {
        await selectTenant(tenantId);
        navigate('/pos');
    };

    const handleCreateTenant = async (e) => {
        e.preventDefault();
        setCreateError('');

        if (!createForm.name.trim() || !createForm.address.trim()) {
            setCreateError('Vui lòng điền đầy đủ thông tin');
            return;
        }

        try {
            setCreateLoading(true);
            const newTenant = await createTenant(createForm.name, createForm.address);
            await reloadTenants();
            setShowCreateModal(false);
            setCreateForm({ name: '', address: '' });

            // Auto select the new tenant
            await selectTenant(newTenant.id);
            navigate('/pos');
        } catch (error) {
            setCreateError(error.message);
        } finally {
            setCreateLoading(false);
        }
    };

    if (loading) {
        return <Loading fullPage text="Đang tải danh sách quán..." />;
    }

    return (
        <div className="select-tenant-page">
            <div className="select-tenant-header">
                <div className="select-tenant-user">
                    <span>Xin chào, <strong>{user?.fullName || user?.username}</strong></span>
                    <Button variant="ghost" size="sm" onClick={logout}>
                        <LogOut size={16} />
                        Đăng xuất
                    </Button>
                </div>
            </div>

            <div className="select-tenant-content">
                <h1 className="select-tenant-title">Chọn quán để quản lý</h1>

                {tenants.length === 0 ? (
                    <Empty
                        icon={Store}
                        message="Bạn chưa có quán nào"
                        description="Tạo quán đầu tiên để bắt đầu sử dụng hệ thống"
                        action={
                            <Button onClick={() => setShowCreateModal(true)}>
                                Tạo quán mới
                            </Button>
                        }
                    />
                ) : (
                    <>
                        <div className="tenant-grid">
                            {tenants.map((tenant) => (
                                <Card
                                    key={tenant.id}
                                    className="tenant-card"
                                    onClick={() => handleSelectTenant(tenant.id)}
                                >
                                    <div className="tenant-card-logo">
                                        {tenant.logoUrl ? (
                                            <img src={tenant.logoUrl} alt={tenant.name} />
                                        ) : (
                                            <Store size={32} />
                                        )}
                                    </div>
                                    <div className="tenant-card-info">
                                        <h3 className="tenant-card-name">{tenant.name}</h3>
                                        <p className="tenant-card-address">{tenant.address}</p>
                                        {tenant.ownerId === user?.id && (
                                            <span className="tenant-card-owner">Chủ quán</span>
                                        )}
                                    </div>
                                </Card>
                            ))}
                        </div>

                        <Button
                            variant="secondary"
                            onClick={() => setShowCreateModal(true)}
                            className="create-tenant-btn"
                        >
                            Tạo quán mới
                        </Button>
                    </>
                )}
            </div>

            <Modal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                title="Tạo quán mới"
            >
                <form onSubmit={handleCreateTenant}>
                    <div className="form-group">
                        <Input
                            label="Tên quán"
                            placeholder="VD: Quán Cà Phê ABC"
                            value={createForm.name}
                            onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <Input
                            label="Địa chỉ"
                            placeholder="VD: 123 Nguyễn Văn A, Quận 1"
                            value={createForm.address}
                            onChange={(e) => setCreateForm({ ...createForm, address: e.target.value })}
                            required
                        />
                    </div>

                    {createError && <p className="form-error">{createError}</p>}

                    <ModalFooter>
                        <Button variant="secondary" onClick={() => setShowCreateModal(false)}>
                            Hủy
                        </Button>
                        <Button type="submit" loading={createLoading}>
                            Tạo quán
                        </Button>
                    </ModalFooter>
                </form>
            </Modal>
        </div>
    );
}
