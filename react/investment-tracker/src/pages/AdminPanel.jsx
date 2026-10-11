import React from "react";
import useSupportPanel from "../hooks/useSupportPanel";
import { StatusBadge } from "../components/support/SupportControls";
import OverviewSection from "../components/support/OverviewSection";
import TicketsSection from "../components/support/TicketsSection";
import UsersSection from "../components/support/UsersSection";
import SystemSection from "../components/support/SystemSection";
import AuditSection from "../components/support/AuditSection";
import "../styles/AdminPanel.css";

export default function AdminPanel() {
  const { scope, isAdmin, section, setSection, overview, users, search, setSearch, selectedUser, selectUser, supportReason, setSupportReason, openSupportView, userDetails, userTab, loadUserTab, portfolio, trackedItems, sessions, activity, currentUser, performUserAction, loadUsers, tickets, ticketStatus, setTicketStatus, loadTickets, changeTicketStatus, removeTicket, systemReason, setSystemReason, runSystemAction, audit, loadAudit, navigation, notice, loading, newTicketCount } = useSupportPanel();
  return (
    <main className="admin-shell">
      <div className="admin-page-header">
        <div>
          <h1>Support Panel</h1>
        </div>
        <StatusBadge tone={isAdmin ? "danger" : "info"}>
          {isAdmin ? "ADMIN" : "SUPPORT"}
        </StatusBadge>
      </div>

      {notice && <div className={`admin-notice ${notice.type}`}>{notice.text}</div>}

      <div className="admin-layout">
        <aside className="admin-sidebar">
          {navigation.map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={section === key ? "active" : ""}
              onClick={() => {
                ["details", "tab", "refresh-selected", "user-action", "system-action"].forEach(scope.cancel);
                setSection(key);
              }}
            >
              <span>{label}</span>
              {key === "tickets" && newTicketCount > 0 && (
                <span
                  className="admin-nav-alert"
                  aria-label={`${newTicketCount} new contact request${newTicketCount === 1 ? "" : "s"}`}
                  title={`${newTicketCount} new contact request${newTicketCount === 1 ? "" : "s"}`}
                >
                  !
                </span>
              )}
            </button>
          ))}
          <div className="admin-sidebar-note">
            Detailed user access requires a support reason and is written to the audit log.
          </div>
        </aside>

        <section className="admin-content">
          {loading && <div className="admin-loading-bar" />}
          {section === "overview" && <OverviewSection overview={overview} />}
          {section === "tickets" && (
            <TicketsSection
              tickets={tickets}
              status={ticketStatus}
              setStatus={setTicketStatus}
              loadPage={(page) => loadTickets(page, ticketStatus)}
              changeStatus={changeTicketStatus}
              removeTicket={removeTicket}
            />
          )}
          {section === "users" && (
            <UsersSection
              users={users}
              search={search}
              setSearch={setSearch}
              loadUsers={loadUsers}
              selectedUser={selectedUser}
              selectUser={selectUser}
              supportReason={supportReason}
              setSupportReason={setSupportReason}
              openSupportView={openSupportView}
              userDetails={userDetails}
              userTab={userTab}
              loadUserTab={loadUserTab}
              portfolio={portfolio}
              trackedItems={trackedItems}
              sessions={sessions}
              activity={activity}
              isAdmin={isAdmin}
              currentUser={currentUser}
              performUserAction={performUserAction}
              changeUserPage={(page) => loadUsers(page, search)}
            />
          )}
          {section === "system" && (
            <SystemSection
              overview={overview}
              reason={systemReason}
              setReason={setSystemReason}
              runSystemAction={runSystemAction}
            />
          )}
          {section === "audit" && isAdmin && (
            <AuditSection audit={audit} loadPage={loadAudit} />
          )}
        </section>
      </div>
    </main>
  );
}
