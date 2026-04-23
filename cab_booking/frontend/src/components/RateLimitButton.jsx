import React, { useState, useEffect, useCallback } from 'react';

/**
 * RateLimitButton – Nút bấm thông minh có xử lý Rate Limit (429).
 *
 * Props:
 *  - onClick      : async function gọi API (nhận axios instance)
 *  - label        : text hiển thị khi bình thường
 *  - loadingLabel : text khi đang loading (mặc định: "Đang xử lý...")
 *  - variant      : 'primary' | 'secondary' (mặc định: 'primary')
 *  - className    : class CSS tùy chỉnh
 *  - axiosInstance: instance axios đã cấu hình interceptor (mặc định: axios mặc định)
 */
const RateLimitButton = ({
  onClick,
  label = 'Thực hiện',
  loadingLabel = 'Đang xử lý...',
  variant = 'primary',
  className = '',
  axiosInstance = null,
}) => {
  const [isLoading, setIsLoading] = useState(false);
  const [isCountingDown, setIsCountingDown] = useState(false);
  const [countdown, setCountdown] = useState(0);

  // ── Countdown timer ──────────────────────────────────────────────────────
  useEffect(() => {
    if (!isCountingDown || countdown <= 0) return;

    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          setIsCountingDown(false);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [isCountingDown, countdown]);

  // ── Lắng nghe event từ Axios Interceptor ────────────────────────────────
  useEffect(() => {
    const handleRateLimit = (event) => {
      // event.detail chứa { retrySeconds, url, method }
      setCountdown(event.detail.retrySeconds);
      setIsCountingDown(true);
      setIsLoading(false);
    };

    window.addEventListener('rate-limit-triggered', handleRateLimit);
    return () => window.removeEventListener('rate-limit-triggered', handleRateLimit);
  }, []);

  // ── Xử lý khi user click ────────────────────────────────────────────────
  const handleClick = async () => {
    if (isLoading || isCountingDown) return;

    setIsLoading(true);
    try {
      await onClick(axiosInstance);
    } catch (error) {
      // The Axios interceptor already handles 429; this prevents uncaught
      // runtime errors for network failures or any caller-side rejection.
      if (error?.code !== 'RATE_LIMIT_EXCEEDED') {
        console.error('Button request failed:', error);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const isDisabled = isLoading || isCountingDown;

  // ── Kiểu variant CSS inline ─────────────────────────────────────────────
  const baseStyle = {
    padding: '10px 20px',
    borderRadius: '8px',
    border: 'none',
    fontSize: '15px',
    fontWeight: '600',
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    transition: 'background-color 0.2s, opacity 0.2s, transform 0.1s',
    outline: 'none',
    minWidth: '160px',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '8px',
    opacity: isDisabled ? 0.7 : 1,
  };

  const variants = {
    primary: {
      backgroundColor: '#2563eb',
      color: '#ffffff',
    },
    secondary: {
      backgroundColor: '#e5e7eb',
      color: '#374151',
    },
  };

  const style = { ...baseStyle, ...variants[variant] };

  // ── Render nội dung nút ──────────────────────────────────────────────────
  const renderContent = () => {
    if (isCountingDown) {
      return (
        <>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          Thử lại sau {countdown}s...
        </>
      );
    }
    if (isLoading) {
      return (
        <>
          <svg
            width="16" height="16" viewBox="0 0 24 24"
            fill="none" stroke="currentColor" strokeWidth="2"
            style={{ animation: 'spin 1s linear infinite' }}
          >
            <path d="M21 12a9 9 0 11-6.219-8.56" />
          </svg>
          {loadingLabel}
        </>
      );
    }
    return label;
  };

  return (
    <>
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        .rate-limit-btn:hover:not(:disabled) {
          filter: brightness(1.1);
          transform: translateY(-1px);
        }
        .rate-limit-btn:active:not(:disabled) {
          transform: translateY(0);
        }
      `}</style>
      <button
        className={`rate-limit-btn ${className}`}
        style={style}
        onClick={handleClick}
        disabled={isDisabled}
        aria-label={isCountingDown ? `Thử lại sau ${countdown} giây` : label}
      >
        {renderContent()}
      </button>
    </>
  );
};

export default RateLimitButton;
