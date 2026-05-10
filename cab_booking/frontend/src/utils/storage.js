import { env } from '../config/env';

const isBrowser = typeof window !== 'undefined';

const safeJsonParse = (value) => {
  try {
    return JSON.parse(value);
  } catch (error) {
    return null;
  }
};

export const sanitizeUserForStorage = (user) => {
  if (!user) {
    return null;
  }

  return {
    userId: user.userId || null,
    email: user.email || '',
    fullName: user.fullName || '',
    avatarUrl: user.avatarUrl || '',
    role: user.role || '',
  };
};

export const getStoredUser = () => {
  if (!isBrowser) {
    return null;
  }

  return safeJsonParse(window.localStorage.getItem(env.userStorageKey));
};

export const setStoredUser = (user) => {
  if (!isBrowser) {
    return;
  }

  const safeUser = sanitizeUserForStorage(user);
  if (!safeUser) {
    window.localStorage.removeItem(env.userStorageKey);
    return;
  }

  window.localStorage.setItem(env.userStorageKey, JSON.stringify(safeUser));
};

export const clearStoredUser = () => {
  if (!isBrowser) {
    return;
  }

  window.localStorage.removeItem(env.userStorageKey);
};

export const getOrCreateDeviceId = () => {
  if (!isBrowser) {
    return 'server-device';
  }

  const existingId = window.localStorage.getItem(env.deviceStorageKey);
  if (existingId) {
    return existingId;
  }

  const newId = `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  window.localStorage.setItem(env.deviceStorageKey, newId);
  return newId;
};
