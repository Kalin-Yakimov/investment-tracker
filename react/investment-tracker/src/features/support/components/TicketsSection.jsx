import React from "react";
import { formatDate } from "../utils/supportFormat";
import { Pagination, StatusBadge } from "./SupportControls";

function ticketStatusLabel(status) {
  if (status === "IN_PROGRESS") return "In progress";
  if (status === "RESOLVED") return "Resolved";
  return "New";
}

function ticketStatusTone(status) {
  if (status === "IN_PROGRESS") return "warning";
  if (status === "RESOLVED") return "success";
  return "info";
}

export default function TicketsSection({ tickets, status, setStatus, loadPage, changeStatus, removeTicket }) {
  return (
    <>
      <div className="admin-section-heading admin-ticket-heading">
        <div>
          <h2>Contact requests</h2>
          <p>Messages submitted by signed-in users through the Contact Us form.</p>
        </div>
        <select
          className="admin-ticket-filter"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          aria-label="Filter contact requests by status"
        >
          <option value="">All statuses</option>
          <option value="NEW">New</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="RESOLVED">Resolved</option>
        </select>
      </div>

      <div className="admin-ticket-list">
        {tickets.content.map((ticket) => (
          <article className="admin-ticket-card" key={ticket.id}>
            <div className="admin-ticket-card-header">
              <div>
                <h3>{ticket.accountUsername || "User account"}</h3>
                <span className="admin-ticket-account">
                  Account: {ticket.accountEmail}
                  {ticket.userId != null && ` · User #${ticket.userId}`}
                </span>
              </div>
              <StatusBadge tone={ticketStatusTone(ticket.status)}>
                {ticketStatusLabel(ticket.status)}
              </StatusBadge>
            </div>

            <p className="admin-ticket-message">{ticket.message}</p>

            <div className="admin-ticket-footer">
              <div className="admin-ticket-contact">
                <span>Reply by email</span>
                <a href={`mailto:${ticket.contactEmail}`}>{ticket.contactEmail}</a>
                <small>
                  Submitted {formatDate(ticket.createdAt)}
                  {ticket.updatedAt !== ticket.createdAt && ` · Updated ${formatDate(ticket.updatedAt)}`}
                </small>
              </div>

              <div className="admin-ticket-actions">
                <label htmlFor={`ticket-status-${ticket.id}`}>Status</label>
                <select
                  id={`ticket-status-${ticket.id}`}
                  value={ticket.status}
                  onChange={(event) => changeStatus(ticket.id, event.target.value)}
                >
                  <option value="NEW">New</option>
                  <option value="IN_PROGRESS">In progress</option>
                  <option value="RESOLVED">Resolved</option>
                </select>
                <button type="button" onClick={() => removeTicket(ticket.id)}>
                  Remove
                </button>
              </div>
            </div>
          </article>
        ))}
      </div>

      {tickets.content.length === 0 && (
        <div className="admin-empty">No contact requests match this status.</div>
      )}
      <Pagination page={tickets} onChange={loadPage} />
    </>
  );
}
