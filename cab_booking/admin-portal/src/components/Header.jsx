import React from 'react';
import { useLocation } from 'react-router-dom';
import { Server, Activity } from 'lucide-react';

const Header = () => {
  const location = useLocation();

  const getPageTitle = () => {
    switch (location.pathname) {
      case '/dashboard':
        return 'Dashboard & Hệ thống Báo cáo';
      case '/users':
        return 'Quản lý Tài khoản Khách hàng';
      case '/drivers':
        return 'Quản lý Hồ sơ Tài xế';
      case '/invoices':
        return 'Quản lý Giao dịch & Hóa đơn';
      default:
        return 'Cổng điều hành Admin';
    }
  };

  return (
    <header className="admin-header">
      <div className="header-title">
        <h1>{getPageTitle()}</h1>
      </div>

      <div className="header-actions">
        <div className="header-stat-badge">
          <Activity size={14} style={{ color: 'var(--color-success)' }} />
          <span>Hệ thống hoạt động</span>
          <span className="badge-dot"></span>
        </div>

        <div className="header-stat-badge" style={{ borderColor: 'rgba(59, 130, 246, 0.2)' }}>
          <Server size={14} style={{ color: 'var(--color-info)' }} />
          <span>Cổng Gateway: 8080</span>
        </div>
      </div>
    </header>
  );
};

export default Header;
