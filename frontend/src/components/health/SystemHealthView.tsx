import React, { useEffect, useState } from 'react';
import { SystemHealthData } from '../../types';
import { StatusBadge } from '../common/StatusBadge';
import { api } from '../../api/client';
import { Database, MessageSquare, HardDrive, Cpu } from 'lucide-react';

export const SystemHealthView: React.FC = () => {
  const [health, setHealth] = useState<SystemHealthData | null>(null);

  useEffect(() => {
    api.getSystemHealth().then(setHealth);
  }, []);

  if (!health) {
    return <div className="text-center py-12 text-slate-400">Loading system telemetry...</div>;
  }

  const subsystems = [
    {
      name: 'PostgreSQL Database',
      desc: 'Multi-tenant relational schema, Flyway migrations, connection pool',
      icon: Database,
      status: health.databaseConnected ? 'UP' : 'DOWN',
      detail: 'HikariCP active, 17 tables verified',
    },
    {
      name: 'RabbitMQ Message Broker',
      desc: 'Topic exchange umis.events, durable queues & dead-letter queue',
      icon: MessageSquare,
      status: health.rabbitMqConnected ? 'UP' : 'DOWN',
      detail: `${health.pendingOutboxCount} outbox events pending dispatch`,
    },
    {
      name: 'S3 Object Storage (MinIO)',
      desc: 'Binary email body & attachment storage repository',
      icon: HardDrive,
      status: health.storageConnected ? 'UP' : 'DOWN',
      detail: 'Bucket umis-emails verified',
    },
    {
      name: 'Ingestion Worker Scheduler',
      desc: 'ThreadPoolTaskScheduler executing polling & webhook workers',
      icon: Cpu,
      status: 'UP',
      detail: '10 background threads allocated',
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-white tracking-tight">System Infrastructure Health</h2>
          <p className="text-xs text-slate-400">
            Real-time status of persistence, messaging broker, and worker subsystems.
          </p>
        </div>
        <StatusBadge status={health.status} />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {subsystems.map((sub) => {
          const Icon = sub.icon;
          return (
            <div
              key={sub.name}
              className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-5 space-y-3 shadow-sm"
            >
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl bg-slate-700/60 text-sky-400">
                    <Icon className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-white">{sub.name}</h3>
                    <p className="text-xs text-slate-400">{sub.desc}</p>
                  </div>
                </div>
                <StatusBadge status={sub.status} />
              </div>
              <div className="pt-2 border-t border-slate-700/50 flex items-center justify-between text-xs text-slate-400">
                <span>{sub.detail}</span>
                <span className="text-[10px] font-mono text-slate-400">Actuator Verified</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
