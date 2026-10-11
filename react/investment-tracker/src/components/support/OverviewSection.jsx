import React from "react";
import { formatDate } from "./supportFormat";

export default function OverviewSection({ overview }) {
  if (!overview) return <div className="admin-empty">Loading overview…</div>;
  const cards = [
    ["Users", overview.totalUsers, `${overview.enabledUsers} activated`],
    ["Pending activation", overview.pendingActivationUsers, "Email confirmation required"],
    ["Locked accounts", overview.lockedUsers, "Currently blocked"],
    ["Active sessions", overview.activeSessions, "Valid sessions, not online users"],
    ["Portfolio records", overview.portfolioRecords, "Across all accounts"],
    ["Tracked items", overview.trackedItems, "PriceTracker entries"],
    ["Catalogue items", overview.catalogueItems, "Available CS items"],
    ["Price snapshots", overview.priceSnapshots, "Recorded market observations"],
  ];
  return (
    <>
      <div className="admin-section-heading">
        <div>
          <h2>System overview</h2>
          <p>Operational totals without exposing passwords, tokens or other secrets.</p>
        </div>
      </div>
      <div className="admin-stat-grid">
        {cards.map(([label, value, description]) => (
          <article className="admin-stat-card" key={label}>
            <span>{label}</span>
            <strong>{value ?? 0}</strong>
            <small>{description}</small>
          </article>
        ))}
      </div>
      <div className="admin-two-column">
        <JobCard title="Price update" job={overview.priceUpdate} />
        <JobCard title="Catalogue synchronization" job={overview.catalogueSync} />
      </div>
    </>
  );
}

function JobCard({ title, job }) {
  return (
    <article className="admin-panel-card">
      <h3>{title}</h3>
      <p>Last completed: <strong>{formatDate(job?.completedAt)}</strong></p>
    </article>
  );
}
