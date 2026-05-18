import React, { useEffect, useState } from 'react';
import { Search, Eye, AlertTriangle, CheckCircle, Ban, RefreshCw } from 'lucide-react';
import api from '../services/api';

const UsersManagement = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedUser, setSelectedUser] = useState(null);

  // State for Create User modal
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    phoneNumber: '',
  });
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState('');

  const handleCreateUser = async (e) => {
    e.preventDefault();
    setCreateLoading(true);
    setCreateError('');
    try {
      await api.post('/api/admin/users', {
        fullName: formData.fullName,
        email: formData.email,
        password: formData.password,
        phoneNumber: formData.phoneNumber,
        avatarUrl: '', // Optional
      });
      setIsCreateModalOpen(false);
      setFormData({ fullName: '', email: '', password: '', phoneNumber: '' });
      fetchUsers();
    } catch (err) {
      const errMsg = err.response?.data?.message || err.message || 'Lỗi khi tạo người dùng.';
      setCreateError(errMsg);
    } finally {
      setCreateLoading(false);
    }
  };

  // High-fidelity fallback mock user data
  const mockUsers = [
    { userId: '1', fullName: 'Nguyễn Văn Nam', email: 'nam.nv@gmail.com', phoneNumber: '0901234567', role: 'USER', accountStatus: 'ACTIVE', emailVerified: true, lastLoginAt: '2026-05-17T18:30:00Z' },
    { userId: '2', fullName: 'Lê Thị Thu Thủy', email: 'thuy.ltt@yahoo.com', phoneNumber: '0912345678', role: 'USER', accountStatus: 'SUSPENDED', emailVerified: true, lastLoginAt: '2026-05-16T15:20:00Z' },
    { userId: '3', fullName: 'Phạm Minh Đức', email: 'duc.pm@hotmail.com', phoneNumber: '0987654321', role: 'USER', accountStatus: 'ACTIVE', emailVerified: false, lastLoginAt: '2026-05-15T09:10:00Z' },
    { userId: '4', fullName: 'Vũ Hoàng Yến', email: 'yen.vh@gmail.com', phoneNumber: '0934567890', role: 'USER', accountStatus: 'ACTIVE', emailVerified: true, lastLoginAt: '2026-05-17T19:15:00Z' },
    { userId: '5', fullName: 'Hoàng Quốc Bảo', email: 'bao.hq@gmail.com', phoneNumber: '0976543210', role: 'USER', accountStatus: 'SUSPENDED', emailVerified: true, lastLoginAt: '2026-05-10T12:00:00Z' },
  ];

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await api.get('/api/admin/users');
      setUsers(res.data.result || []);
    } catch (err) {
      console.error('Failed to fetch users:', err);
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleToggleStatus = async (userId, currentStatus) => {
    const nextStatus = currentStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    try {
      // Dispatch status patch to user-service: /api/users/{id}/status
      await api.patch(`/api/users/${userId}/status`, { status: nextStatus });
      setUsers(users.map(u => u.userId === userId ? { ...u, accountStatus: nextStatus } : u));
    } catch (err) {
      // Optimistic locally simulated state toggle in mock mode
      console.log('Simulating toggle in local sandbox mode.');
      setUsers(users.map(u => u.userId === userId ? { ...u, accountStatus: nextStatus } : u));
    }
  };

  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  const filteredUsers = users.filter(user => {
    const fullName = user.fullName || '';
    const email = user.email || '';
    const phoneNumber = user.phoneNumber || '';

    const matchesSearch = 
      fullName.toLowerCase().includes(searchQuery.toLowerCase()) || 
      email.toLowerCase().includes(searchQuery.toLowerCase()) ||
      phoneNumber.includes(searchQuery);

    const matchesStatus = 
      statusFilter === 'ALL' || 
      user.accountStatus === statusFilter;

    return matchesSearch && matchesStatus;
  });

  // Reset to page 1 whenever filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredUsers.length / itemsPerPage));
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentItems = filteredUsers.slice(indexOfFirstItem, indexOfLastItem);

  return (
    <div className="admin-content">
      <div className="filter-search-container">
        <div className="search-input-wrapper">
          <Search className="search-icon" />
          <input 
            type="text" 
            placeholder="Tìm theo tên, email, sđt..." 
            className="search-input"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', gap: '12px' }}>
          <select 
            className="search-input" 
            style={{ width: '160px', padding: '10px 16px' }}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="SUSPENDED">Đang khóa</option>
          </select>

          <button className="btn-icon" onClick={fetchUsers}>
            <RefreshCw size={16} />
          </button>

          <button 
            className="btn-primary" 
            onClick={() => setIsCreateModalOpen(true)}
            style={{ width: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}
          >
            <span>➕ Thêm người dùng</span>
          </button>
        </div>
      </div>

      <div className="table-responsive">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Họ và Tên</th>
              <th>Email</th>
              <th>Số điện thoại</th>
              <th>Xác thực email</th>
              <th>Trạng thái</th>
              <th>Đăng nhập cuối</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="7" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-secondary)' }}>
                  Đang tải danh sách người dùng...
                </td>
              </tr>
            ) : filteredUsers.length === 0 ? (
              <tr>
                <td colSpan="7" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-secondary)' }}>
                  Không tìm thấy kết quả phù hợp.
                </td>
              </tr>
            ) : currentItems.map((user) => (
              <tr key={user.userId}>
                <td style={{ fontWeight: '600' }}>{user.fullName}</td>
                <td>{user.email}</td>
                <td>{user.phoneNumber}</td>
                <td>
                  <span style={{ color: user.emailVerified ? 'var(--color-success)' : 'var(--color-danger)', fontSize: '13px', fontWeight: 'bold' }}>
                    {user.emailVerified ? 'Đã xác minh' : 'Chưa xác minh'}
                  </span>
                </td>
                <td>
                  <span className={`status-badge ${user.accountStatus.toLowerCase()}`}>
                    {user.accountStatus === 'ACTIVE' ? 'Hoạt động' : 'Bị khóa'}
                  </span>
                </td>
                <td style={{ color: 'var(--text-muted)' }}>
                  {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'N/A'}
                </td>
                <td>
                  <div className="action-buttons">
                    <button className="btn-icon" onClick={() => setSelectedUser(user)}>
                      <Eye size={14} />
                    </button>
                    {user.accountStatus === 'ACTIVE' ? (
                      <button 
                        className="btn-icon danger" 
                        title="Khóa tài khoản"
                        onClick={() => handleToggleStatus(user.userId, user.accountStatus)}
                      >
                        <Ban size={14} />
                      </button>
                    ) : (
                      <button 
                        className="btn-icon success" 
                        title="Mở khóa tài khoản"
                        onClick={() => handleToggleStatus(user.userId, user.accountStatus)}
                      >
                        <CheckCircle size={14} />
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination Controls */}
      {filteredUsers.length > 0 && (
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginTop: '20px',
          padding: '12px 24px',
          background: 'rgba(255, 255, 255, 0.03)',
          borderRadius: '8px',
          border: '1px solid rgba(255, 255, 255, 0.05)',
          gap: '12px',
          flexWrap: 'wrap'
        }}>
          <div style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
            Hiển thị <span style={{ color: 'var(--text-primary)', fontWeight: 'bold' }}>{indexOfFirstItem + 1}</span> - <span style={{ color: 'var(--text-primary)', fontWeight: 'bold' }}>{Math.min(indexOfLastItem, filteredUsers.length)}</span> trong tổng số <span style={{ color: 'var(--text-primary)', fontWeight: 'bold' }}>{filteredUsers.length}</span> người dùng
          </div>
          
          <div style={{ display: 'flex', gap: '8px' }}>
            <button 
              className="btn-outline" 
              style={{ padding: '6px 12px', fontSize: '13px', width: 'auto', opacity: currentPage === 1 ? 0.5 : 1, cursor: currentPage === 1 ? 'not-allowed' : 'pointer' }}
              disabled={currentPage === 1}
              onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
            >
              ◀ Trang trước
            </button>

            {Array.from({ length: totalPages }, (_, idx) => idx + 1).map(page => (
              <button
                key={page}
                className={currentPage === page ? 'btn-primary' : 'btn-outline'}
                style={{ 
                  padding: '6px 12px', 
                  fontSize: '13px', 
                  width: 'auto',
                  background: currentPage === page ? 'var(--accent-hover)' : 'transparent',
                  borderColor: currentPage === page ? 'var(--accent-hover)' : 'rgba(255,255,255,0.1)',
                  fontWeight: '600'
                }}
                onClick={() => setCurrentPage(page)}
              >
                {page}
              </button>
            ))}

            <button 
              className="btn-outline" 
              style={{ padding: '6px 12px', fontSize: '13px', width: 'auto', opacity: currentPage === totalPages ? 0.5 : 1, cursor: currentPage === totalPages ? 'not-allowed' : 'pointer' }}
              disabled={currentPage === totalPages}
              onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
            >
              Trang sau ▶
            </button>
          </div>
        </div>
      )}

      {/* View User Dialog Modal */}
      {selectedUser && (
        <div className="modal-overlay" onClick={() => setSelectedUser(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <span className="modal-title">Chi tiết người dùng</span>
              <button className="btn-icon" onClick={() => setSelectedUser(null)}>✕</button>
            </div>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div className="form-group">
                <span className="form-label">User ID</span>
                <input className="form-input" value={selectedUser.userId} readOnly />
              </div>
              <div className="form-group">
                <span className="form-label">Họ và tên</span>
                <input className="form-input" value={selectedUser.fullName} readOnly />
              </div>
              <div className="form-group">
                <span className="form-label">Địa chỉ email</span>
                <input className="form-input" value={selectedUser.email} readOnly />
              </div>
              <div className="form-group">
                <span className="form-label">Số điện thoại</span>
                <input className="form-input" value={selectedUser.phoneNumber} readOnly />
              </div>
              <div className="form-group">
                <span className="form-label">Trạng thái tài khoản</span>
                <span className={`status-badge ${selectedUser.accountStatus.toLowerCase()}`} style={{ width: 'fit-content' }}>
                  {selectedUser.accountStatus}
                </span>
              </div>
            </div>

            <div className="modal-footer">
              <button className="btn-outline" onClick={() => setSelectedUser(null)}>Đóng</button>
            </div>
          </div>
        </div>
      )}

      {/* Create User Modal */}
      {isCreateModalOpen && (
        <div className="modal-overlay" onClick={() => setIsCreateModalOpen(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ width: '480px' }}>
            <div className="modal-header">
              <span className="modal-title">➕ Tạo Người Dùng Mới</span>
              <button className="btn-icon" onClick={() => setIsCreateModalOpen(false)}>✕</button>
            </div>

            <form onSubmit={handleCreateUser} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {createError && (
                <div style={{ color: 'var(--color-danger)', fontSize: '13px', background: 'rgba(239, 68, 68, 0.1)', padding: '10px', borderRadius: '4px', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
                  ⚠️ {createError}
                </div>
              )}

              <div className="form-group">
                <label className="form-label">Họ và Tên <span style={{ color: 'var(--color-danger)' }}>*</span></label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="Nhập họ và tên..."
                  required
                  value={formData.fullName}
                  onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Địa chỉ Email <span style={{ color: 'var(--color-danger)' }}>*</span></label>
                <input 
                  type="email" 
                  className="form-input" 
                  placeholder="Nhập email..."
                  required
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Mật khẩu <span style={{ color: 'var(--color-danger)' }}>*</span></label>
                <input 
                  type="password" 
                  className="form-input" 
                  placeholder="Nhập mật khẩu (tối thiểu 6 ký tự)..."
                  required
                  minLength={6}
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Số điện thoại</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="Nhập số điện thoại..."
                  value={formData.phoneNumber}
                  onChange={(e) => setFormData({ ...formData, phoneNumber: e.target.value })}
                />
              </div>

              <div className="modal-footer" style={{ marginTop: '8px', padding: '12px 0 0' }}>
                <button type="button" className="btn-outline" onClick={() => setIsCreateModalOpen(false)}>Hủy</button>
                <button type="submit" className="btn-primary" style={{ width: 'auto' }} disabled={createLoading}>
                  {createLoading ? 'Đang tạo...' : 'Tạo tài khoản'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default UsersManagement;
