import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { User, Camera, Mail, Shield, ArrowLeft, Store, LogOut } from 'lucide-react';
import { Card, Loading, Button } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import { useToast } from '../../context/ToastContext';
import { uploadAvatar } from '../../api/auth';
import './UserProfilePage.css';

export function UserProfilePage() {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const { tenant } = useTenant();
    const toast = useToast();
    const [uploading, setUploading] = useState(false);

    const handleAvatarChange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        // Simple validation
        if (file.size > 5 * 1024 * 1024) {
            toast.error('File ảnh quá lớn (max 5MB)');
            return;
        }

        try {
            setUploading(true);
            const newUrl = await uploadAvatar(file);
            toast.success('Đã cập nhật ảnh đại diện');

            // Refresh user data functionality would ideally be in AuthContext
            // For now we might need to rely on a page reload or context update
            window.location.reload();
        } catch (error) {
            toast.error('Lỗi upload: ' + error.message);
        } finally {
            setUploading(false);
        }
    };

    const handleBack = () => {
        if (tenant) {
            navigate('/pos');
        } else {
            navigate('/dashboard');
        }
    };

    if (!user) return <Loading fullPage />;

    return (
        <div className="profile-page-standalone">
            {/* Simple Header */}
            <header className="profile-header">
                <button className="btn-back" onClick={handleBack}>
                    <ArrowLeft size={20} />
                </button>
                <h1>Hồ sơ cá nhân</h1>
                <div className="header-actions">
                    {tenant && (
                        <Button variant="secondary" onClick={() => navigate('/pos')}>
                            <Store size={16} /> {tenant.name}
                        </Button>
                    )}
                    <button className="btn-logout" onClick={logout}>
                        <LogOut size={16} /> Đăng xuất
                    </button>
                </div>
            </header>

            <main className="profile-main">
                <Card className="profile-card">
                    <div className="profile-avatar-section">
                        {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt={user.fullName} className="profile-avatar" />
                        ) : (
                            <div className="profile-avatar-placeholder">
                                <User size={48} />
                            </div>
                        )}

                        <label className="avatar-upload-btn" title="Đổi ảnh đại diện">
                            {uploading ? <Loading size="sm" color="white" /> : <Camera size={18} />}
                            <input
                                type="file"
                                accept="image/*"
                                onChange={handleAvatarChange}
                                disabled={uploading}
                                hidden
                            />
                        </label>
                    </div>

                    <div className="profile-info">
                        <h2>{user.fullName || user.username}</h2>
                        <p className="profile-email">{user.email}</p>
                    </div>

                    <div className="profile-details">
                        <div className="detail-item">
                            <span className="detail-label">
                                <Shield size={14} style={{ display: 'inline', marginRight: 4 }} />
                                User ID
                            </span>
                            <span className="detail-value">{user.id}</span>
                        </div>
                        <div className="detail-item">
                            <span className="detail-label">
                                <Mail size={14} style={{ display: 'inline', marginRight: 4 }} />
                                Email
                            </span>
                            <span className="detail-value">{user.email}</span>
                        </div>
                    </div>
                </Card>

                {/* Quick Links */}
                <div className="profile-links">
                    <Card className="link-card" onClick={() => navigate('/dashboard')}>
                        <Store size={24} />
                        <span>Quán của tôi</span>
                    </Card>
                    <Card className="link-card" onClick={() => navigate('/dashboard')}>
                        <ArrowLeft size={24} />
                        <span>Về trang chủ</span>
                    </Card>
                </div>
            </main>
        </div>
    );
}
