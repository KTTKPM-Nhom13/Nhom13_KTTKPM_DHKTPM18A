import { createSlice } from '@reduxjs/toolkit';

const savedUser = localStorage.getItem('admin_user');
const savedToken = localStorage.getItem('admin_access_token');

const initialState = {
  accessToken: savedToken || null,
  user: savedUser ? JSON.parse(savedUser) : null,
  isAuthenticated: !!savedToken,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (state, action) => {
      const { accessToken, user } = action.payload;
      state.accessToken = accessToken;
      state.user = user;
      state.isAuthenticated = !!accessToken;
      if (accessToken) {
        localStorage.setItem('admin_access_token', accessToken);
      }
      if (user) {
        localStorage.setItem('admin_user', JSON.stringify(user));
      }
    },
    updateUser: (state, action) => {
      state.user = { ...state.user, ...action.payload };
      localStorage.setItem('admin_user', JSON.stringify(state.user));
    },
    clearCredentials: (state) => {
      state.accessToken = null;
      state.user = null;
      state.isAuthenticated = false;
      // Remove tokens and user info from localStorage on logout
      localStorage.removeItem('admin_refresh_token');
      localStorage.removeItem('admin_access_token');
      localStorage.removeItem('admin_user');
    },
  },
});

export const { setCredentials, updateUser, clearCredentials } = authSlice.actions;

export default authSlice.reducer;

export const selectCurrentToken = (state) => state.auth.accessToken;
export const selectCurrentUser = (state) => state.auth.user;
export const selectIsAuthenticated = (state) => state.auth.isAuthenticated;
