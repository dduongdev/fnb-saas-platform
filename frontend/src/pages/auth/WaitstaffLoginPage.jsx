import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { getMyTenants } from '../../api/tenant';
import { useToast } from '../../context/ToastContext';

import './WaitstaffLoginPage.css';

export const WaitstaffLoginPage = () => {
    const [accessKey, setAccessKey] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const toast = useToast();

    const handleLogin = async (e) => {
        e.preventDefault();
        
        if (!accessKey.trim()) {
            toast.error('Vui lòng nhập Access Key');
            return;
        }

        setLoading(true);
        try {
            localStorage.setItem('pos_access_key', accessKey.trim());
            const shops = await getMyTenants();
            
            if (shops && shops.length > 0) {
                toast.success('Đăng nhập thành công!');
                // The page will reload so AuthContext picks up the new key and sets user
                window.location.href = '/my-shops'; 
            } else {
                localStorage.removeItem('pos_access_key');
                toast.error('Access Key không hợp lệ hoặc hết hạn.');
            }
        } catch (error) {
            localStorage.removeItem('pos_access_key');
            toast.error('Lỗi đăng nhập: ' + error.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="waitstaff-login-container">
            <div className="login-card">
                <div className="login-header">
                    <Shield size={48} className="shield-icon" />
                    <h2>Nhân viên điểm bán (POS)</h2>
                    <p>Đăng nhập bằng mã Access Key được cung cấp bởi Chủ quán</p>
                </div>

                <form className="login-form" onSubmit={handleLogin}>
                    <div className="form-group">
                        <label>Mã Access Key</label>
                        <input
                            type="text"
                            placeholder="Nhập mã Access Key gồm 6-8 chữ/số..."
                            value={accessKey}
                            onChange={(e) => setAccessKey(e.target.value.toUpperCase())}
                            disabled={loading}
                        />
                    </div>

                    <button type="submit" className="btn btn-primary" disabled={loading || !accessKey}>
                        {loading ? 'Đang kiểm tra...' : 'Truy cập POS'}
                    </button>
                    
                    <button 
                        type="button" 
                        className="btn btn-secondary back-btn"
                        onClick={() => navigate('/')}
                        disabled={loading}
                    >
                        Quay lại trang chủ
                    </button>
                </form>
            </div>
        </div>
    );
};
