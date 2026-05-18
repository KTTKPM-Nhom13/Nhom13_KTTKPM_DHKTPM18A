import React, { useEffect, useState } from 'react';
import { Users, Car, RefreshCw } from 'lucide-react';
import api from '../services/api';

const Dashboard = () => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalUsers: 0,
    totalDrivers: 0,
  });

  const fetchDashboardStats = async () => {
    setLoading(true);
    try {
      const userRes = await api.get('/api/users/count').catch(() => null);
      const driverRes = await api.get('/api/drivers/count').catch(() => null);

      setStats({
        totalUsers: (userRes && userRes.data && typeof userRes.data.result === 'number') ? userRes.data.result : 0,
        totalDrivers: (driverRes && driverRes.data && typeof driverRes.data.result === 'number') ? driverRes.data.result : 0,
      });
    } catch (err) {
      console.warn('Failed to fetch dashboard stats from active API endpoints.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardStats();
  }, []);

  return (
    <div className="admin-content">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: 'bold', color: 'var(--text-primary)' }}>Dashboard Quản Trị Hệ Thống</h2>
        <button className="btn-icon" onClick={fetchDashboardStats} title="Làm mới dữ liệu">
          <RefreshCw size={16} />
        </button>
      </div>

      {/* Stat Cards Grid */}
      <div className="dashboard-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
        
        {/* Total Customers Card */}
        <div className="stat-card" style={{ position: 'relative', overflow: 'hidden' }}>
          <div className="stat-icon-wrapper purple">
            <Users size={24} />
          </div>
          <div className="stat-details">
            <span className="stat-label">Tổng Khách Hàng (Users)</span>
            <span className="stat-value">
              {loading ? '...' : stats.totalUsers.toLocaleString()}
            </span>
          </div>
        </div>

        {/* Active Drivers Card */}
        <div className="stat-card" style={{ position: 'relative', overflow: 'hidden' }}>
          <div className="stat-icon-wrapper emerald">
            <Car size={24} />
          </div>
          <div className="stat-details">
            <span className="stat-label">Tổng Tài Xế (Drivers)</span>
            <span className="stat-value">
              {loading ? '...' : stats.totalDrivers.toLocaleString()}
            </span>
          </div>
        </div>

      </div>
    </div>
  );
};

export default Dashboard;
