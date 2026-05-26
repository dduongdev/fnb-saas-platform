import { useState, useEffect } from 'react';
import { CreditCard, Save } from 'lucide-react';
import { useToast } from '../../context/ToastContext';
import { PageLayout } from '../../components/layout';
import { Button, Card, CardHeader, CardTitle, CardContent, Input, Loading } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import { getPaymentConfig, updatePaymentConfig } from '../../api/tenant';
import './PaymentSettingsPage.css';

export function PaymentSettingsPage() {
    const toast = useToast();
    const { tenant, selectTenant } = useTenant();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    // Config state matches PaymentConfigDto structure
    const [config, setConfig] = useState({
        vnpay: {
            tmnCode: '',
            hashSecret: '',
            enabled: false
        },
        momo: {
            partnerCode: '',
            accessKey: '',
            secretKey: '',
            enabled: false
        },
        paypal: {
            clientId: '',
            clientSecret: '',
            mode: 'sandbox',
            enabled: false
        }
    });

    useEffect(() => {
        if (tenant?.id) {
            loadConfig();
        }
    }, [tenant?.id]);

    const loadConfig = async () => {
        try {
            setLoading(true);
            // Fetch payment config directly from secure endpoint
            const paymentConfig = await getPaymentConfig(tenant.id);

            if (paymentConfig) {
                // Merge with default structure to ensure all fields exist
                setConfig(prev => ({
                    vnpay: { ...prev.vnpay, ...paymentConfig.vnpay },
                    momo: { ...prev.momo, ...paymentConfig.momo },
                    paypal: { ...prev.paypal, ...paymentConfig.paypal }
                }));
            }
        } catch (error) {
            console.error('Failed to load payment config:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (e) => {
        e.preventDefault();
        try {
            setSaving(true);
            await updatePaymentConfig(tenant.id, config);
            toast.success('Đã lưu cấu hình thanh toán');
            // Refresh tenant context
            await selectTenant(tenant.id);
        } catch (error) {
            console.error('Save failed:', error);
            toast.error('Lỗi khi lưu cấu hình: ' + error.message);
        } finally {
            setSaving(false);
        }
    };

    const updateVnpay = (field, value) => {
        setConfig(prev => ({
            ...prev,
            vnpay: { ...prev.vnpay, [field]: value }
        }));
    };

    const updateMomo = (field, value) => {
        setConfig(prev => ({
            ...prev,
            momo: { ...prev.momo, [field]: value }
        }));
    };

    if (loading) {
        return (
            <PageLayout title="Cấu hình thanh toán">
                <Loading />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Cấu hình thanh toán">
            <form onSubmit={handleSave} className="payment-settings-form">

                {/* VNPAY Config */}
                <Card className="payment-provider-card">
                    <CardHeader>
                        <div className="provider-header">
                            <div className="provider-info">
                                <CreditCard className="provider-icon" />
                                <CardTitle>VNPAY</CardTitle>
                            </div>
                            <div className="provider-toggle">
                                <label className="switch">
                                    <input
                                        type="checkbox"
                                        checked={config.vnpay.enabled}
                                        onChange={(e) => updateVnpay('enabled', e.target.checked)}
                                    />
                                    <span className="slider round"></span>
                                </label>
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent>
                        <div className={`provider-fields ${!config.vnpay.enabled ? 'disabled' : ''}`}>
                            <Input
                                label="Terminal Code (TmnCode)"
                                value={config.vnpay.tmnCode || ''}
                                onChange={(e) => updateVnpay('tmnCode', e.target.value)}
                                placeholder="Nhập mã terminal"
                                disabled={!config.vnpay.enabled}
                            />
                            <Input
                                label="Hash Secret"
                                value={config.vnpay.hashSecret || ''}
                                onChange={(e) => updateVnpay('hashSecret', e.target.value)}
                                placeholder="Nhập chuỗi bí mật"
                                type="password"
                                disabled={!config.vnpay.enabled}
                            />
                        </div>
                    </CardContent>
                </Card>

                {/* MOMO Config */}
                <Card className="payment-provider-card">
                    <CardHeader>
                        <div className="provider-header">
                            <div className="provider-info">
                                <div className="provider-logo momo-logo">M</div>
                                <CardTitle>MoMo</CardTitle>
                            </div>
                            <div className="provider-toggle">
                                <label className="switch">
                                    <input
                                        type="checkbox"
                                        checked={config.momo.enabled}
                                        onChange={(e) => updateMomo('enabled', e.target.checked)}
                                    />
                                    <span className="slider round"></span>
                                </label>
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent>
                        <div className={`provider-fields ${!config.momo.enabled ? 'disabled' : ''}`}>
                            <Input
                                label="Partner Code"
                                value={config.momo.partnerCode || ''}
                                onChange={(e) => updateMomo('partnerCode', e.target.value)}
                                placeholder="Nhập partner code"
                                disabled={!config.momo.enabled}
                            />
                            <Input
                                label="Access Key"
                                value={config.momo.accessKey || ''}
                                onChange={(e) => updateMomo('accessKey', e.target.value)}
                                placeholder="Nhập access key"
                                type="password"
                                disabled={!config.momo.enabled}
                            />
                            <Input
                                label="Secret Key"
                                value={config.momo.secretKey || ''}
                                onChange={(e) => updateMomo('secretKey', e.target.value)}
                                placeholder="Nhập secret key"
                                type="password"
                                disabled={!config.momo.enabled}
                            />
                        </div>
                    </CardContent>
                </Card>

                {/* PAYPAL Config */}
                <Card className="payment-provider-card">
                    <CardHeader>
                        <div className="provider-header">
                            <div className="provider-info">
                                <span className="provider-logo text-logo" style={{ color: '#003087', fontWeight: 'bold' }}>P</span>
                                <CardTitle>PayPal</CardTitle>
                            </div>
                            <div className="provider-toggle">
                                <label className="switch">
                                    <input
                                        type="checkbox"
                                        checked={config.paypal.enabled}
                                        onChange={(e) => setConfig(prev => ({ ...prev, paypal: { ...prev.paypal, enabled: e.target.checked } }))}
                                    />
                                    <span className="slider round"></span>
                                </label>
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent>
                        <div className={`provider-fields ${!config.paypal.enabled ? 'disabled' : ''}`}>
                            <Input
                                label="Client ID"
                                value={config.paypal.clientId || ''}
                                onChange={(e) => setConfig(prev => ({ ...prev, paypal: { ...prev.paypal, clientId: e.target.value } }))}
                                placeholder="Nhập Client ID"
                                disabled={!config.paypal.enabled}
                            />
                            <Input
                                label="Client Secret"
                                value={config.paypal.clientSecret || ''}
                                onChange={(e) => setConfig(prev => ({ ...prev, paypal: { ...prev.paypal, clientSecret: e.target.value } }))}
                                placeholder="Nhập Client Secret"
                                type="password"
                                disabled={!config.paypal.enabled}
                            />
                            <div className="form-group">
                                <label>Mode</label>
                                <select
                                    className="form-input"
                                    value={config.paypal.mode || 'sandbox'}
                                    onChange={(e) => setConfig(prev => ({ ...prev, paypal: { ...prev.paypal, mode: e.target.value } }))}
                                    disabled={!config.paypal.enabled}
                                >
                                    <option value="sandbox">Sandbox (Test)</option>
                                    <option value="live">Live (Production)</option>
                                </select>
                            </div>
                        </div>
                    </CardContent>
                </Card>

                <div className="payment-actions">
                    <Button type="submit" loading={saving} size="lg">
                        <Save size={18} />
                        Lưu cấu hình
                    </Button>
                </div>
            </form>
        </PageLayout>
    );
}
