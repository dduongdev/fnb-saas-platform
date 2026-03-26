import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../../context/ToastContext';
import { registerUser } from '../../api/auth';
import './RegisterPage.css';

export function RegisterPage() {
  const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '', fullName: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!form.username.trim() || !form.password || !form.confirmPassword) {
      setError('Vui lòng điền đầy đủ thông tin yêu cầu.');
      return;
    }

    if (form.password !== form.confirmPassword) {
      setError('Mật khẩu không trùng nhau.');
      return;
    }

    setLoading(true);

    try {
      await registerUser({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
      });

      toast.success('Đăng ký thành công. Vui lòng đăng nhập.');
      navigate('/login');
    } catch (err) {
      setError(err.message || 'Đăng ký thất bại, thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="register-root">
      <div className="register-panel">
        <h2>Đăng ký tài khoản</h2>

        <form className="register-form" onSubmit={handleSubmit}>
          <label>Họ và tên</label>
          <input
            value={form.fullName}
            onChange={(e) => setForm({ ...form, fullName: e.target.value })}
            placeholder="Họ tên"
          />

          <label>Username</label>
          <input
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            placeholder="Username"
          />

          <label>Email</label>
          <input
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            type="email"
            placeholder="Email"
          />

          <label>Mật khẩu</label>
          <input
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            placeholder="Mật khẩu"
          />

          <label>Xác nhận mật khẩu</label>
          <input
            type="password"
            value={form.confirmPassword}
            onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
            placeholder="Xác nhận mật khẩu"
          />

          {error && <div className="register-error">{error}</div>}

          <button className="btn-primary" type="submit" disabled={loading}>
            {loading ? 'Đang gửi...' : 'Đăng ký'}
          </button>

          <button type="button" className="btn-ghost" onClick={() => navigate('/login')}>
            Đã có tài khoản? Đăng nhập
          </button>
        </form>
      </div>
    </div>
  );
}
