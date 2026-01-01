import { useState, useEffect } from 'react';
import { Save, Upload, Power, PowerOff, Store } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { Button, Input, Card, Loading, ConfirmModal } from '../../components/common';
import { useTenant } from '../../context/TenantContext';
import { useToast } from '../../context/ToastContext';
import { updateTenant, updateTenantStatus } from '../../api/tenant';
import './TenantSettingsPage.css';

export function TenantSettingsPage() {
    const { tenant, refreshTenant } = useTenant();
    const toast = useToast();

    const [form, setForm] = useState({
        name: '',
        address: ''
    });
    const [selectedLogo, setSelectedLogo] = useState(null);
    const [logoPreview, setLogoPreview] = useState(null);
    const [saving, setSaving] = useState(false);
    const [showStatusModal, setShowStatusModal] = useState(false);
    const [statusLoading, setStatusLoading] = useState(false);

    useEffect(() => {
        if (tenant) {
            setForm({
                name: tenant.name || '',
                address: tenant.address || ''
            });
            setLogoPreview(tenant.logoUrl || null);
        }
    }, [tenant]);

    const handleLogoChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            setSelectedLogo(file);
            setLogoPreview(URL.createObjectURL(file));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        
        if (!form.name.trim()) {
            toast.error('Vui lòng nhập tên quán');
            return;
        }

        try {
            setSaving(true);
            await updateTenant(tenant.id, form.name, form.address, selectedLogo);
            toast.success('Đã cập nhật thông tin quán');
            await refreshTenant?.();
            setSelectedLogo(null);
        } catch (error) {
            toast.error('Lỗi: ' + error.message);
        } finally {
            setSaving(false);
        }
    };

    const handleToggleStatus = async () => {
        try {
            setStatusLoading(true);
            const newStatus = !tenant.isActive;
            await updateTenantStatus(tenant.id, newStatus);
            toast.success(newStatus ? 'Đã mở lại quán' : 'Đã tạm đóng quán');
            await refreshTenant?.();
            setShowStatusModal(false);
        } catch (error) {
            toast.error('Lỗi: ' + error.message);
        } finally {
            setStatusLoading(false);
        }
    };

    if (!tenant) return <PageLayout title="Cài đặt quán"><Loading /></PageLayout>;

    return (
        <PageLayout title="Cài đặt quán">
            <div className="settings-container">
                <Card className="settings-card">
                    <div className="settings-header">
                        <Store size={24} />
                        <h3>Thông tin cửa hàng</h3>
                    </div>

                    <form onSubmit={handleSubmit} className="settings-form">
                        <div className="form-group logo-group">
                            <label>Logo quán</label>
                            <div className="logo-upload">
                                {logoPreview ? (
                                    <img src={logoPreview} alt="Logo" className="logo-preview" />
                                ) : (
                                    <div className="logo-placeholder">
                                        <Store size={48} />
                                    </div>
                                )}
                                <label className="upload-btn">
                                    <Upload size={16} />
                                    <span>Đổi logo</span>
                                    <input
                                        type="file"
                                        accept="image/*"
                                        onChange={handleLogoChange}
                                        hidden
                                    />
                                </label>
                            </div>
                        </div>

                        <div className="form-group">
                            <Input
                                label="Tên quán"
                                value={form.name}
                                onChange={(e) => setForm({ ...form, name: e.target.value })}
                                placeholder="Nhập tên quán..."
                                required
                            />
                        </div>

                        <div className="form-group">
                            <Input
                                label="Địa chỉ"
                                value={form.address}
                                onChange={(e) => setForm({ ...form, address: e.target.value })}
                                placeholder="Nhập địa chỉ..."
                            />
                        </div>

                        <div className="form-actions">
                            <Button type="submit" loading={saving}>
                                <Save size={18} />
                                Lưu thay đổi
                            </Button>
                        </div>
                    </form>

                    {/* Trạng thái hoạt động */}
                    <div className="status-section">
                        <div className="status-header">
                            {tenant.isActive ? <Power size={20} /> : <PowerOff size={20} />}
                            <span>Trạng thái:</span>
                            <span className={`status-value ${tenant.isActive ? 'active' : 'inactive'}`}>
                                {tenant.isActive ? 'Hoạt động' : 'Tạm đóng'}
                            </span>
                        </div>
                        <p className="status-desc">
                            {tenant.isActive
                                ? 'Khách hàng có thể xem menu và đặt món qua QR code.'
                                : 'Khách hàng không thể truy cập menu của quán.'}
                        </p>
                        <Button
                            variant={tenant.isActive ? 'danger' : 'primary'}
                            size="sm"
                            onClick={() => setShowStatusModal(true)}
                        >
                            {tenant.isActive ? (
                                <>
                                    <PowerOff size={16} />
                                    Tạm đóng quán
                                </>
                            ) : (
                                <>
                                    <Power size={16} />
                                    Mở lại quán
                                </>
                            )}
                        </Button>
                    </div>
                </Card>
            </div>

            <ConfirmModal
                isOpen={showStatusModal}
                onClose={() => setShowStatusModal(false)}
                onConfirm={handleToggleStatus}
                title={tenant.isActive ? 'Tạm đóng quán?' : 'Mở lại quán?'}
                message={tenant.isActive
                    ? 'Khách hàng sẽ không thể truy cập menu của quán.'
                    : 'Khách hàng sẽ có thể xem menu và đặt món.'}
                confirmText={tenant.isActive ? 'Tạm đóng' : 'Mở lại'}
                variant={tenant.isActive ? 'danger' : 'primary'}
                loading={statusLoading}
            />
        </PageLayout>
    );
}
