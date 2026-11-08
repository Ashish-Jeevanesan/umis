import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { DashboardLayout } from './layouts/DashboardLayout';
import { UmisDashboard } from './pages/umis/UmisDashboard';
import { PoDashboardPage } from './pages/po/PoDashboardPage';
import { PoJobDetailsPage } from './pages/po/PoJobDetailsPage';

export const AppContent: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<DashboardLayout />}>
          <Route path="/" element={<Navigate to="/umis" replace />} />
          <Route path="/umis" element={<UmisDashboard />} />
          <Route path="/po" element={<PoDashboardPage />} />
          <Route path="/po/jobs/:id" element={<PoJobDetailsPage />} />
          <Route path="*" element={<Navigate to="/umis" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};
