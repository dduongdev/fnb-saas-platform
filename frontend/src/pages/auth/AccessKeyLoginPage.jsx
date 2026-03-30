import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { getMyTenants } from '../../api/tenant';
import { useToast } from '../../context/ToastContext';

import '../auth/LoginPage.css';

export const AccessKeyLoginPage = () => {
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
                window.location.href = '/dashboard'; 
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
        <div className="login-root">
            <div className="login-panel">
                <div className="login-brand">
                    <div style={{display:'flex',alignItems:'center',gap:12}}>
                        <div style={{width:48,height:48,display:'flex',alignItems:'center',justifyContent:'center',background:'#eef2ff',borderRadius:10}}>
                            <Shield size={28} style={{color:'#2b3896'}} />
                        </div>
                        <div>
                            <h1 style={{margin:0}}>Access Key Login</h1>
                            <p style={{margin:0,fontSize:13}}>Nhân viên</p>
                        </div>
                    </div>
                </div>

                <form className="login-actions" onSubmit={handleLogin}>
                    <p>Đăng nhập bằng mã Access Key được cung cấp bởi Chủ quán</p>

                    <label>Mã Access Key</label>
                    <input
                        type="text"
                        placeholder="Nhập mã Access Key gồm 6-8 chữ/số..."
                        value={accessKey}
                        onChange={(e) => setAccessKey(e.target.value.toUpperCase())}
                        disabled={loading}
                    />

                    <div style={{display:'flex',gap:8}}>
                        <button type="submit" className="btn-primary" disabled={loading || !accessKey}>
                            {loading ? 'Đang kiểm tra...' : 'Truy cập POS'}
                        </button>
                        <button type="button" className="btn-secondary" onClick={() => navigate('/login')} disabled={loading}>
                            Quay lại
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};
