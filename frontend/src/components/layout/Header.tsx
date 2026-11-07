import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { Mail, Shield, Building2, ChevronDown } from 'lucide-react';

export const Header: React.FC = () => {
  const { currentUser, switchRole } = useAuth();

  return (
    <header className="h-16 border-b border-slate-800 bg-slate-900/90 backdrop-blur px-6 flex items-center justify-between sticky top-0 z-20">
      <div className="flex items-center gap-3">
        <div className="w-9 h-9 rounded-lg bg-sky-600 flex items-center justify-center text-white shadow-sm">
          <Mail className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-base font-semibold text-white tracking-tight leading-none">
            UMIS
          </h1>
          <p className="text-xs text-slate-400 mt-1">Universal Mailbox Integration Service</p>
        </div>
      </div>

      <div className="flex items-center gap-4">
        {/* Role & Tenant Switcher for Testing / Demonstration */}
        <div className="relative group">
          <button className="flex items-center gap-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-200 transition-colors">
            {currentUser.role === 'GLOBAL_ADMIN' ? (
              <Shield className="w-3.5 h-3.5 text-sky-400" />
            ) : (
              <Building2 className="w-3.5 h-3.5 text-emerald-400" />
            )}
            <span>
              {currentUser.role === 'GLOBAL_ADMIN'
                ? 'Global Administrator'
                : `${currentUser.tenantName} (Tenant Admin)`}
            </span>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
          </button>

          <div className="absolute right-0 mt-1 w-64 bg-slate-800 border border-slate-700 rounded-xl shadow-xl p-1.5 hidden group-hover:block z-30">
            <p className="px-2.5 py-1 text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
              Switch Context
            </p>
            <button
              onClick={() => switchRole('GLOBAL_ADMIN')}
              className="w-full text-left px-2.5 py-2 text-xs text-slate-200 hover:bg-slate-700/70 rounded-lg flex items-center gap-2 transition-colors"
            >
              <Shield className="w-3.5 h-3.5 text-sky-400" />
              <span>Global Administrator</span>
            </button>
            <button
              onClick={() =>
                switchRole('TENANT_ADMIN', '11111111-1111-1111-1111-111111111111', 'Acme Corporation')
              }
              className="w-full text-left px-2.5 py-2 text-xs text-slate-200 hover:bg-slate-700/70 rounded-lg flex items-center gap-2 transition-colors"
            >
              <Building2 className="w-3.5 h-3.5 text-emerald-400" />
              <span>Acme Corp (Tenant A)</span>
            </button>
            <button
              onClick={() =>
                switchRole('TENANT_ADMIN', '22222222-2222-2222-2222-222222222222', 'Globex Industries')
              }
              className="w-full text-left px-2.5 py-2 text-xs text-slate-200 hover:bg-slate-700/70 rounded-lg flex items-center gap-2 transition-colors"
            >
              <Building2 className="w-3.5 h-3.5 text-emerald-400" />
              <span>Globex (Tenant B)</span>
            </button>
          </div>
        </div>

        <div className="h-4 w-px bg-slate-800" />
        <div className="flex items-center gap-2 text-xs text-slate-400">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span>Operational</span>
        </div>
      </div>
    </header>
  );
};
