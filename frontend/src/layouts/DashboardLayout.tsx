import React from 'react';
import { Outlet } from 'react-router-dom';
import { Header } from '../components/layout/Header';

export const DashboardLayout: React.FC = () => {
  return (
    <div className="min-h-screen bg-slate-950 flex flex-col text-slate-100 antialiased selection:bg-sky-500 selection:text-white">
      <Header />
      <div className="flex-1 flex overflow-hidden">
        <Outlet />
      </div>
    </div>
  );
};
