import React from 'react';
import { LucideIcon } from 'lucide-react';

interface KpiCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: LucideIcon;
  variant?: 'default' | 'success' | 'warning' | 'danger';
}

export const KpiCard: React.FC<KpiCardProps> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  variant = 'default',
}) => {
  const variantStyles = {
    default: 'text-sky-400 bg-sky-950/40 border-sky-800/40',
    success: 'text-emerald-400 bg-emerald-950/40 border-emerald-800/40',
    warning: 'text-amber-400 bg-amber-950/40 border-amber-800/40',
    danger: 'text-rose-400 bg-rose-950/40 border-rose-800/40',
  };

  return (
    <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-4 shadow-sm backdrop-blur-sm">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">{title}</p>
        <div className={`p-2 rounded-lg border ${variantStyles[variant]}`}>
          <Icon className="w-4 h-4" />
        </div>
      </div>
      <div className="mt-2 flex items-baseline gap-2">
        <p className="text-2xl font-bold text-white tracking-tight">{value}</p>
        {subtitle && <span className="text-xs text-slate-400">{subtitle}</span>}
      </div>
    </div>
  );
};
