import React, { useState } from "react";
import UserService from "../service/UserService";
import "../styles/ChangePasswordModal.css";

function ChangePasswordModal({ show, onClose }) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmationPassword, setConfirmationPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

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

    setLoading(true);

    try {
      await UserService.changePassword({
        currentPassword,
        newPassword,
        confirmationPassword,
      });

      setSuccess("Password updated successfully!");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmationPassword("");

      setTimeout(() => {
        setSuccess("");
        onClose();
      }, 1800);
    } catch (err) {
      console.error("Change password error:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "Failed to update password. Please check your current password."
      );
    } finally {
      setLoading(false);
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
            <input
              type="password"
              className="form-control"
              placeholder="••••••••"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="form-label text-white small fw-semibold">
              New Password
            </label>
            <input
              type="password"
              className="form-control"
              placeholder="••••••••"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="form-label text-white small fw-semibold">
              Confirm New Password
            </label>
            <input
              type="password"
              className="form-control"
              placeholder="••••••••"
              value={confirmationPassword}
              onChange={(e) => setConfirmationPassword(e.target.value)}
              required
            />
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
              disabled={loading}
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
