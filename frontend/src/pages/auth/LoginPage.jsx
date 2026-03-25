import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import './LoginPage.css';

export function LoginPage() {
  const { directLogin } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await directLogin(username.trim(), password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.message || 'Đăng nhập thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-root">
      <div className="login-panel">
        <div className="login-brand">
          <h1>F&B Management</h1>
          <p>A light-weight solution for managing your F&B</p>
        </div>

        <form className="login-actions" onSubmit={handleSubmit}>
          <h2>Đăng nhập</h2>
          <p>Đăng nhập bằng tài khoản của bạn để tiếp tục.</p>

          <label>Username</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Tên đăng nhập hoặc email"
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                handleSubmit(e);
              }
            }}
          />

          <label>Mật khẩu</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Mật khẩu"
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                handleSubmit(e);
              }
            }}
          />

          {error && <div className="login-error">{error}</div>}

          <button className="btn-primary" type="submit" disabled={loading || !username || !password}>
            {loading ? 'Đang xử lý...' : 'Đăng nhập'}
          </button>

          <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', marginTop: 12 }}>
            <button type="button" className="btn-secondary" onClick={() => navigate('/waiter-login')}>
              Đăng nhập bằng Access Key (POS)
            </button>
            <button type="button" className="btn-ghost" onClick={() => navigate('/shops')}>
              Xem danh sách quán
            </button>
          </div>
        </form>

        <footer className="login-footer">©dduongdev</footer>
      </div>
    </div>
  );
}

export default LoginPage;
