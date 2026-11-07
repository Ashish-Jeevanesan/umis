import React, { useState } from 'react';
import { EmailMessage } from '../../types';
import { StatusBadge } from '../common/StatusBadge';
import { Paperclip, Search, ChevronRight } from 'lucide-react';

interface EmailActivityTableProps {
  emails: EmailMessage[];
  onSelectEmail: (emailId: string) => void;
}

export const EmailActivityTable: React.FC<EmailActivityTableProps> = ({
  emails,
  onSelectEmail,
}) => {
  const [searchTerm, setSearchTerm] = useState('');

  const filteredEmails = emails.filter((item) => {
    const term = searchTerm.toLowerCase();
    return (
      item.subject?.toLowerCase().includes(term) ||
      item.sender.toLowerCase().includes(term) ||
      item.mailboxEmail.toLowerCase().includes(term)
    );
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-white tracking-tight">Email Ingestion Activity</h2>
          <p className="text-xs text-slate-400">
            Real-time feed of discovered, normalized, and published incoming emails.
          </p>
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search sender or subject..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-slate-800/80 border border-slate-700 rounded-lg pl-9 pr-3 py-1.5 text-xs text-white placeholder-slate-400 focus:outline-none focus:border-sky-500"
          />
        </div>
      </div>

      <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-900/40 text-slate-400 uppercase tracking-wider text-[10px]">
              <tr>
                <th className="px-5 py-3 font-semibold">Sender & Subject</th>
                <th className="px-5 py-3 font-semibold">Mailbox</th>
                <th className="px-5 py-3 font-semibold">Attachments</th>
                <th className="px-5 py-3 font-semibold">Ingestion Status</th>
                <th className="px-5 py-3 font-semibold">Received Time</th>
                <th className="px-5 py-3 font-semibold text-right">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800 text-slate-300">
              {filteredEmails.map((email) => (
                <tr
                  key={email.id}
                  onClick={() => onSelectEmail(email.id)}
                  className="hover:bg-slate-700/30 cursor-pointer transition-colors"
                >
                  <td className="px-5 py-3">
                    <div className="font-medium text-white truncate max-w-md">{email.subject}</div>
                    <div className="text-[11px] text-slate-400 mt-0.5">
                      {email.senderName ? `${email.senderName} <${email.sender}>` : email.sender}
                    </div>
                  </td>
                  <td className="px-5 py-3 font-mono text-[11px] text-slate-400">
                    {email.mailboxEmail}
                  </td>
                  <td className="px-5 py-3">
                    {email.hasAttachments ? (
                      <span className="inline-flex items-center gap-1 bg-slate-700/50 text-slate-300 px-2 py-0.5 rounded text-[11px]">
                        <Paperclip className="w-3 h-3 text-sky-400" />
                        <span>{email.attachmentCount}</span>
                      </span>
                    ) : (
                      <span className="text-slate-400 text-[11px]">None</span>
                    )}
                  </td>
                  <td className="px-5 py-3">
                    <StatusBadge status={email.ingestionStatus} />
                  </td>
                  <td className="px-5 py-3 text-slate-400">
                    {new Date(email.receivedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })},{' '}
                    {new Date(email.receivedAt).toLocaleDateString()}
                  </td>
                  <td className="px-5 py-3 text-right">
                    <ChevronRight className="w-4 h-4 text-slate-400 inline" />
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
