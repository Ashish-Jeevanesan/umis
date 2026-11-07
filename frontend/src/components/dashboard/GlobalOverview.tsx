import React from 'react';
import { GlobalDashboardData } from '../../types';
import { KpiCard } from '../common/KpiCard';
import { StatusBadge } from '../common/StatusBadge';
import {
  Building2,
  Inbox,
  Mail,
  CheckCircle2,
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  BarChart,
  Bar,
} from 'recharts';

interface GlobalOverviewProps {
  data: GlobalDashboardData;
  onNavigateTab: (tab: string) => void;
}

export const GlobalOverview: React.FC<GlobalOverviewProps> = ({ data, onNavigateTab }) => {
  return (
    <div className="space-y-6">
      {/* Top KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <KpiCard
          title="Total Tenants"
          value={data.totalTenants}
          subtitle={`${data.activeTenants} active`}
          icon={Building2}
          variant="default"
        />
        <KpiCard
          title="Connected Mailboxes"
          value={data.connectedMailboxes}
          subtitle="Across all tenants"
          icon={Inbox}
          variant="default"
        />
        <KpiCard
          title="Emails Ingested"
          value={data.totalEmailsIngested.toLocaleString()}
          subtitle={`${data.totalEmailsPublished.toLocaleString()} published`}
          icon={Mail}
          variant="success"
        />
        <KpiCard
          title="Parsed by Consumers"
          value={data.emailsParsedSuccess.toLocaleString()}
          subtitle={`${data.emailsFailed} failed`}
          icon={CheckCircle2}
          variant={data.emailsFailed > 0 ? 'warning' : 'success'}
        />
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Ingestion & Downstream Trends Chart */}
        <div className="lg:col-span-2 bg-slate-800/60 border border-slate-700/60 rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm font-semibold text-white">Daily Ingestion & Downstream Processing</h3>
              <p className="text-xs text-slate-400">Ingested emails vs successfully parsed downstream</p>
            </div>
            <div className="flex items-center gap-4 text-xs">
              <span className="flex items-center gap-1.5 text-sky-400">
                <span className="w-2.5 h-2.5 rounded-sm bg-sky-500" /> Ingested
              </span>
              <span className="flex items-center gap-1.5 text-emerald-400">
                <span className="w-2.5 h-2.5 rounded-sm bg-emerald-500" /> Parsed Success
              </span>
            </div>
          </div>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={data.recentTrends} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="ingestedGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#0ea5e9" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#0ea5e9" stopOpacity={0.0} />
                  </linearGradient>
                  <linearGradient id="parsedGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.5} />
                <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#334155', borderRadius: '8px' }}
                />
                <Area type="monotone" dataKey="ingested" stroke="#0ea5e9" fill="url(#ingestedGradient)" strokeWidth={2} />
                <Area type="monotone" dataKey="parsedSuccess" stroke="#10b981" fill="url(#parsedGradient)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Provider Distribution Chart */}
        <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-5 shadow-sm">
          <h3 className="text-sm font-semibold text-white mb-1">Mailbox Providers</h3>
          <p className="text-xs text-slate-400 mb-4">Connected mailboxes by protocol</p>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.providerDistribution} layout="vertical" margin={{ top: 0, right: 20, left: 20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.5} horizontal={false} />
                <XAxis type="number" stroke="#64748b" fontSize={11} tickLine={false} />
                <YAxis dataKey="displayName" type="category" stroke="#94a3b8" fontSize={11} tickLine={false} width={90} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', borderColor: '#334155', borderRadius: '8px' }}
                />
                <Bar dataKey="mailboxCount" fill="#38bdf8" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Recent Organizations Table */}
      <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-4 border-b border-slate-700/60 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-white">Recent Organizations (Tenants)</h3>
            <p className="text-xs text-slate-400">Registered multi-tenant organizations</p>
          </div>
          <button
            onClick={() => onNavigateTab('tenants')}
            className="text-xs font-medium text-sky-400 hover:text-sky-300"
          >
            Manage All Tenants &rarr;
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-900/40 text-slate-400 uppercase tracking-wider text-[10px]">
              <tr>
                <th className="px-5 py-3 font-semibold">Tenant Name</th>
                <th className="px-5 py-3 font-semibold">Tenant Code</th>
                <th className="px-5 py-3 font-semibold">Status</th>
                <th className="px-5 py-3 font-semibold">Created Date</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800 text-slate-300">
              {data.recentTenants.map((tenant) => (
                <tr key={tenant.id} className="hover:bg-slate-700/30 transition-colors">
                  <td className="px-5 py-3 font-medium text-white">{tenant.name}</td>
                  <td className="px-5 py-3 font-mono text-slate-400">{tenant.code}</td>
                  <td className="px-5 py-3">
                    <StatusBadge status={tenant.status} />
                  </td>
                  <td className="px-5 py-3 text-slate-400">
                    {new Date(tenant.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
