import React, { useEffect, useState } from "react";
import UserService from "../service/UserService";
import useModalRequestScope from "../hooks/useModalRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import "../styles/ChangePasswordModal.css";

const EyeOpenIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
    <circle cx="12" cy="12" r="3"></circle>
  </svg>
);

const EyeOffIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
    <line x1="1" y1="1" x2="23" y2="23"></line>
  </svg>
);

function ChangePasswordModal({ show, onClose }) {
  // Success intentionally ends the session; guard that transition in UserService.
  const scope = useModalRequestScope(show, { guardSession: false });
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmationPassword, setConfirmationPassword] = useState("");
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const loading = scope.isPending("submit");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    if (!show) {
      setCurrentPassword(""); setNewPassword(""); setConfirmationPassword("");
      setError(""); setSuccess("");
    }
    if (!show || !success) return undefined;
    const timer = setTimeout(() => {
      setSuccess("");
      onClose();
      window.location.href = "/login?passwordChanged=true";
    }, 1800);
    return () => clearTimeout(timer);
  }, [show, success, onClose]);

  if (!show) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (newPassword.length < 6) {
      setError("New password must be at least 6 characters long.");
      return;
    }

    if (newPassword !== confirmationPassword) {
      setError("New passwords do not match.");
      return;
    }

    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    try {
      await UserService.changePassword({
        currentPassword,
        newPassword,
        confirmationPassword,
      });
      if (!request.isCurrent()) return;
      setSuccess("Password updated successfully!");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmationPassword("");

    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      console.error("Change password error:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "Failed to update password. Please check your current password."
      );
    } finally {
      request.finish();
    }
  };

  return (
    <div className="change-pass-modal-backdrop" onClick={onClose}>
      <div
        className="change-pass-modal-card card shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="d-flex align-items-center justify-content-between mb-3">
          <div className="d-flex align-items-center gap-2">
            <span style={{ fontSize: "1.5rem" }}>🔒</span>
            <h4 className="text-white mb-0 fw-bold">Change Password</h4>
          </div>
          <button
            type="button"
            className="btn-close btn-close-white"
            onClick={onClose}
            aria-label="Close"
          ></button>
        </div>

        {error && <div className="alert alert-danger py-2 mb-3 small">{error}</div>}
        {success && <div className="alert alert-success py-2 mb-3 small">{success}</div>}

        <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
          <div>
            <label className="form-label text-white small fw-semibold">
              Current Password
            </label>
            <div className="input-group password-input-group">
              <input
                type={showCurrent ? "text" : "password"}
                className="form-control"
                placeholder="••••••••"
                value={currentPassword}
                disabled={loading || Boolean(success)}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowCurrent(!showCurrent)}
                title={showCurrent ? "Hide Password" : "Show Password"}
              >
                {showCurrent ? <EyeOffIcon /> : <EyeOpenIcon />}
              </button>
            </div>
          </div>

          <div>
            <label className="form-label text-white small fw-semibold">
              New Password
            </label>
            <div className="input-group password-input-group">
              <input
                type={showNew ? "text" : "password"}
                className="form-control"
                placeholder="••••••••"
                value={newPassword}
                disabled={loading || Boolean(success)}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowNew(!showNew)}
                title={showNew ? "Hide Password" : "Show Password"}
              >
                {showNew ? <EyeOffIcon /> : <EyeOpenIcon />}
              </button>
            </div>
          </div>

          <div>
            <label className="form-label text-white small fw-semibold">
              Confirm New Password
            </label>
            <div className="input-group password-input-group">
              <input
                type={showConfirm ? "text" : "password"}
                className="form-control"
                placeholder="••••••••"
                value={confirmationPassword}
                disabled={loading || Boolean(success)}
                onChange={(e) => setConfirmationPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowConfirm(!showConfirm)}
                title={showConfirm ? "Hide Password" : "Show Password"}
              >
                {showConfirm ? <EyeOffIcon /> : <EyeOpenIcon />}
              </button>
            </div>
          </div>

          <div className="d-flex justify-content-end gap-2 mt-3">
            <button
              type="button"
              className="btn btn-secondary px-3"
              onClick={onClose}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary px-4"
              disabled={loading || Boolean(success)}
            >
              {loading ? "Updating..." : "Update Password"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ChangePasswordModal;
