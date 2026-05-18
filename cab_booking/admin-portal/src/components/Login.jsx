import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import axios from 'axios';
import { KeyRound, Mail, AlertTriangle } from 'lucide-react';
import { setCredentials } from '../store/authSlice';
import { API_BASE_URL } from '../services/api';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);

  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();

  const from = location.state?.from?.pathname || '/dashboard';

  const handleLogin = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMsg('Vui lòng nhập đầy đủ email và mật khẩu!');
      return;
    }

    setLoading(true);
    setErrorMsg(null);

    try {
      // Send login request through API Gateway to auth-service: /api/auth/login
      const res = await axios.post(`${API_BASE_URL}/api/auth/login`, {
        email,
        password,
        deviceId: 'web-admin-portal',
        platform: 'WEB',
        userAgent: navigator.userAgent,
        appVersion: '1.0.0',
      });

      const data = res.data.result;
      const accessToken = data.accessToken;
      const refreshToken = data.refreshToken;
      const userSummary = data.user;

      // Verify the user role is indeed ADMIN
      if (userSummary?.role !== 'ADMIN') {
        setErrorMsg('Lỗi quyền truy cập! Chỉ quản trị viên mới được đăng nhập cổng này.');
        setLoading(false);
        return;
      }

      // Save accessToken strictly in-memory inside the Redux Store
      dispatch(
        setCredentials({
          accessToken,
          user: userSummary,
        })
      );

      // Save refreshToken in localStorage for Silent Refresh
      if (refreshToken) {
        localStorage.setItem('admin_refresh_token', refreshToken);
      }

      navigate(from, { replace: true });
    } catch (err) {
      console.error('API login failed, checking for local sandbox fallback.');
      
      // Standalone sandbox mode for effortless demonstration/testing if backend is down
      if (email === 'admin@cab.local' && password === 'AdminPassword123') {
        const mockUser = {
          userId: '00000000-0000-0000-0000-000000000000',
          email: 'admin@cab.local',
          fullName: 'Tài Khoản Admin Demo',
          role: 'ADMIN',
          accountStatus: 'ACTIVE',
        };

        dispatch(
          setCredentials({
            accessToken: 'mock_sandbox_access_token_jwt',
            user: mockUser,
          })
        );
        localStorage.setItem('admin_refresh_token', 'mock_sandbox_refresh_token_jwt');
        navigate(from, { replace: true });
      } else {
        setErrorMsg(
          err.response?.data?.message || 
          'Không thể kết nối đến máy chủ! Sai thông tin hoặc Cổng Gateway 8080 chưa chạy.'
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-header">
          <div className="auth-logo">👑</div>
          <h2>CAB ADMIN PORTAL</h2>
          <p>Đăng nhập vào hệ thống điều hành xe công nghệ</p>
        </div>

        {errorMsg && (
          <div style={{ 
            background: 'var(--color-danger-bg)', 
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: 'var(--radius-sm)',
            padding: '12px 16px',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            color: '#f87171',
            fontSize: '13px',
            lineHeight: '1.4',
            textAlign: 'left'
          }}>
            <AlertTriangle size={18} style={{ flexShrink: 0 }} />
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label className="form-label">Tài khoản Email</label>
            <div style={{ position: 'relative' }}>
              <Mail size={16} style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
              <input 
                type="email" 
                placeholder="admin@cab.local" 
                className="form-input"
                style={{ paddingLeft: '42px' }}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
                required
              />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '28px' }}>
            <label className="form-label">Mật khẩu</label>
            <div style={{ position: 'relative' }}>
              <KeyRound size={16} style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
              <input 
                type="password" 
                placeholder="••••••••••••" 
                className="form-input"
                style={{ paddingLeft: '42px' }}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
                required
              />
            </div>
          </div>

          <button 
            type="submit" 
            className="btn-primary"
            disabled={loading}
          >
            {loading ? 'Đang xác thực...' : 'Đăng Nhập Quản Trị'}
          </button>
        </form>
        
        <div style={{ textAlign: 'center', marginTop: '24px', fontSize: '12px', color: 'var(--text-muted)' }}>
          <span>Mặc định: <strong>admin@cab.local</strong> / <strong>AdminPassword123</strong></span>
        </div>
      </div>
    </div>
  );
};

export default Login;
