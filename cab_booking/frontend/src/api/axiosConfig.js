import axios from 'axios';
import toast from 'react-hot-toast';

/**
 * Cấu hình Axios Interceptor toàn cục.
 * - Bắt lỗi 429 Too Many Requests.
 * - Hiển thị toast notification.
 * - Đọc header Retry-After và dispatch sự kiện để ButtonComponent xử lý countdown.
 */
const axiosInstance = axios.create({
  baseURL: process.env.REACT_APP_API_GATEWAY_URL || 'http://localhost:8080',
  timeout: 10000,
});

// ── Response Interceptor: bắt lỗi 429 ────────────────────────────────────────
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const { response, config } = error;

    // Nếu không có response (lỗi network, timeout...) → re-throw
    if (!response) {
      return Promise.reject(error);
    }

    // ── 429: Rate Limit exceeded ──────────────────────────────────────────────
    if (response.status === 429) {
      // 1) Đọc Retry-After từ header (mặc định 10 giây)
      const retryAfterHeader = response.headers['retry-after'];
      let retrySeconds = 10;

      if (retryAfterHeader) {
        // Header có thể là số giây hoặc HTTP-date (RFC 7231)
        retrySeconds = parseInt(retryAfterHeader, 10);
        if (isNaN(retrySeconds)) {
          // Parse HTTP-date: "Wed, 21 Oct 2025 07:28:00 GMT"
          const httpDate = new Date(retryAfterHeader).getTime();
          retrySeconds = Math.max(1, Math.ceil((httpDate - Date.now()) / 1000));
        }
      }

      // 2) Thử đọc retryAfter từ body JSON (do custom ErrorHandler trả về)
      try {
        const body = typeof response.data === 'string'
          ? JSON.parse(response.data)
          : response.data;
        if (body?.retryAfter) {
          retrySeconds = body.retryAfter;
        }
      } catch (_) { /* ignore parse error */ }

      // 3) Hiển thị toast notification thân thiện
      toast.error(
        `Bạn thao tác quá nhanh! Vui lòng chờ ${retrySeconds}s rồi thử lại.`,
        { duration: retrySeconds * 1000 + 1000, id: 'rate-limit-toast' }
      );

      // 4) Dispatch custom event để ButtonComponent lắng nghe và xử lý countdown
      const rateLimitEvent = new CustomEvent('rate-limit-triggered', {
        detail: {
          retrySeconds,
          url: config.url,
          method: config.method,
        },
      });
      window.dispatchEvent(rateLimitEvent);

      // 5) Tạo error có cấu trúc để component có thể xử lý thêm
      const rateLimitError = new Error('Too Many Requests');
      rateLimitError.code = 'RATE_LIMIT_EXCEEDED';
      rateLimitError.retryAfter = retrySeconds;
      rateLimitError.response = response;
      return Promise.reject(rateLimitError);
    }

    // ── Các lỗi khác → re-throw để caller xử lý ─────────────────────────────
    return Promise.reject(error);
  }
);

export default axiosInstance;
