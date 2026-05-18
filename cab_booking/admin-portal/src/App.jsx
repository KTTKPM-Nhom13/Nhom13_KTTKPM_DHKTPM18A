import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from './store';
import Login from './components/Login';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import Dashboard from './components/Dashboard';
import UsersManagement from './components/UsersManagement';
import DriversManagement from './components/DriversManagement';
import ProtectedRoute from './components/ProtectedRoute';

// Admin Layout wrapper including sticky sidebar and header viewports
const AdminLayout = () => {
  return (
    <div className="admin-layout">
      <Sidebar />
      <div className="admin-viewport">
        <Header />
        <Outlet />
      </div>
    </div>
  );
};

function App() {
  return (
    <Provider store={store}>
      <BrowserRouter>
        <Routes>
          {/* Public login page */}
          <Route path="/login" element={<Login />} />

          {/* Secure Admin Portal routes */}
          <Route
            element={
              <ProtectedRoute>
                <AdminLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/users" element={<UsersManagement />} />
            <Route path="/drivers" element={<DriversManagement />} />
          </Route>

          {/* Fallback redirects */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </Provider>
  );
}

export default App;
