import React from "react";
import SupportPanelApi from "../SupportPanelApi";
import { formatDate } from "../utils/supportFormat";
import { StatusBadge } from "./SupportControls";
import { PortfolioTable, TrackedTable, SessionsTable, ActivityTable } from "./SupportTables";

export default function UserSupportView(props) {
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
                  () => SupportPanelApi.revokeSessions(user.id, props.supportReason),
                  `Sign out ${user.email} from every device?`,
                )}
              >Revoke all sessions</button>
              {!user.enabled && (
                <button type="button" onClick={() => props.performUserAction(
                  () => SupportPanelApi.resendActivation(user.id, props.supportReason),
                )}>Resend activation</button>
              )}
              {props.isAdmin && !isTargetAdmin && (
                <button type="button" onClick={() => props.performUserAction(
                  () => SupportPanelApi.setSupportRole(user.id, !isSupport, props.supportReason),
                  `${isSupport ? "Remove" : "Grant"} SUPPORT role for ${user.email}? Existing sessions will be revoked.`,
                )}>{isSupport ? "Remove SUPPORT role" : "Grant SUPPORT role"}</button>
              )}
              {props.isAdmin && !isTargetAdmin && !isSelf && (
                <button
                  type="button"
                  className={user.locked ? "safe" : "danger"}
                  onClick={() => props.performUserAction(
                    () => SupportPanelApi.setAccountLocked(user.id, !user.locked, props.supportReason),
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
