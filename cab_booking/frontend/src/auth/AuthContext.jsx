import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import authHttp from '../api/authHttp';
import { env } from '../config/env';
import { clearAccessToken, setAccessToken } from './authSession';
import { deleteCookie, getCookie, setCookie } from '../utils/cookies';
import {
  clearStoredUser,
  getOrCreateDeviceId,
  getStoredUser,
  sanitizeUserForStorage,
  setStoredUser,
} from '../utils/storage';

const AuthContext = createContext(null);

const SESSION_EXPIRED_EVENT = 'auth:session-expired';

const getPlatform = () => {
  const agent = navigator.userAgent.toLowerCase();
  if (agent.includes('android')) {
    return 'ANDROID';
  }
  if (agent.includes('iphone') || agent.includes('ipad')) {
    return 'IOS';
  }
  return 'WEB';
};

const normalizeAuthResult = (response) => response?.data?.result || null;

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getStoredUser());
  const [accessTokenValue, setAccessTokenValue] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isBootstrapping, setIsBootstrapping] = useState(true);

  const applySession = useCallback((authResult) => {
    const safeUser = sanitizeUserForStorage(authResult?.user);

    setAccessToken(authResult?.accessToken || null);
    setAccessTokenValue(authResult?.accessToken || null);
    setUser(safeUser);
    setIsAuthenticated(Boolean(authResult?.accessToken));
    setStoredUser(safeUser);

    if (authResult?.refreshToken) {
      setCookie(env.refreshTokenCookieName, authResult.refreshToken, {
        maxAge: env.refreshTokenDays * 24 * 60 * 60,
        path: '/',
        sameSite: 'Lax',
        secure: window.location.protocol === 'https:',
      });
    }
  }, []);

  const clearSession = useCallback((options = {}) => {
    clearAccessToken();
    setAccessTokenValue(null);
    setIsAuthenticated(false);
    setUser(null);
    clearStoredUser();
    deleteCookie(env.refreshTokenCookieName);

    if (options.showToast) {
      toast.error(options.message || 'Phien dang nhap da het han. Vui long dang nhap lai.');
    }
  }, []);

  const refreshSession = useCallback(async () => {
    const refreshToken = getCookie(env.refreshTokenCookieName);
    if (!refreshToken) {
      clearSession();
      return null;
    }

    const response = await authHttp.post(`${env.authBasePath}/refresh`, {
      refreshToken,
    });

    const authResult = normalizeAuthResult(response);
    if (!authResult?.accessToken) {
      throw new Error('Refresh response did not include an access token');
    }

    applySession(authResult);
    return authResult.accessToken;
  }, [applySession, clearSession]);

  const login = useCallback(async ({ email, password }) => {
    const response = await authHttp.post(`${env.authBasePath}/login`, {
      email,
      password,
      deviceId: getOrCreateDeviceId(),
      platform: getPlatform(),
      userAgent: navigator.userAgent,
      appVersion: env.appVersion,
    });

    const authResult = normalizeAuthResult(response);
    applySession(authResult);
    toast.success('Dang nhap thanh cong.');
    return authResult;
  }, [applySession]);

  const logout = useCallback(async () => {
    const refreshToken = getCookie(env.refreshTokenCookieName);

    try {
      if (refreshToken) {
        await authHttp.post(`${env.authBasePath}/logout`, { refreshToken });
      }
    } finally {
      clearSession();
      toast.success('Dang xuat thanh cong.');
    }
  }, [clearSession]);

  useEffect(() => {
    const bootstrapAuth = async () => {
      try {
        const refreshToken = getCookie(env.refreshTokenCookieName);
        if (!refreshToken) {
          clearSession();
          return;
        }

        await refreshSession();
      } catch (error) {
        clearSession();
      } finally {
        setIsBootstrapping(false);
      }
    };

    bootstrapAuth();
  }, [clearSession, refreshSession]);

  useEffect(() => {
    const handleSessionExpired = () => {
      clearSession({
        showToast: true,
        message: 'Refresh token het hieu luc. Vui long dang nhap lai.',
      });
    };

    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, [clearSession]);

  const contextValue = useMemo(
    () => ({
      user,
      accessToken: accessTokenValue,
      isAuthenticated,
      isBootstrapping,
      login,
      logout,
      refreshSession,
      clearSession,
    }),
    [accessTokenValue, clearSession, isAuthenticated, isBootstrapping, login, logout, refreshSession, user]
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
}

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AUTH_EVENTS = {
  sessionExpired: SESSION_EXPIRED_EVENT,
};
