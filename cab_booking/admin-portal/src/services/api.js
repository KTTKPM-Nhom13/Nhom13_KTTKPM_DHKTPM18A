import axios from 'axios';
import { store } from '../store';
import { setCredentials, clearCredentials } from '../store/authSlice';

const API_BASE_URL = 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to attach accessToken from Redux store in-memory
api.interceptors.request.use(
  (config) => {
    const token = store.getState().auth.accessToken;
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle auto token refresh when 401 Unauthorized occurs
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Check if error status is 401 and this request has not already been retried
    if (error.response?.status === 401 && !originalRequest._retry) {
      // If we are already in the middle of refreshing the token, queue subsequent requests
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers['Authorization'] = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('admin_refresh_token');
      if (!refreshToken) {
        store.dispatch(clearCredentials());
        isRefreshing = false;
        return Promise.reject(error);
      }

      // Bypass automatic silent refresh / session clearing if using mock sandbox token
      if (refreshToken === 'mock_sandbox_refresh_token_jwt') {
        isRefreshing = false;
        return Promise.reject(error);
      }

      try {
        // Send request through Gateway to auth-service: /api/auth/refresh
        const res = await axios.post(`${API_BASE_URL}/api/auth/refresh`, {
          refreshToken,
        });

        const data = res.data.result;
        const accessToken = data.accessToken;
        const newRefreshToken = data.refreshToken;
        const userSummary = data.user;

        // Securely write accessToken and User info back into Redux in-memory state
        store.dispatch(
          setCredentials({
            accessToken,
            user: userSummary,
          })
        );

        if (newRefreshToken) {
          localStorage.setItem('admin_refresh_token', newRefreshToken);
        }

        processQueue(null, accessToken);
        isRefreshing = false;

        // Re-execute original failed request with fresh accessToken
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        // Clear Redux state & log out user if the refresh token has expired
        store.dispatch(clearCredentials());
        isRefreshing = false;
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
export { API_BASE_URL };
