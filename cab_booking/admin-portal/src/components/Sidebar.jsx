import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { LayoutDashboard, Users, Car, LogOut, ShieldAlert } from 'lucide-react';
import { selectCurrentUser, clearCredentials } from '../store/authSlice';
import api from '../services/api';

const Sidebar = () => {
  const currentUser = useSelector(selectCurrentUser);
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      const refreshToken = localStorage.getItem('admin_refresh_token');
      if (refreshToken) {
        // Quietly notify auth-service of session invalidation
        await api.post('/api/auth/logout', { refreshToken }).catch(() => {});
      }
    } finally {
      dispatch(clearCredentials());
      navigate('/login');
    }
  };

  return (
    <aside className="admin-sidebar">
      <div className="sidebar-logo">
        <div className="logo-icon">👑</div>
        <div className="logo-text">CAB ADMIN</div>
      </div>

      <nav className="sidebar-menu">
        <NavLink to="/dashboard" className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}>
          <LayoutDashboard size={18} />
          <span>Dashboard</span>
        </NavLink>

        <NavLink to="/users" className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}>
          <Users size={18} />
          <span>Quản lý Users</span>
        </NavLink>

        <NavLink to="/drivers" className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}>
          <Car size={18} />
          <span>Quản lý Drivers</span>
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <div className="admin-profile-card">
          <div className="admin-avatar">
            {currentUser?.fullName?.charAt(0) || 'A'}
          </div>
          <div className="admin-info">
            <div className="admin-name">{currentUser?.fullName || 'System Admin'}</div>
            <div className="admin-role">{currentUser?.role || 'ADMIN'}</div>
          </div>
        </div>

        <button className="btn-logout" onClick={handleLogout}>
          <LogOut size={16} />
          <span>Đăng xuất</span>
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;
