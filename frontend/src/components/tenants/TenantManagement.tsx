import React, { useState } from 'react';
import { Tenant } from '../../types';
import { StatusBadge } from '../common/StatusBadge';
import { api } from '../../api/client';
import { Plus, X, Power, PowerOff } from 'lucide-react';

interface TenantManagementProps {
  tenants: Tenant[];
  onRefresh: () => void;
}

export const TenantManagement: React.FC<TenantManagementProps> = ({ tenants, onRefresh }) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCreateTenant = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await api.createTenant({ code, name });
      setCode('');
      setName('');
      setIsModalOpen(false);
      onRefresh();
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (tenantId: string, currentStatus: string) => {
    await api.toggleTenantStatus(tenantId, currentStatus);
    onRefresh();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-white tracking-tight">Organization Tenants</h2>
          <p className="text-xs text-slate-400">
            Multi-tenant organization registry and account lifecycle administration.
          </p>
        </div>
        <button
          onClick={() => setIsModalOpen(true)}
          className="bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium px-3.5 py-2 rounded-lg flex items-center gap-2 shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Add Tenant</span>
        </button>
      </div>

      <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-900/40 text-slate-400 uppercase tracking-wider text-[10px]">
              <tr>
                <th className="px-5 py-3 font-semibold">Organization Name</th>
                <th className="px-5 py-3 font-semibold">Tenant Identifier</th>
                <th className="px-5 py-3 font-semibold">Status</th>
                <th className="px-5 py-3 font-semibold">Created Date</th>
                <th className="px-5 py-3 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800 text-slate-300">
              {tenants.map((tenant) => (
                <tr key={tenant.id} className="hover:bg-slate-700/20 transition-colors">
                  <td className="px-5 py-3 font-medium text-white">{tenant.name}</td>
                  <td className="px-5 py-3 font-mono text-[11px] text-slate-400">{tenant.code}</td>
                  <td className="px-5 py-3">
                    <StatusBadge status={tenant.status} />
                  </td>
                  <td className="px-5 py-3 text-slate-400">
                    {new Date(tenant.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-5 py-3 text-right">
                    <button
                      onClick={() => handleToggleStatus(tenant.id, tenant.status)}
                      title={tenant.status === 'ACTIVE' ? 'Suspend Tenant' : 'Activate Tenant'}
                      className={`p-1.5 rounded-lg transition-colors ${
                        tenant.status === 'ACTIVE'
                          ? 'text-slate-400 hover:text-amber-400 hover:bg-slate-700/50'
                          : 'text-slate-400 hover:text-emerald-400 hover:bg-slate-700/50'
                      }`}
                    >
                      {tenant.status === 'ACTIVE' ? <PowerOff className="w-3.5 h-3.5" /> : <Power className="w-3.5 h-3.5" />}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md shadow-2xl p-6">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="text-sm font-semibold text-white">Create Organization Tenant</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-200">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleCreateTenant} className="mt-4 space-y-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1">Organization Name</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Wayne Enterprises"
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500"
                />
              </div>
              <div>
                <label className="block text-slate-400 mb-1">Tenant Code (Slug)</label>
                <input
                  type="text"
                  required
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="e.g. wayne-enterprises"
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-sky-500 font-mono text-[11px]"
                />
              </div>
              <div className="pt-3 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-3.5 py-1.5 rounded-lg bg-slate-800 text-slate-300"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="px-3.5 py-1.5 rounded-lg bg-sky-600 text-white font-medium"
                >
                  {loading ? 'Creating...' : 'Create Tenant'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
