import axios from 'axios';
import toast from 'react-hot-toast';
import authHttp from './authHttp';
import { env } from '../config/env';
import { AUTH_EVENTS } from '../auth/AuthContext';
import { clearAccessToken, getAccessToken, setAccessToken } from '../auth/authSession';
import { deleteCookie, getCookie, setCookie } from '../utils/cookies';

const axiosInstance = axios.create({
  baseURL: env.apiGatewayUrl,
  timeout: 10000,
});

let refreshRequest = null;

const isAuthRoute = (url = '') =>
  url.includes(`${env.authBasePath}/login`) ||
  url.includes(`${env.authBasePath}/refresh`) ||
  url.includes(`${env.authBasePath}/logout`);

const emitSessionExpired = () => {
  clearAccessToken();
  deleteCookie(env.refreshTokenCookieName);
  window.dispatchEvent(new Event(AUTH_EVENTS.sessionExpired));
};

const refreshAccessToken = async () => {
  if (refreshRequest) {
    return refreshRequest;
  }

  const refreshToken = getCookie(env.refreshTokenCookieName);
  if (!refreshToken) {
    emitSessionExpired();
    throw new Error('Missing refresh token');
  }

  refreshRequest = authHttp
    .post(`${env.authBasePath}/refresh`, { refreshToken })
    .then((response) => {
      const authResult = response?.data?.result;
      const nextAccessToken = authResult?.accessToken;
      const nextRefreshToken = authResult?.refreshToken;

      if (!nextAccessToken) {
        throw new Error('Refresh response missing access token');
      }

      setAccessToken(nextAccessToken);

      if (nextRefreshToken) {
        setCookie(env.refreshTokenCookieName, nextRefreshToken, {
          maxAge: env.refreshTokenDays * 24 * 60 * 60,
          path: '/',
          sameSite: 'Lax',
          secure: window.location.protocol === 'https:',
        });
      }

      return nextAccessToken;
    })
    .catch((error) => {
      emitSessionExpired();
      throw error;
    })
    .finally(() => {
      refreshRequest = null;
    });

  return refreshRequest;
};

axiosInstance.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { response, config } = error;

    if (!response) {
      return Promise.reject(error);
    }

    if (response.status === 429) {
      const retryAfterHeader = response.headers['retry-after'];
      let retrySeconds = 10;

      if (retryAfterHeader) {
        retrySeconds = parseInt(retryAfterHeader, 10);
        if (isNaN(retrySeconds)) {
          const httpDate = new Date(retryAfterHeader).getTime();
          retrySeconds = Math.max(1, Math.ceil((httpDate - Date.now()) / 1000));
        }
      }

      try {
        const body = typeof response.data === 'string'
          ? JSON.parse(response.data)
          : response.data;
        if (body?.retryAfter) {
          retrySeconds = body.retryAfter;
        }
      } catch (_) {
      }

      toast.error(
        `Ban thao tac qua nhanh. Vui long cho ${retrySeconds}s roi thu lai.`,
        { duration: retrySeconds * 1000 + 1000, id: 'rate-limit-toast' }
      );

      window.dispatchEvent(new CustomEvent('rate-limit-triggered', {
        detail: {
          retrySeconds,
          url: config?.url,
          method: config?.method,
        },
      }));

      const rateLimitError = new Error('Too Many Requests');
      rateLimitError.code = 'RATE_LIMIT_EXCEEDED';
      rateLimitError.retryAfter = retrySeconds;
      rateLimitError.response = response;
      return Promise.reject(rateLimitError);
    }

    if (response.status === 401 && config && !config._retry && !isAuthRoute(config.url || '')) {
      config._retry = true;

      try {
        const nextAccessToken = await refreshAccessToken();
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${nextAccessToken}`;
        return axiosInstance(config);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default axiosInstance;
