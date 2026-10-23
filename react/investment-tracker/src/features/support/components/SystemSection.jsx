import SupportPanelApi from "../SupportPanelApi";
import { StatusBadge } from "./SupportControls";

export default function SystemSection({ overview, reason, setReason, runSystemAction }) {
  return (
    <>
      <div className="admin-section-heading"><div><h2>Catalogue &amp; prices</h2><p>Run controlled maintenance tasks and monitor their current state.</p></div></div>
      <div className="admin-panel-card admin-system-reason">
        <label className="admin-field-label" htmlFor="system-reason">Reason for manual operation</label>
        <textarea id="system-reason" rows="3" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Example: Verify catalogue synchronization after reported missing item." />
        <small className="admin-field-help">The operation and reason are stored in the audit log.</small>
      </div>
      <div className="admin-two-column">
        <article className="admin-panel-card">
          <div className="admin-card-title-row"><h3>CS item catalogue</h3><StatusBadge tone={overview?.catalogueSync?.status === "RUNNING" ? "warning" : "info"}>{overview?.catalogueSync?.status || "UNKNOWN"}</StatusBadge></div>
          <p>{overview?.catalogueItems ?? 0} catalogue items are currently stored.</p>
          <p className="admin-muted">{overview?.catalogueSync?.message}</p>
          <button className="admin-primary-btn" type="button" disabled={overview?.catalogueSync?.status === "RUNNING"} onClick={() => runSystemAction(SupportPanelApi.startCatalogueSync)}>Synchronize catalogue</button>
        </article>
        <article className="admin-panel-card">
          <div className="admin-card-title-row"><h3>Tracked prices</h3><StatusBadge tone={overview?.priceUpdate?.status === "RUNNING" ? "warning" : "info"}>{overview?.priceUpdate?.status || "UNKNOWN"}</StatusBadge></div>
          <p>{overview?.trackedItems ?? 0} tracked items use the scheduled price update.</p>
          <p className="admin-muted">{overview?.priceUpdate?.message}</p>
          <button className="admin-primary-btn" type="button" disabled={overview?.priceUpdate?.status === "RUNNING"} onClick={() => runSystemAction(SupportPanelApi.startPriceUpdate)}>Update all tracked prices</button>
        </article>
      </div>
    </>
  );
}
