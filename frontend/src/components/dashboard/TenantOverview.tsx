import React from 'react';
import { TenantDashboardData } from '../../types';
import { KpiCard } from '../common/KpiCard';
import { StatusBadge } from '../common/StatusBadge';
import {
  Inbox,
  Mail,
  CheckCircle2,
  Clock,
  ArrowRight,
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';

interface TenantOverviewProps {
  data: TenantDashboardData;
  onNavigateTab: (tab: string) => void;
}

export const TenantOverview: React.FC<TenantOverviewProps> = ({ data, onNavigateTab }) => {
  return (
    <div className="space-y-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <KpiCard
          title="Connected Mailboxes"
          value={data.connectedMailboxes}
          subtitle="Active ingestion"
          icon={Inbox}
          variant="default"
        />
        <KpiCard
          title="Emails Ingested"
          value={data.emailsIngested.toLocaleString()}
          subtitle={`${data.emailsPublished} published to queue`}
          icon={Mail}
          variant="success"
        />
        <KpiCard
          title="Parsed by Downstream"
          value={data.emailsParsedSuccess.toLocaleString()}
          subtitle="e.g. POs & CRM records"
          icon={CheckCircle2}
          variant="success"
        />
        <KpiCard
          title="Pending / Review"
          value={data.emailsPending}
          subtitle={`${data.emailsFailed} failures`}
          icon={Clock}
          variant={data.emailsPending > 0 ? 'warning' : 'default'}
        />
      </div>

      {/* Ingestion Trend Chart */}
      <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-5 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-sm font-semibold text-white">Ingestion Activity & Downstream Outcomes</h3>
            <p className="text-xs text-slate-400">Past 7 days volume for {data.tenantName}</p>
          </div>
          <div className="flex items-center gap-4 text-xs">
            <span className="flex items-center gap-1.5 text-sky-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-sky-500" /> Ingested
            </span>
            <span className="flex items-center gap-1.5 text-emerald-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-emerald-500" /> Parsed Downstream
            </span>
          </div>
        </div>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data.trends} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="tenantIngestedGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#0ea5e9" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#0ea5e9" stopOpacity={0.0} />
                </linearGradient>
                <linearGradient id="tenantParsedGradient" x1="0" y1="0" x2="0" y2="1">
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
              <Area type="monotone" dataKey="ingested" stroke="#0ea5e9" fill="url(#tenantIngestedGradient)" strokeWidth={2} />
              <Area type="monotone" dataKey="parsedSuccess" stroke="#10b981" fill="url(#tenantParsedGradient)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Connected Mailboxes Overview Card */}
      <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-4 border-b border-slate-700/60 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-white">Active Mailbox Connections</h3>
            <p className="text-xs text-slate-400">Configured mailboxes for this organization</p>
          </div>
          <button
            onClick={() => onNavigateTab('mailboxes')}
            className="text-xs font-medium text-sky-400 hover:text-sky-300 flex items-center gap-1"
          >
            Manage Mailboxes <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
        <div className="divide-y divide-slate-800">
          {data.mailboxes.map((mailbox) => (
            <div key={mailbox.id} className="p-4 flex items-center justify-between hover:bg-slate-700/20 transition-colors">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-slate-700 flex items-center justify-center text-slate-300">
                  <Inbox className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-xs font-medium text-white">{mailbox.emailAddress}</p>
                  <p className="text-[11px] text-slate-400">
                    {mailbox.displayName} &bull; <span className="font-mono">{mailbox.provider}</span>
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-4">
                <StatusBadge status={mailbox.status} />
                <span className="text-xs text-slate-400">
                  Last sync: {mailbox.lastSuccessfulSyncAt ? new Date(mailbox.lastSuccessfulSyncAt).toLocaleTimeString() : 'Never'}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
