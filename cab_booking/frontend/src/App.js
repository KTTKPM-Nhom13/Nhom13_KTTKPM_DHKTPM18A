import React from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import DemoPage from './pages/DemoPage';
import { AuthProvider } from './auth/AuthContext';
import './api/axiosConfig';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="top-center"
          toastOptions={{
            style: {
              background: '#132238',
              color: '#f8fafc',
              borderRadius: '12px',
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
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
