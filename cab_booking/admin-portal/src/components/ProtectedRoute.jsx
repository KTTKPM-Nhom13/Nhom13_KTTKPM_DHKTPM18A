import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import axios from 'axios';
import { selectIsAuthenticated, selectCurrentUser, setCredentials, clearCredentials } from '../store/authSlice';
import { API_BASE_URL } from '../services/api';

const ProtectedRoute = ({ children }) => {
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const currentUser = useSelector(selectCurrentUser);
  const dispatch = useDispatch();
  const location = useLocation();
  const [isAttemptingSilentRefresh, setIsAttemptingSilentRefresh] = useState(!isAuthenticated);

  useEffect(() => {
    const attemptSilentRefresh = async () => {
      // If we already have the token in Redux, no refresh is needed
      if (isAuthenticated) {
        setIsAttemptingSilentRefresh(false);
        return;
      }

      const refreshToken = localStorage.getItem('admin_refresh_token');
      if (!refreshToken) {
        setIsAttemptingSilentRefresh(false);
        return;
      }

      // Bypass backend request if using mock sandbox refresh token
      if (refreshToken === 'mock_sandbox_refresh_token_jwt') {
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
        setIsAttemptingSilentRefresh(false);
        return;
      }

      try {
        const res = await axios.post(`${API_BASE_URL}/api/auth/refresh`, {
          refreshToken,
        });

        const data = res.data.result;
        const accessToken = data.accessToken;
        const newRefreshToken = data.refreshToken;
        const userSummary = data.user;

        // Verify the user is an admin
        if (userSummary?.role !== 'ADMIN') {
          throw new Error('Unauthorized role access');
        }

        dispatch(
          setCredentials({
            accessToken,
            user: userSummary,
          })
        );

        if (newRefreshToken) {
          localStorage.setItem('admin_refresh_token', newRefreshToken);
        }
      } catch (err) {
        console.error('Silent refresh failed:', err);
        dispatch(clearCredentials());
      } finally {
        setIsAttemptingSilentRefresh(false);
      }
    };

    attemptSilentRefresh();
  }, [isAuthenticated, dispatch]);

  if (isAttemptingSilentRefresh) {
    return (
      <div className="auth-container">
        <div style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px' }}>
          <div className="logo-icon" style={{ width: '60px', height: '60px', fontSize: '28px', animation: 'pulse 1.5s infinite' }}>
            👑
          </div>
          <div style={{ color: 'var(--text-secondary)', fontWeight: '600', letterSpacing: '0.5px' }}>
            Verifying Admin Session...
          </div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (currentUser?.role !== 'ADMIN') {
    return (
      <div className="auth-container">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <div className="stat-icon-wrapper red" style={{ margin: '0 auto 20px', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', width: '64px', height: '64px', borderRadius: '12px' }}>
            🚫
          </div>
          <h2 style={{ color: '#ef4444', marginBottom: '12px' }}>Access Denied</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '24px', fontSize: '14px' }}>
            Only users with the administrator role are permitted to enter this portal.
          </p>
          <button className="btn-primary" onClick={() => dispatch(clearCredentials())}>
            Return to Login
          </button>
        </div>
      </div>
    );
  }

  return children;
};

export default ProtectedRoute;
