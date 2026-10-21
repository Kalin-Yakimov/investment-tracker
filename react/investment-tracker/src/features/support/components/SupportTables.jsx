import { formatDate, formatMoney } from "../utils/supportFormat";
import { Pagination, StatusBadge } from "./SupportControls";

export function PortfolioTable({ page, loadPage }) {
  return (
    <div className="admin-table-scroll compact">
      <table className="admin-table"><thead><tr><th>Item</th><th>Status</th><th>Date</th><th>Quantity</th><th>Unit price</th></tr></thead>
        <tbody>{page.content.map((item) => <tr key={item.id}><td><strong>{item.itemName}</strong></td><td>{item.itemStatus}</td><td>{item.date || "—"}</td><td>{item.quantity}</td><td>{formatMoney(item.price)}</td></tr>)}</tbody>
      </table>
      {page.content.length === 0 && <div className="admin-empty small">No portfolio records.</div>}
      <Pagination page={page} onChange={loadPage} />
    </div>
  );
}

export function TrackedTable({ page, loadPage }) {
  return (
    <div className="admin-table-scroll compact">
      <table className="admin-table"><thead><tr><th>Item</th><th>Current price</th><th>Quantity</th><th>Dashboard</th></tr></thead>
        <tbody>{page.content.map((item) => <tr key={item.id}><td><strong>{item.itemName}</strong><small>{item.marketHashName}</small></td><td>{formatMoney(item.currentPrice)}</td><td>{item.quantity}</td><td>{item.showInDashboard ? "Yes" : "No"}</td></tr>)}</tbody>
      </table>
      {page.content.length === 0 && <div className="admin-empty small">No tracked items.</div>}
      <Pagination page={page} onChange={loadPage} />
    </div>
  );
}

export function SessionsTable({ page, loadPage }) {
  return (
    <div className="admin-session-list">
      {page.content.map((session) => (
        <article key={session.id}>
          <div><strong>{session.userAgent || "Unknown browser"}</strong><small>Last used {formatDate(session.lastUsedAt)}</small></div>
          <div><span>{session.remembered ? "Remembered" : "Standard"}</span><small>Expires {formatDate(session.expiresAt)}</small>{session.ipAddress && <small>IP {session.ipAddress}</small>}</div>
        </article>
      ))}
      {page.content.length === 0 && <div className="admin-empty small">No active sessions.</div>}
      <Pagination page={page} onChange={loadPage} />
    </div>
  );
}

export function ActivityTable({ page, loadPage }) {
  return (
    <div className="admin-activity-list">
      {page.content.map((event) => (
        <article key={event.id}>
          <div><strong>{event.eventType.replaceAll("_", " ")}</strong><StatusBadge>{event.category}</StatusBadge></div>
          <p>{event.details || "No additional details"}</p>
          {event.reason && <blockquote>{event.reason}</blockquote>}
          <small>{formatDate(event.createdAt)} · {event.actor}</small>
        </article>
      ))}
      {page.content.length === 0 && <div className="admin-empty small">No activity has been recorded.</div>}
      <Pagination page={page} onChange={loadPage} />
    </div>
  );
}
