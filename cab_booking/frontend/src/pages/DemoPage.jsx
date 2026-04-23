import React from 'react';
import RateLimitButton from '../components/RateLimitButton';
import apiClient from '../api/axiosConfig';

/**
 * Trang minh họa cách dùng RateLimitButton với các API call thực tế.
 */
const DemoPage = () => {

  const createTraceId = (prefix = 'login') => `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}`;

  // ── Lấy giá dự kiến ────────────────────────────────────────────────────
  const handleGetETAPrice = async (axiosInstance) => {
    // Ví dụ: gọi API tính giá qua API Gateway
    const response = await axiosInstance.post('/eta/calculate', {
      pickupLocation: { lat: 10.7629, lng: 106.6604 },
      dropoffLocation: { lat: 10.8231, lng: 106.6297 },
    });
    console.log('ETA response:', response.data);
  };

  // Gui nhieu request song song de test rate limit de dang hon click tay.
  const handleBurstETAPrice = async (axiosInstance) => {
    const payload = {
      pickupLocation: { lat: 10.7629, lng: 106.6604 },
      dropoffLocation: { lat: 10.8231, lng: 106.6297 },
    };

    const requests = Array.from({ length: 20 }, () =>
      axiosInstance.post('/eta/calculate', payload)
    );

    const results = await Promise.allSettled(requests);
    const okCount = results.filter((r) => r.status === 'fulfilled').length;
    const failCount = results.length - okCount;
    console.log(`Burst done: success=${okCount}, failed=${failCount}`);
  };

  // ── Đăng nhập ──────────────────────────────────────────────────────────
  const handleLogin = async (axiosInstance) => {
    const traceId = createTraceId();
    const response = await axiosInstance.get('/api/auth/token', {
      headers: {
        'X-Debug-Trace-Id': traceId,
      },
    });
    console.log('Login response:', response.data);
    console.log('Trace headers:', {
      requestTraceId: traceId,
      responseTraceId: response.headers['x-debug-trace-id'],
      servedBy: response.headers['x-served-by'],
    });
  };

  // Gui nhieu request dang nhap song song de test rate limit auth.
  const handleBurstLogin = async (axiosInstance) => {
    const requests = Array.from({ length: 10 }, (_, index) =>
      axiosInstance.get('/api/auth/token', {
        headers: {
          'X-Debug-Trace-Id': createTraceId(`login-burst-${index + 1}`),
        },
      })
    );

    const results = await Promise.allSettled(requests);
    const okCount = results.filter((r) => r.status === 'fulfilled').length;
    const failCount = results.length - okCount;
    console.log(`Login burst done: success=${okCount}, failed=${failCount}`);
  };

  return (
    <div style={{ padding: '40px', maxWidth: '600px', margin: '0 auto', fontFamily: 'Arial, sans-serif' }}>
      <h1 style={{ fontSize: '24px', marginBottom: '8px' }}>Demo Rate Limiting</h1>
      <p style={{ color: '#666', marginBottom: '32px' }}>
        Nhấn liên tục vào các nút bên dưới để kích hoạt lỗi 429 từ API Gateway.
        Sau khi bị block, nút sẽ tự động đếm ngược và enable lại.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

        {/* Nút: Lấy giá dự kiến */}
        <div style={{ padding: '16px', border: '1px solid #e5e7eb', borderRadius: '12px' }}>
          <h3 style={{ margin: '0 0 8px 0', fontSize: '16px' }}>Tính giá dự kiến</h3>
          <p style={{ margin: '0 0 16px 0', color: '#666', fontSize: '14px' }}>
            Route: <code>/eta/**</code> – Giới hạn: 3 req/s, burst: 20
          </p>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <RateLimitButton
              label="Lấy giá dự kiến"
              loadingLabel="Đang tính giá..."
              onClick={handleGetETAPrice}
              axiosInstance={apiClient}
            />
            <RateLimitButton
              label="Test burst 20 req"
              loadingLabel="Đang bắn request..."
              onClick={handleBurstETAPrice}
              axiosInstance={apiClient}
              variant="secondary"
            />
          </div>
        </div>

        {/* Nút: Đăng nhập */}
        <div style={{ padding: '16px', border: '1px solid #e5e7eb', borderRadius: '12px' }}>
          <h3 style={{ margin: '0 0 8px 0', fontSize: '16px' }}>Đăng nhập</h3>
          <p style={{ margin: '0 0 16px 0', color: '#666', fontSize: '14px' }}>
            Route: <code>/api/auth/**</code> – Giới hạn route-level: 1 req/s, burst: 2
          </p>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <RateLimitButton
              label="Đăng nhập"
              loadingLabel="Đang đăng nhập..."
              onClick={handleLogin}
              axiosInstance={apiClient}
              variant="secondary"
            />
            <RateLimitButton
              label="Test login burst 10 req"
              loadingLabel="Đang bắn login..."
              onClick={handleBurstLogin}
              axiosInstance={apiClient}
            />
          </div>
        </div>

      </div>

      <div style={{
        marginTop: '32px',
        padding: '16px',
        backgroundColor: '#f0f9ff',
        borderRadius: '8px',
        fontSize: '13px',
        color: '#1e40af',
      }}>
        <strong>Cơ chế hoạt động:</strong>
        <ul style={{ margin: '8px 0 0 0', paddingLeft: '20px' }}>
          <li>Axios Interceptor bắt HTTP 429 từ API Gateway</li>
          <li>Đọc header <code>Retry-After</code> và body JSON để lấy thời gian chờ</li>
          <li>Hiển thị toast notification + disable nút + countdown</li>
          <li>Hết giờ → nút tự enable lại</li>
        </ul>
      </div>
    </div>
  );
};

export default DemoPage;
