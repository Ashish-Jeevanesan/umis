import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from './context/AuthContext';
import { api } from './api/client';
import { consumePendingOAuth } from './api/oauthSession';
import { CheckCircle2, AlertTriangle, X } from 'lucide-react';
import { Header } from './components/layout/Header';
import { Sidebar } from './components/layout/Sidebar';
import { GlobalOverview } from './components/dashboard/GlobalOverview';
import { TenantOverview } from './components/dashboard/TenantOverview';
import { MailboxList } from './components/mailboxes/MailboxList';
import { ConnectMailboxModal } from './components/mailboxes/ConnectMailboxModal';
import { EmailActivityTable } from './components/emails/EmailActivityTable';
import { EmailDetailDrawer } from './components/emails/EmailDetailDrawer';
import { TenantManagement } from './components/tenants/TenantManagement';
import { SystemHealthView } from './components/health/SystemHealthView';

export const AppContent: React.FC = () => {
  const { currentUser } = useAuth();
  const [activeTab, setActiveTab] = useState('overview');
  const [isConnectModalOpen, setIsConnectModalOpen] = useState(false);
  const [selectedEmailId, setSelectedEmailId] = useState<string | null>(null);
  const [oauthMessage, setOauthMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const isGlobal = currentUser.role === 'GLOBAL_ADMIN';

  // Global dashboard query
  const { data: globalData, refetch: refetchGlobal } = useQuery({
    queryKey: ['globalDashboard'],
    queryFn: () => api.getGlobalDashboard(),
    enabled: isGlobal,
  });

  // Tenant dashboard query
  const { data: tenantData, refetch: refetchTenant } = useQuery({
    queryKey: ['tenantDashboard', currentUser.tenantId],
    queryFn: () => api.getTenantDashboard(currentUser.tenantId),
    enabled: !isGlobal,
  });

  // Mailboxes query
  const { data: mailboxes = [], refetch: refetchMailboxes } = useQuery({
    queryKey: ['mailboxes', currentUser.tenantId],
    queryFn: () => api.listMailboxes(currentUser.tenantId),
  });

  // Emails query
  const { data: emails = [], refetch: refetchEmails } = useQuery({
    queryKey: ['emails', currentUser.tenantId],
    queryFn: () => api.listEmails(currentUser.tenantId),
  });

  // Tenants query
  const { data: tenants = [], refetch: refetchTenants } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => api.listTenants(),
    enabled: isGlobal,
  });

  // Completes an OAuth mailbox connection after the provider redirects the
  // browser back here with ?code=...&state=.... Runs once on mount.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    const providerError = params.get('error');

    if (!code && !state && !providerError) {
      return;
    }

    // Always strip OAuth query params so a page refresh doesn't replay them.
    window.history.replaceState({}, '', window.location.pathname);

    if (providerError) {
      setOauthMessage({ type: 'error', text: `Provider denied access: ${providerError}` });
      return;
    }

    const pending = consumePendingOAuth();
    if (!pending || !code || pending.state !== state) {
      setOauthMessage({ type: 'error', text: 'OAuth session expired or was tampered with. Please try connecting again.' });
      return;
    }

    api
      .connectMailbox({
        tenantId: pending.tenantId,
        provider: pending.provider,
        displayName: pending.displayName,
        authorizationCode: code,
        codeVerifier: pending.codeVerifier,
        redirectUri: pending.redirectUri,
        state,
      })
      .then(() => {
        setOauthMessage({ type: 'success', text: `${pending.provider} mailbox connected successfully.` });
        refetchMailboxes();
      })
      .catch((err) => {
        setOauthMessage({
          type: 'error',
          text: err instanceof Error ? err.message : 'Failed to complete mailbox connection.',
        });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleRefreshAll = () => {
    refetchGlobal();
    refetchTenant();
    refetchMailboxes();
    refetchEmails();
    refetchTenants();
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col">
      <Header />

      <div className="flex-1 flex overflow-hidden">
        <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />

        <main className="flex-1 overflow-y-auto p-6">
          <div className="max-w-7xl mx-auto space-y-6">
            {oauthMessage && (
              <div
                className={`p-3 rounded-xl border text-xs flex items-start gap-2.5 ${
                  oauthMessage.type === 'success'
                    ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                    : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                }`}
              >
                {oauthMessage.type === 'success' ? (
                  <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                )}
                <p className="flex-1">{oauthMessage.text}</p>
                <button onClick={() => setOauthMessage(null)} className="shrink-0 hover:opacity-70">
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}

            {activeTab === 'overview' && (
              <>
                {isGlobal && globalData && (
                  <GlobalOverview data={globalData} onNavigateTab={setActiveTab} />
                )}
                {!isGlobal && tenantData && (
                  <TenantOverview data={tenantData} onNavigateTab={setActiveTab} />
                )}
              </>
            )}

            {activeTab === 'tenants' && isGlobal && (
              <TenantManagement tenants={tenants} onRefresh={handleRefreshAll} />
            )}

            {activeTab === 'mailboxes' && (
              <MailboxList
                mailboxes={mailboxes}
                onRefresh={handleRefreshAll}
                onOpenConnectModal={() => setIsConnectModalOpen(true)}
              />
            )}

            {activeTab === 'emails' && (
              <EmailActivityTable emails={emails} onSelectEmail={setSelectedEmailId} />
            )}

            {activeTab === 'processing' && (
              <div className="space-y-4">
                <div className="bg-slate-800/40 border border-slate-800 rounded-xl p-4 text-xs text-slate-300">
                  <p className="font-semibold text-white mb-1">Downstream Processing Isolation</p>
                  <p className="text-slate-400">
                    Emails ingested by UMIS are processed independently by registered consumers
                    (e.g. Purchase Order extraction, CRM linking, Invoice matching). Select an email below to view consumer outcomes.
                  </p>
                </div>
                <EmailActivityTable emails={emails} onSelectEmail={setSelectedEmailId} />
              </div>
            )}

            {activeTab === 'health' && isGlobal && <SystemHealthView />}

            {activeTab === 'settings' && (
              <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-6 text-xs text-slate-300 space-y-4">
                <h2 className="text-sm font-semibold text-white">System Settings & Webhook URLs</h2>
                <div className="space-y-2 font-mono text-[11px] bg-slate-900/60 p-4 rounded-lg border border-slate-800">
                  <p className="text-slate-400"># Provider Webhook Listener Endpoints</p>
                  <p className="text-sky-400">POST /api/v1/webhooks/microsoft</p>
                  <p className="text-sky-400">POST /api/v1/webhooks/google</p>
                  <p className="text-sky-400">POST /api/v1/webhooks/unified-provider</p>
                  <p className="text-slate-400 pt-2"># Consumer Acknowledgement Endpoint</p>
                  <p className="text-emerald-400">POST /api/v1/emails/{'{emailId}'}/acknowledgements</p>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>

      <ConnectMailboxModal
        isOpen={isConnectModalOpen}
        onClose={() => setIsConnectModalOpen(false)}
        onSuccess={handleRefreshAll}
      />

      <EmailDetailDrawer
        emailId={selectedEmailId}
        onClose={() => setSelectedEmailId(null)}
      />
    </div>
  );
};
