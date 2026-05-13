import React, { useState } from 'react';
import toast from 'react-hot-toast';
import RateLimitButton from '../components/RateLimitButton';
import apiClient from '../api/axiosConfig';
import { env } from '../config/env';
import { useAuth } from '../auth/AuthContext';

const initialCredentials = {
  email: '',
  password: '',
};

const cardStyle = {
  background: '#ffffff',
  border: '1px solid #dbe4f0',
  borderRadius: '20px',
  boxShadow: '0 16px 40px rgba(15, 23, 42, 0.08)',
  padding: '24px',
};

const inputStyle = {
  width: '100%',
  padding: '12px 14px',
  borderRadius: '12px',
  border: '1px solid #bfd0e3',
  fontSize: '14px',
  outline: 'none',
  boxSizing: 'border-box',
};

const createTraceId = (prefix = 'login') =>
  `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}`;

const DemoPage = () => {
  const [credentials, setCredentials] = useState(initialCredentials);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { user, accessToken, isAuthenticated, isBootstrapping, login, logout } = useAuth();

  const handleChange = (event) => {
    const { name, value } = event.target;
    setCredentials((prev) => ({ ...prev, [name]: value }));
  };

  const handleLoginSubmit = async (event) => {
    event.preventDefault();
    setIsSubmitting(true);

    try {
      await login(credentials);
      setCredentials((prev) => ({ ...prev, password: '' }));
    } catch (error) {
      const message = error?.response?.data?.message || 'Dang nhap that bai.';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = async () => {
    setIsSubmitting(true);
    try {
      await logout();
    } catch (error) {
      toast.error('Dang xuat that bai.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGetETAPrice = async (axiosInstance) => {
    const response = await axiosInstance.post('/eta/calculate', {
      pickupLocation: { lat: 10.7629, lng: 106.6604 },
      dropoffLocation: { lat: 10.8231, lng: 106.6297 },
    });
    console.log('ETA response:', response.data);
    toast.success('Da goi API ETA thanh cong.');
  };

  const handleBurstETAPrice = async (axiosInstance) => {
    const payload = {
      pickupLocation: { lat: 10.7629, lng: 106.6604 },
      dropoffLocation: { lat: 10.8231, lng: 106.6297 },
    };

    const requests = Array.from({ length: 20 }, () =>
      axiosInstance.post('/eta/calculate', payload)
    );

    const results = await Promise.allSettled(requests);
    const okCount = results.filter((result) => result.status === 'fulfilled').length;
    toast.success(`Burst ETA xong: ${okCount}/${results.length} request thanh cong.`);
  };

  const handleDebugToken = async (axiosInstance) => {
    const traceId = createTraceId();
    const response = await axiosInstance.get(`${env.authBasePath}/token`, {
      headers: {
        'X-Debug-Trace-Id': traceId,
      },
    });

    console.log('Debug token:', response.data);
    console.log('Trace headers:', {
      requestTraceId: traceId,
      responseTraceId: response.headers['x-debug-trace-id'],
      servedBy: response.headers['x-served-by'],
    });

    toast.success('Da lay debug token.');
  };

  const handleBurstLogin = async (axiosInstance) => {
    const requests = Array.from({ length: 5 }, (_, index) =>
      axiosInstance.get(`${env.authBasePath}/token`, {
        headers: {
          'X-Debug-Trace-Id': createTraceId(`login-burst-${index + 1}`),
        },
      })
    );

    const results = await Promise.allSettled(requests);
    const okCount = results.filter((result) => result.status === 'fulfilled').length;
    toast.success(`Burst auth xong: ${okCount}/${results.length} request thanh cong.`);
  };

  if (isBootstrapping) {
    return (
      <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#f4f7fb' }}>
        <div style={{ ...cardStyle, width: 'min(420px, 92vw)', textAlign: 'center' }}>
          <h2 style={{ marginTop: 0 }}>Khoi phuc phien dang nhap</h2>
          <p style={{ marginBottom: 0, color: '#526277' }}>
            He thong dang kiem tra refresh token trong cookie.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'linear-gradient(180deg, #eff6ff 0%, #f8fafc 52%, #eef2ff 100%)',
        padding: '32px 20px 56px',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ maxWidth: '1080px', margin: '0 auto' }}>
        <div style={{ marginBottom: '24px' }}>
          <p style={{ margin: 0, color: '#355070', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
            CAB Booking Frontend
          </p>
          <h1 style={{ margin: '8px 0 10px', fontSize: 'clamp(30px, 4vw, 46px)', color: '#0f172a' }}>
            Auth flow an toan hon cho login, logout va refresh token
          </h1>
          <p style={{ maxWidth: '760px', margin: 0, color: '#4b5563', lineHeight: 1.6 }}>
            Access token chi ton tai trong state bo nho. Refresh token nam trong cookie. Local storage chi luu
            thong tin co ban cua user de frontend hien thi nhanh sau khi tai lai trang.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
          <section style={cardStyle}>
            <h2 style={{ marginTop: 0, marginBottom: '16px', color: '#0f172a' }}>
              {isAuthenticated ? 'Thong tin phien dang nhap' : 'Dang nhap'}
            </h2>

            {!isAuthenticated ? (
              <form onSubmit={handleLoginSubmit}>
                <div style={{ marginBottom: '14px' }}>
                  <label htmlFor="email" style={{ display: 'block', marginBottom: '8px', fontWeight: 600, color: '#243b53' }}>
                    Email
                  </label>
                  <input
                    id="email"
                    name="email"
                    type="email"
                    value={credentials.email}
                    onChange={handleChange}
                    placeholder="tai.demo@cab.local"
                    style={inputStyle}
                    required
                  />
                </div>

                <div style={{ marginBottom: '18px' }}>
                  <label htmlFor="password" style={{ display: 'block', marginBottom: '8px', fontWeight: 600, color: '#243b53' }}>
                    Mat khau
                  </label>
                  <input
                    id="password"
                    name="password"
                    type="password"
                    value={credentials.password}
                    onChange={handleChange}
                    placeholder="123456"
                    style={inputStyle}
                    required
                  />
                </div>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  style={{
                    width: '100%',
                    padding: '12px 16px',
                    borderRadius: '14px',
                    border: 'none',
                    background: '#0f766e',
                    color: '#fff',
                    fontSize: '15px',
                    fontWeight: 700,
                    cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    opacity: isSubmitting ? 0.7 : 1,
                  }}
                >
                  {isSubmitting ? 'Dang dang nhap...' : 'Dang nhap'}
                </button>
              </form>
            ) : (
              <>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '14px',
                    padding: '14px',
                    borderRadius: '16px',
                    background: '#f8fafc',
                    border: '1px solid #dbe4f0',
                    marginBottom: '16px',
                  }}
                >
                  <div
                    style={{
                      width: '52px',
                      height: '52px',
                      borderRadius: '50%',
                      background: '#cbd5e1',
                      backgroundImage: user?.avatarUrl ? `url(${user.avatarUrl})` : 'none',
                      backgroundSize: 'cover',
                      backgroundPosition: 'center',
                      display: 'grid',
                      placeItems: 'center',
                      fontWeight: 700,
                      color: '#0f172a',
                    }}
                  >
                    {!user?.avatarUrl ? (user?.fullName || 'U').slice(0, 1).toUpperCase() : null}
                  </div>

                  <div>
                    <div style={{ fontWeight: 700, color: '#0f172a' }}>{user?.fullName || 'Unknown user'}</div>
                    <div style={{ color: '#526277', fontSize: '14px' }}>{user?.email}</div>
                    <div style={{ color: '#0f766e', fontSize: '13px', fontWeight: 700 }}>{user?.role}</div>
                  </div>
                </div>

                <div style={{ marginBottom: '14px', color: '#475569', fontSize: '14px', lineHeight: 1.6 }}>
                  <div>Access token trong state: {accessToken ? 'Co' : 'Khong'}</div>
                  <div>Refresh token trong cookie: Co</div>
                  <div>Local storage chi luu: userId, email, fullName, avatarUrl, role</div>
                </div>

                <button
                  type="button"
                  onClick={handleLogout}
                  disabled={isSubmitting}
                  style={{
                    width: '100%',
                    padding: '12px 16px',
                    borderRadius: '14px',
                    border: '1px solid #fda4af',
                    background: '#fff1f2',
                    color: '#be123c',
                    fontSize: '15px',
                    fontWeight: 700,
                    cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    opacity: isSubmitting ? 0.7 : 1,
                  }}
                >
                  {isSubmitting ? 'Dang dang xuat...' : 'Dang xuat'}
                </button>
              </>
            )}
          </section>

          <section style={cardStyle}>
            <h2 style={{ marginTop: 0, marginBottom: '16px', color: '#0f172a' }}>API va rate limit demo</h2>
            <p style={{ marginTop: 0, color: '#526277', lineHeight: 1.6 }}>
              `axiosConfig` da duoc cap nhat de tu gan bearer token va tu refresh khi access token het han.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ padding: '16px', border: '1px solid #dbe4f0', borderRadius: '16px', background: '#fcfdff' }}>
                <h3 style={{ marginTop: 0, marginBottom: '8px' }}>Tinh gia du kien</h3>
                <p style={{ marginTop: 0, marginBottom: '14px', color: '#64748b', fontSize: '14px' }}>
                  Route <code>/eta/**</code>, van giu co che bat 429 va countdown tren button.
                </p>
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                  <RateLimitButton
                    label="Lay gia du kien"
                    loadingLabel="Dang tinh gia..."
                    onClick={handleGetETAPrice}
                    axiosInstance={apiClient}
                  />
                  <RateLimitButton
                    label="Burst 20 request"
                    loadingLabel="Dang ban request..."
                    onClick={handleBurstETAPrice}
                    axiosInstance={apiClient}
                    variant="secondary"
                  />
                </div>
              </div>

              <div style={{ padding: '16px', border: '1px solid #dbe4f0', borderRadius: '16px', background: '#fcfdff' }}>
                <h3 style={{ marginTop: 0, marginBottom: '8px' }}>Auth debug</h3>
                <p style={{ marginTop: 0, marginBottom: '14px', color: '#64748b', fontSize: '14px' }}>
                  Dung de test route auth va cac interceptor hien tai.
                </p>
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                  <RateLimitButton
                    label="Lay debug token"
                    loadingLabel="Dang lay token..."
                    onClick={handleDebugToken}
                    axiosInstance={apiClient}
                    variant="secondary"
                  />
                  <RateLimitButton
                    label="Burst auth 5 request"
                    loadingLabel="Dang ban auth..."
                    onClick={handleBurstLogin}
                    axiosInstance={apiClient}
                  />
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
};

export default DemoPage;
