import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import AdminService from "../service/AdminService";
import UserService from "../service/UserService";
import useRequestScope from "../hooks/useRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import { SUPPORT_JOB_POLL_MS, SUPPORT_TICKET_POLL_MS } from "../config/uiConfig";
import "../styles/AdminPanel.css";

const EMPTY_PAGE = { content: [], number: 0, totalPages: 0, totalElements: 0 };

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatMoney(value) {
  if (value === null || value === undefined) return "—";
  return new Intl.NumberFormat("en-IE", {
    style: "currency",
    currency: "EUR",
  }).format(Number(value));
}

function errorMessage(error) {
  return (
    error?.response?.data?.error ||
    error?.response?.data?.message ||
    error?.error ||
    error?.message ||
    "The operation could not be completed"
  );
}

function readableEventType(value) {
  if (!value) return "Recorded action";
  const text = value.replaceAll("_", " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function StatusBadge({ children, tone = "neutral" }) {
  return <span className={`admin-status-badge ${tone}`}>{children}</span>;
}

function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="admin-pagination">
      <button
        type="button"
        disabled={page.number <= 0}
        onClick={() => onChange(page.number - 1)}
      >
        Previous
      </button>
      <span>
        Page {page.number + 1} of {page.totalPages}
      </span>
      <button
        type="button"
        disabled={page.number + 1 >= page.totalPages}
        onClick={() => onChange(page.number + 1)}
      >
        Next
      </button>
    </div>
  );
}

