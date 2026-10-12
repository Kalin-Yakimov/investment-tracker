import { Pagination, StatusBadge } from "./SupportControls";
import UserSupportView from "./UserSupportView";

export default function UsersSection(props) {
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
