import React from 'react';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  Building2,
  Inbox,
  Mail,
  Activity,
  HeartPulse,
  Settings,
} from 'lucide-react';

interface SidebarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab }) => {
  const { currentUser } = useAuth();
  const isGlobal = currentUser.role === 'GLOBAL_ADMIN';

  const navItems = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    ...(isGlobal ? [{ id: 'tenants', label: 'Tenants', icon: Building2 }] : []),
    { id: 'mailboxes', label: 'Mailboxes', icon: Inbox },
    { id: 'emails', label: 'Email Activity', icon: Mail },
    { id: 'processing', label: 'Downstream Stats', icon: Activity },
    ...(isGlobal ? [{ id: 'health', label: 'System Health', icon: HeartPulse }] : []),
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <aside className="w-60 border-r border-slate-800 bg-slate-900/60 p-4 flex flex-col justify-between shrink-0">
      <div className="space-y-1">
        <p className="px-3 py-1.5 text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
          {isGlobal ? 'Global Administration' : 'Organization Dashboard'}
        </p>

        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-colors ${
                isActive
                  ? 'bg-sky-500/10 text-sky-400 font-semibold'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              }`}
            >
              <Icon className="w-4 h-4" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>

      <div className="p-3 bg-slate-800/40 border border-slate-800 rounded-xl text-[11px] text-slate-400">
        <div className="font-medium text-slate-300">UMIS v1.0.0-PROD</div>
        <div className="text-[10px] text-slate-400 mt-0.5">Standalone Microservice</div>
      </div>
    </aside>
  );
};
