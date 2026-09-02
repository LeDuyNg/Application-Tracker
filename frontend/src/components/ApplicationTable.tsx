import { Link } from 'react-router-dom';

import type { ApplicationSummaryResponse } from '../api/types';
import { label } from '../lib/enums';
import { formatDate } from '../lib/format';
import { Empty } from './feedback';
import { StatusBadge } from './StatusBadge';

/** The applications list table. Rows link to the detail page. */
export function ApplicationTable({ rows }: { rows: ApplicationSummaryResponse[] }) {
  if (rows.length === 0) return <Empty>No applications match.</Empty>;

  return (
    <div style={{ overflowX: 'auto' }}>
      <table>
        <thead>
          <tr>
            <th>Company</th>
            <th>Role</th>
            <th>Status</th>
            <th>Current stage</th>
            <th>Applied</th>
            <th>Follow-up</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((a) => (
            <tr key={a.id}>
              <td><Link to={`/applications/${a.id}`}>{a.companyName}</Link></td>
              <td>{a.role}</td>
              <td><StatusBadge status={a.status} /></td>
              <td>{label(a.currentStageType)}</td>
              <td className="nowrap">{formatDate(a.appliedDate)}</td>
              <td className="nowrap">{formatDate(a.followUpDate)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
