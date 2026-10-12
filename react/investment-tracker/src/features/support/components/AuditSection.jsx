import { formatDate, readableEventType } from "../utils/supportFormat";
import { Pagination } from "./SupportControls";

export default function AuditSection({ audit, loadPage }) {
  return (
    <>
      <div className="admin-section-heading"><div><h2>Audit log</h2></div></div>
      <div className="admin-table-card">
        <div className="admin-table-scroll">
          <table className="admin-table admin-audit-table">
            <thead>
              <tr><th>Time</th><th>Action</th><th>Affected user</th><th>Performed by</th><th>Reason</th></tr>
            </thead>
            <tbody>
              {audit.content.map((event) => (
                <tr key={event.id}>
                  <td>{formatDate(event.createdAt)}</td>
                  <td>
                    <strong>{readableEventType(event.eventType)}</strong>
                    <small>{event.category.replaceAll("_", " ")}</small>
                  </td>
                  <td>{event.target || "System"}</td>
                  <td>{event.actor}{event.ipAddress && <small>IP {event.ipAddress}</small>}</td>
                  <td>{event.reason || "—"}{event.details && <small>{event.details}</small>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {audit.content.length === 0 && <div className="admin-empty small">No administrative activity has been recorded.</div>}
        <Pagination page={audit} onChange={loadPage} />
      </div>
    </>
  );
}
