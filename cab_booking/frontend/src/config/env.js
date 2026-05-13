const trimTrailingSlash = (value) => value.replace(/\/+$/, '');

const rawGatewayUrl = process.env.REACT_APP_API_GATEWAY_URL || 'http://localhost:8080';

export const env = {
  apiGatewayUrl: trimTrailingSlash(rawGatewayUrl),
  authBasePath: process.env.REACT_APP_AUTH_BASE_PATH || '/api/auth',
  appVersion: process.env.REACT_APP_APP_VERSION || '1.0.0',
  refreshTokenCookieName: process.env.REACT_APP_REFRESH_TOKEN_COOKIE || 'cb_refresh_token',
  refreshTokenDays: Number(process.env.REACT_APP_REFRESH_TOKEN_DAYS || 30),
  userStorageKey: 'cab_booking_user_basic',
  deviceStorageKey: 'cab_booking_device_id',
};
