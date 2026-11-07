import React, { useEffect, useState } from 'react';
import { MailboxProvider, SyncStrategy, Tenant } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../api/client';
import { savePendingOAuth } from '../../api/oauthSession';
import { X, Shield, AlertTriangle } from 'lucide-react';

const OAUTH_PROVIDERS: MailboxProvider[] = ['MICROSOFT_365', 'GMAIL'];

interface ConnectMailboxModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const ConnectMailboxModal: React.FC<ConnectMailboxModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const { currentUser } = useAuth();
  const isGlobalAdmin = currentUser.role === 'GLOBAL_ADMIN';

  const [selectedProvider, setSelectedProvider] = useState<MailboxProvider>('MOCK');
  const [emailAddress, setEmailAddress] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [imapHost, setImapHost] = useState('imap.example.com');
  const [imapPort, setImapPort] = useState(993);
  const [imapPassword, setImapPassword] = useState('');
  const [useSsl, setUseSsl] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState('');

  useEffect(() => {
    if (isOpen) {
      setError(null);
      if (isGlobalAdmin) {
        api.listTenants().then(setTenants);
      }
    }
  }, [isOpen, isGlobalAdmin]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const tenantId = isGlobalAdmin ? selectedTenantId : currentUser.tenantId;
    if (!tenantId) {
      setError('Please select an organization to connect this mailbox to.');
      return;
    }

    setError(null);
    setLoading(true);

    try {
      if (OAUTH_PROVIDERS.includes(selectedProvider)) {
        // Real OAuth providers: redirect the browser to the provider's consent
        // screen. The connection is completed after the redirect back (see
        // the OAuth callback handling in App.tsx), not here.
        const redirectUri = window.location.origin + window.location.pathname;
        const init = await api.initiateOAuth(selectedProvider, redirectUri, tenantId);
        savePendingOAuth({
          tenantId,
          provider: selectedProvider,
          codeVerifier: init.codeVerifier,
          redirectUri,
          state: init.state,
          displayName: displayName || emailAddress,
        });
        window.location.href = init.authorizationUrl;
        return;
      }

      const payload = {
        tenantId,
        provider: selectedProvider,
        emailAddress: emailAddress || (selectedProvider === 'MOCK' ? 'sandbox@mock.internal' : ''),
        displayName: displayName || (selectedProvider === 'MOCK' ? 'Simulated Sandbox' : emailAddress),
        syncStrategy: 'POLLING' as SyncStrategy,
        pollingIntervalMinutes: 5,
        authorizationCode: 'mock_auth_code',
        imapHost,
        imapPort,
        imapPassword,
        useSsl,
      };

      await api.connectMailbox(payload);

      onSuccess();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to connect mailbox. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-white">Connect New Mailbox</h3>
            <p className="text-xs text-slate-400">Select protocol and configure incoming ingestion</p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-200">
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4 text-xs">
          {error && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-rose-300 flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <p>{error}</p>
            </div>
          )}

          {isGlobalAdmin && (
            <div>
              <label className="block text-slate-400 font-medium mb-1.5">Organization</label>
              <select
                required
                value={selectedTenantId}
                onChange={(e) => setSelectedTenantId(e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
              >
                <option value="">Select an organization...</option>
                {tenants.map((tenant) => (
                  <option key={tenant.id} value={tenant.id}>
                    {tenant.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Provider Selection */}
          <div>
            <label className="block text-slate-400 font-medium mb-1.5">Select Ingestion Provider</label>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'MOCK', name: 'Simulated Sandbox', desc: 'Demo & Zero-Config' },
                { id: 'MICROSOFT_365', name: 'Microsoft 365', desc: 'Graph API OAuth' },
                { id: 'GMAIL', name: 'Google Workspace', desc: 'Gmail REST API' },
                { id: 'GENERIC_IMAP', name: 'Generic IMAP', desc: 'SSL/TLS Mail Server' },
              ].map((p) => (
                <button
                  type="button"
                  key={p.id}
                  onClick={() => setSelectedProvider(p.id as MailboxProvider)}
                  className={`p-3 rounded-xl border text-left transition-all ${
                    selectedProvider === p.id
                      ? 'border-sky-500 bg-sky-500/10 text-white'
                      : 'border-slate-800 bg-slate-800/40 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  <p className="font-semibold text-xs text-white">{p.name}</p>
                  <p className="text-[10px] text-slate-400 mt-0.5">{p.desc}</p>
                </button>
              ))}
            </div>
          </div>

          {selectedProvider === 'GENERIC_IMAP' ? (
            <div className="space-y-3 pt-2">
              <div>
                <label className="block text-slate-400 mb-1">Email Address</label>
                <input
                  type="email"
                  required
                  value={emailAddress}
                  onChange={(e) => setEmailAddress(e.target.value)}
                  placeholder="orders@mycompany.com"
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
                />
              </div>
              <div className="grid grid-cols-3 gap-2">
                <div className="col-span-2">
                  <label className="block text-slate-400 mb-1">IMAP Host</label>
                  <input
                    type="text"
                    required
                    value={imapHost}
                    onChange={(e) => setImapHost(e.target.value)}
                    className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
                  />
                </div>
                <div>
                  <label className="block text-slate-400 mb-1">Port</label>
                  <input
                    type="number"
                    required
                    value={imapPort}
                    onChange={(e) => setImapPort(parseInt(e.target.value))}
                    className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
                  />
                </div>
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Password or App Password</label>
                <input
                  type="password"
                  required
                  value={imapPassword}
                  onChange={(e) => setImapPassword(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
                />
              </div>
              <label className="flex items-center gap-2 text-slate-300 cursor-pointer pt-1">
                <input
                  type="checkbox"
                  checked={useSsl}
                  onChange={(e) => setUseSsl(e.target.checked)}
                  className="rounded bg-slate-800 border-slate-700 text-sky-500"
                />
                <span>Enable SSL/TLS Encryption (Port 993)</span>
              </label>
            </div>
          ) : (
            <div className="space-y-3 pt-2">
              <div>
                <label className="block text-slate-400 mb-1">Mailbox Display Label</label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder={selectedProvider === 'MOCK' ? 'Demo Inbox' : 'e.g. Sales Inquiries'}
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
                />
              </div>
              <div className="p-3 bg-slate-800/60 border border-slate-800 rounded-xl text-slate-300 flex items-start gap-2.5">
                <Shield className="w-4 h-4 text-sky-400 shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium text-white">OAuth 2.0 Credential Encryption</p>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Tokens are protected with AES-256 envelope encryption. Client secrets and raw tokens are never exposed.
                  </p>
                </div>
              </div>
            </div>
          )}

          <div className="pt-3 border-t border-slate-800 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white font-medium flex items-center gap-2 shadow-sm"
            >
              {loading ? 'Connecting...' : 'Complete Connection'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
