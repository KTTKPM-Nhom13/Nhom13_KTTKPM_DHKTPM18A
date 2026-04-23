import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import DemoPage from './pages/DemoPage';
import './api/axiosConfig'; // đăng ký interceptor toàn cục

function App() {
  return (
    <BrowserRouter>
      {/* Toast notification container */}
      <Toaster
        position="top-center"
        toastOptions={{
          style: {
            background: '#1e293b',
            color: '#f8fafc',
            borderRadius: '8px',
            fontSize: '14px',
          },
          success: {
            iconTheme: { primary: '#22c55e', secondary: '#fff' },
          },
          error: {
            iconTheme: { primary: '#ef4444', secondary: '#fff' },
          },
        }}
      />

      <Routes>
        <Route path="/" element={<DemoPage />} />
        {/* Các route khác của app sẽ thêm ở đây */}
      </Routes>
    </BrowserRouter>
  );
}

export default App;