export default function AdminPanel() {
  const scope = useRequestScope();
  const currentUser = UserService.getCurrentUser();
  const isAdmin = UserService.hasAuthority("ADMIN");
  const [section, setSection] = useState("overview");
  const [overview, setOverview] = useState(null);
  const [users, setUsers] = useState(EMPTY_PAGE);
  const [search, setSearch] = useState("");
  const [userPage, setUserPage] = useState(0);
  const [selectedUser, setSelectedUser] = useState(null);
  const [userDetails, setUserDetails] = useState(null);
  const [supportReason, setSupportReason] = useState("");
  const [userTab, setUserTab] = useState("profile");
  const userTabRef = useRef("profile");
  const [portfolio, setPortfolio] = useState(EMPTY_PAGE);
  const [trackedItems, setTrackedItems] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [activity, setActivity] = useState(EMPTY_PAGE);
  const [audit, setAudit] = useState(EMPTY_PAGE);
  const [tickets, setTickets] = useState(EMPTY_PAGE);
  const [ticketStatus, setTicketStatus] = useState("");
  const [newTicketCount, setNewTicketCount] = useState(0);
  const [systemReason, setSystemReason] = useState("");
  const loading = ["users", "audit", "tickets", "details", "tab", "refresh-selected", "user-action", "system-action", "ticket-action"]
    .some((key) => scope.isPending(key));
  const [notice, setNotice] = useState(null);

  const showNotice = useCallback((type, text) => {
    setNotice({ type, text });
  }, []);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(() => setNotice(null), 5000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const read = useCallback(async (key, operation, apply, { replace = true, silent = false } = {}) => {
    const request = scope.begin(key, { replace });
    if (!request) return;
    try {
      const result = await operation({ signal: request.signal });
      if (request.isCurrent()) apply(result);
    } catch (error) {
      if (request.isCurrent() && !silent && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  }, [scope, showNotice]);

  const loadOverview = useCallback((replace = false) =>
    read("overview", (config) => AdminService.getOverview(config), setOverview, { replace }),
  [read]);

  const loadUsers = useCallback(
    (page = 0, query = "") =>
      read("users", (config) => AdminService.searchUsers(query, page, 20, config), (result) => {
        setUsers(result);
        setUserPage(page);
      }),
    [read],
  );

  const loadAudit = useCallback(
    (page = 0) => {
      if (!isAdmin) return;
      return read("audit", (config) => AdminService.getAudit(page, 25, config), setAudit);
    },
    [isAdmin, read],
  );

  const loadTickets = useCallback(
    (page = 0, status = "") =>
      read("tickets", (config) => AdminService.getTickets(status, page, 15, config), setTickets),
    [read],
  );

  const loadNewTicketCount = useCallback(() =>
    read("ticket-count", (config) => AdminService.getNewTicketCount(config), setNewTicketCount, { replace: false, silent: true }),
  [read]);

  useEffect(() => {
    loadUsers(0, "");
    loadNewTicketCount();
    return () => { scope.cancel("users"); scope.cancel("ticket-count"); };
  }, [loadUsers, loadNewTicketCount, scope]);

  useEffect(() => {
    const intervalId = window.setInterval(loadNewTicketCount, SUPPORT_TICKET_POLL_MS);
    return () => window.clearInterval(intervalId);
  }, [loadNewTicketCount]);

  useEffect(() => {
    if (section === "audit") loadAudit(0);
    if (section === "tickets") loadTickets(0, ticketStatus);
    if (section === "overview" || section === "system") loadOverview();
    return () => {
      scope.cancel("audit");
      scope.cancel("tickets");
      scope.cancel("ticket-action");
    };
  }, [section, ticketStatus, loadAudit, loadOverview, loadTickets, scope]);

  useEffect(() => {
    const hasRunningJob =
      overview?.catalogueSync?.status === "RUNNING" ||
      overview?.priceUpdate?.status === "RUNNING";
    if (!hasRunningJob) return undefined;

    const intervalId = window.setInterval(loadOverview, SUPPORT_JOB_POLL_MS);
    return () => window.clearInterval(intervalId);
  }, [overview?.catalogueSync?.status, overview?.priceUpdate?.status, loadOverview]);

  const selectUser = (user) => {
    ["details", "tab", "refresh-selected", "user-action"].forEach(scope.cancel);
    setSelectedUser(user);
    setUserDetails(null);
    setSupportReason("");
    setUserTab("profile");
    userTabRef.current = "profile";
    setPortfolio(EMPTY_PAGE);
    setTrackedItems([]);
    setSessions([]);
    setActivity(EMPTY_PAGE);
  };

  const openSupportView = async () => {
    if (!selectedUser || supportReason.trim().length < 5) {
      showNotice("error", "Enter a clear support reason of at least 5 characters.");
      return;
    }
    scope.cancel("tab");
    await read("details", (config) => AdminService.getUserDetails(selectedUser.id, supportReason.trim(), config), (details) => {
      setUserDetails(details);
      setUserTab("profile");
      userTabRef.current = "profile";
    });
  };

  const loadUserTab = async (tab, page = 0) => {
    if (!userDetails || !selectedUser) return;
    setUserTab(tab);
    userTabRef.current = tab;
    scope.cancel("tab");
    if (tab === "portfolio") {
      await read("tab", (config) => AdminService.getPortfolio(selectedUser.id, supportReason, page, 20, config), setPortfolio);
    } else if (tab === "tracked") {
      await read("tab", (config) => AdminService.getTrackedItems(selectedUser.id, supportReason, config), setTrackedItems);
    } else if (tab === "sessions") {
      await read("tab", (config) => AdminService.getSessions(selectedUser.id, supportReason, config), setSessions);
    } else if (tab === "activity") {
      await read("tab", (config) => AdminService.getActivity(selectedUser.id, supportReason, page, 30, config), setActivity);
    }
  };

  const refreshSelectedUser = async () => {
    if (!selectedUser || !supportReason) return;
    await read("refresh-selected", (config) => AdminService.getUserDetails(selectedUser.id, supportReason, config), (details) => {
      setUserDetails(details);
      setSelectedUser(details.user);
    });
  };

  const performUserAction = async (action, confirmation) => {
    if (!selectedUser || !supportReason) return;
    if (scope.isPending("user-action")) return;
    if (confirmation && !window.confirm(confirmation)) return;
    const request = scope.begin("user-action", { replace: false, abortable: false });
    if (!request) return;
    try {
      const result = await action();
      if (!request.isCurrent()) return;
      showNotice("success", result.message);
      await refreshSelectedUser();
      if (!request.isCurrent()) return;
      await Promise.all([loadUsers(userPage, search), loadOverview()]);
      if (!request.isCurrent()) return;
      if (userTabRef.current === "sessions") await loadUserTab("sessions");
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const runSystemAction = async (action) => {
    if (systemReason.trim().length < 5) {
      showNotice("error", "Enter a reason of at least 5 characters.");
      return;
    }
    const request = scope.begin("system-action", { replace: false, abortable: false });
    if (!request) return;
    try {
      const result = await action(systemReason.trim());
      if (!request.isCurrent()) return;
      showNotice("success", result.message);
      setSystemReason("");
      await loadOverview(true);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const changeTicketStatus = async (ticketId, status) => {
    const request = scope.begin("ticket-action", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tickets");
    try {
      await AdminService.updateTicketStatus(ticketId, status);
      if (!request.isCurrent()) return;
      await Promise.all([
        loadTickets(tickets.number, ticketStatus),
        loadNewTicketCount(),
      ]);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const removeTicket = async (ticketId) => {
    if (scope.isPending("ticket-action")) return;
    if (!window.confirm("Remove this contact request permanently?")) return;
    const request = scope.begin("ticket-action", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tickets");
    try {
      const result = await AdminService.removeTicket(ticketId);
      if (!request.isCurrent()) return;
      showNotice("success", result.message);
      const targetPage = tickets.content.length === 1 && tickets.number > 0
        ? tickets.number - 1
        : tickets.number;
      await Promise.all([
        loadTickets(targetPage, ticketStatus),
        loadNewTicketCount(),
      ]);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const navigation = useMemo(
    () => [
      ["overview", "Overview"],
      ["tickets", "Contact requests"],
      ["users", "User support"],
      ["system", "Catalogue & prices"],
      ...(isAdmin ? [["audit", "Audit log"]] : []),
    ],
    [isAdmin],
  );

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

function OverviewSection({ overview }) {
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

function TicketsSection({ tickets, status, setStatus, loadPage, changeStatus, removeTicket }) {
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

function UsersSection(props) {
  return (
    <>
      <div className="admin-section-heading">
        <div>
          <h2>User support</h2>
          <p>Find an account, state the support reason and open a read-only view.</p>
        </div>
      </div>
      <form
        className="admin-search-row"
        onSubmit={(event) => {
          event.preventDefault();
          props.loadUsers(0, props.search);
        }}
      >
        <input
          value={props.search}
          onChange={(event) => props.setSearch(event.target.value)}
          placeholder="Search by email or username"
        />
        <button type="submit" className="admin-primary-btn">Search</button>
      </form>

      <div className="admin-users-layout">
        <div className="admin-table-card">
          <div className="admin-table-scroll">
            <table className="admin-table admin-user-table">
              <thead><tr><th>User</th><th>Status</th><th>Roles</th><th>Records</th></tr></thead>
              <tbody>
                {props.users.content.map((user) => (
                  <tr
                    key={user.id}
                    className={props.selectedUser?.id === user.id ? "selected" : ""}
                    onClick={() => props.selectUser(user)}
                  >
                    <td><strong>{user.username}</strong><small>{user.email}</small></td>
                    <td>
                      <StatusBadge tone={user.locked ? "danger" : user.enabled ? "success" : "warning"}>
                        {user.locked ? "Locked" : user.enabled ? "Active" : "Pending"}
                      </StatusBadge>
                    </td>
                    <td><div className="admin-role-list">{user.roles.map((role) => <span key={role}>{role}</span>)}</div></td>
                    <td><small>{user.portfolioItems} portfolio · {user.trackedItems} tracked</small></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={props.users} onChange={props.changeUserPage} />
        </div>

        <div className="admin-support-pane">
          {!props.selectedUser && <div className="admin-empty">Select a user to begin.</div>}
          {props.selectedUser && !props.userDetails && (
            <div className="admin-panel-card">
              <h3>Open support view</h3>
              <p><strong>{props.selectedUser.username}</strong><br />{props.selectedUser.email}</p>
              <label className="admin-field-label" htmlFor="support-reason">Reason for access</label>
              <textarea
                id="support-reason"
                rows="4"
                value={props.supportReason}
                onChange={(event) => props.setSupportReason(event.target.value)}
                placeholder="Example: User reported that a tracked price is not updating."
              />
              <small className="admin-field-help">This reason is stored in the audit log.</small>
              <button type="button" className="admin-primary-btn w-100 mt-3" onClick={props.openSupportView}>
                Open read-only support view
              </button>
            </div>
          )}
          {props.userDetails && <UserSupportView {...props} />}
        </div>
      </div>
    </>
  );
}

function UserSupportView(props) {
  const user = props.userDetails.user;
  const isSupport = user.roles.includes("SUPPORT");
  const isTargetAdmin = user.roles.includes("ADMIN");
  const isSelf = props.currentUser?.id === user.id;
  const tabs = [
    ["profile", "Profile"],
    ["portfolio", "Portfolio"],
    ["tracked", "PriceTracker"],
    ["sessions", "Sessions"],
    ["activity", "Activity"],
  ];

  return (
    <div className="admin-panel-card support-view-card">
      <div className="support-view-banner">Read-only support view · Access is being audited</div>
      <div className="admin-card-title-row">
        <div><h3>{user.username}</h3><p>{user.email}</p></div>
        <StatusBadge tone={user.locked ? "danger" : user.enabled ? "success" : "warning"}>
          {user.locked ? "Locked" : user.enabled ? "Active" : "Pending activation"}
        </StatusBadge>
      </div>
      <div className="admin-detail-tabs">
        {tabs.map(([key, label]) => (
          <button type="button" key={key} className={props.userTab === key ? "active" : ""} onClick={() => props.loadUserTab(key)}>
            {label}
          </button>
        ))}
      </div>

      <div className="admin-support-tab-content">
        {props.userTab === "profile" && (
          <div className="admin-profile-content">
            <dl className="admin-compact-list">
              <div><dt>Registered</dt><dd>{formatDate(user.createdDate)}</dd></div>
              <div><dt>Last session activity</dt><dd>{formatDate(props.userDetails.lastSessionActivity)}</dd></div>
              <div><dt>Active sessions</dt><dd>{user.activeSessions}</dd></div>
              <div><dt>Portfolio records</dt><dd>{user.portfolioItems}</dd></div>
              <div><dt>Tracked items</dt><dd>{user.trackedItems}</dd></div>
              <div><dt>Roles</dt><dd>{user.roles.join(", ")}</dd></div>
            </dl>
            <div className="admin-action-grid">
              <button
                type="button"
                onClick={() => props.performUserAction(
                  () => AdminService.revokeSessions(user.id, props.supportReason),
                  `Sign out ${user.email} from every device?`,
                )}
              >Revoke all sessions</button>
              {!user.enabled && (
                <button type="button" onClick={() => props.performUserAction(
                  () => AdminService.resendActivation(user.id, props.supportReason),
                )}>Resend activation</button>
              )}
              {props.isAdmin && !isTargetAdmin && (
                <button type="button" onClick={() => props.performUserAction(
                  () => AdminService.setSupportRole(user.id, !isSupport, props.supportReason),
                  `${isSupport ? "Remove" : "Grant"} SUPPORT role for ${user.email}? Existing sessions will be revoked.`,
                )}>{isSupport ? "Remove SUPPORT role" : "Grant SUPPORT role"}</button>
              )}
              {props.isAdmin && !isTargetAdmin && !isSelf && (
                <button
                  type="button"
                  className={user.locked ? "safe" : "danger"}
                  onClick={() => props.performUserAction(
                    () => AdminService.setAccountLocked(user.id, !user.locked, props.supportReason),
                    `${user.locked ? "Unlock" : "Lock"} ${user.email}?`,
                  )}
                >{user.locked ? "Unlock account" : "Lock account"}</button>
              )}
            </div>
          </div>
        )}

        {props.userTab === "portfolio" && <PortfolioTable page={props.portfolio} loadPage={(page) => props.loadUserTab("portfolio", page)} />}
        {props.userTab === "tracked" && <TrackedTable items={props.trackedItems} />}
        {props.userTab === "sessions" && <SessionsTable sessions={props.sessions} />}
        {props.userTab === "activity" && <ActivityTable page={props.activity} loadPage={(page) => props.loadUserTab("activity", page)} />}
      </div>
    </div>
  );
}

function PortfolioTable({ page, loadPage }) {
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

function TrackedTable({ items }) {
  return (
    <div className="admin-table-scroll compact">
      <table className="admin-table"><thead><tr><th>Item</th><th>Current price</th><th>Quantity</th><th>Dashboard</th></tr></thead>
        <tbody>{items.map((item) => <tr key={item.id}><td><strong>{item.itemName}</strong><small>{item.marketHashName}</small></td><td>{formatMoney(item.currentPrice)}</td><td>{item.quantity}</td><td>{item.showInDashboard ? "Yes" : "No"}</td></tr>)}</tbody>
      </table>
      {items.length === 0 && <div className="admin-empty small">No tracked items.</div>}
    </div>
  );
}

function SessionsTable({ sessions }) {
  return (
    <div className="admin-session-list">
      {sessions.map((session) => (
        <article key={session.id}>
          <div><strong>{session.userAgent || "Unknown browser"}</strong><small>Last used {formatDate(session.lastUsedAt)}</small></div>
          <div><span>{session.remembered ? "Remembered" : "Standard"}</span><small>Expires {formatDate(session.expiresAt)}</small>{session.ipAddress && <small>IP {session.ipAddress}</small>}</div>
        </article>
      ))}
      {sessions.length === 0 && <div className="admin-empty small">No active sessions.</div>}
    </div>
  );
}

function ActivityTable({ page, loadPage }) {
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

function SystemSection({ overview, reason, setReason, runSystemAction }) {
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
          <button className="admin-primary-btn" type="button" disabled={overview?.catalogueSync?.status === "RUNNING"} onClick={() => runSystemAction(AdminService.startCatalogueSync)}>Synchronize catalogue</button>
        </article>
        <article className="admin-panel-card">
          <div className="admin-card-title-row"><h3>Tracked prices</h3><StatusBadge tone={overview?.priceUpdate?.status === "RUNNING" ? "warning" : "info"}>{overview?.priceUpdate?.status || "UNKNOWN"}</StatusBadge></div>
          <p>{overview?.trackedItems ?? 0} tracked items use the scheduled price update.</p>
          <p className="admin-muted">{overview?.priceUpdate?.message}</p>
          <button className="admin-primary-btn" type="button" disabled={overview?.priceUpdate?.status === "RUNNING"} onClick={() => runSystemAction(AdminService.startPriceUpdate)}>Update all tracked prices</button>
        </article>
      </div>
    </>
  );
}

function AuditSection({ audit, loadPage }) {
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
