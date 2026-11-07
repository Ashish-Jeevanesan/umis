import React, { useState } from 'react';
import { MailboxConnection } from '../../types';
import { StatusBadge } from '../common/StatusBadge';
import { api } from '../../api/client';
import { Plus, RefreshCw, PowerOff, ShieldCheck } from 'lucide-react';

interface MailboxListProps {
  mailboxes: MailboxConnection[];
  onRefresh: () => void;
  onOpenConnectModal: () => void;
}

export const MailboxList: React.FC<MailboxListProps> = ({
  mailboxes,
  onRefresh,
  onOpenConnectModal,
}) => {
  const [syncingId, setSyncingId] = useState<string | null>(null);
  const [healthCheckingId, setHealthCheckingId] = useState<string | null>(null);

  const handleManualSync = async (mailboxId: string) => {
    setSyncingId(mailboxId);
    try {
      await api.triggerSync(mailboxId);
      onRefresh();
    } finally {
      setSyncingId(null);
    }
  };

  const handleHealthCheck = async (mailboxId: string) => {
    setHealthCheckingId(mailboxId);
    try {
      await api.checkMailboxHealth(mailboxId);
      onRefresh();
    } finally {
      setHealthCheckingId(null);
    }
  };

  const handleDisconnect = async (mailboxId: string) => {
    if (confirm('Are you sure you want to disconnect this mailbox? Ingestion will stop.')) {
      await api.disconnectMailbox(mailboxId);
      onRefresh();
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-white tracking-tight">Connected Mailboxes</h2>
          <p className="text-xs text-slate-400">
            Configure incoming email connections across Microsoft Graph, Gmail, IMAP, and Unified APIs.
          </p>
        </div>
        <button
          onClick={onOpenConnectModal}
          className="bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium px-3.5 py-2 rounded-lg flex items-center gap-2 shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Connect Mailbox</span>
        </button>
      </div>

      <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-900/40 text-slate-400 uppercase tracking-wider text-[10px]">
              <tr>
                <th className="px-5 py-3 font-semibold">Mailbox Address</th>
                <th className="px-5 py-3 font-semibold">Provider</th>
                <th className="px-5 py-3 font-semibold">Status</th>
                <th className="px-5 py-3 font-semibold">Strategy</th>
                <th className="px-5 py-3 font-semibold">Last Successful Sync</th>
                <th className="px-5 py-3 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800 text-slate-300">
              {mailboxes.map((mailbox) => (
                <tr key={mailbox.id} className="hover:bg-slate-700/20 transition-colors">
                  <td className="px-5 py-3">
                    <div className="font-medium text-white">{mailbox.emailAddress}</div>
                    <div className="text-[11px] text-slate-400">{mailbox.displayName}</div>
                  </td>
                  <td className="px-5 py-3 font-mono text-[11px] text-slate-300">{mailbox.provider}</td>
                  <td className="px-5 py-3">
                    <StatusBadge status={mailbox.status} />
                  </td>
                  <td className="px-5 py-3 text-slate-400">
                    {mailbox.syncStrategy} ({mailbox.pollingIntervalMinutes}m)
                  </td>
                  <td className="px-5 py-3 text-slate-400">
                    {mailbox.lastSuccessfulSyncAt
                      ? new Date(mailbox.lastSuccessfulSyncAt).toLocaleString()
                      : 'Never'}
                  </td>
                  <td className="px-5 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button
                        title="Trigger Immediate Sync"
                        onClick={() => handleManualSync(mailbox.id)}
                        disabled={syncingId === mailbox.id}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-sky-400 hover:bg-slate-700/50 transition-colors"
                      >
                        <RefreshCw className={`w-3.5 h-3.5 ${syncingId === mailbox.id ? 'animate-spin text-sky-400' : ''}`} />
                      </button>
                      <button
                        title="Test Health & Connectivity"
                        onClick={() => handleHealthCheck(mailbox.id)}
                        disabled={healthCheckingId === mailbox.id}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-slate-700/50 transition-colors"
                      >
                        <ShieldCheck className="w-3.5 h-3.5" />
                      </button>
                      {mailbox.status !== 'DISCONNECTED' && (
                        <button
                          title="Disconnect Mailbox"
                          onClick={() => handleDisconnect(mailbox.id)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-700/50 transition-colors"
                        >
                          <PowerOff className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
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
