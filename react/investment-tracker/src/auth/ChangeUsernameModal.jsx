import React, { useState, useEffect } from "react";
import UserService from "../service/UserService";

function ChangeUsernameModal({ show, onClose, currentUsername, onSuccess }) {
  const [newUsername, setNewUsername] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    if (currentUsername) {
      setNewUsername(currentUsername);
    }
  }, [currentUsername, show]);

  if (!show) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (newUsername.trim().length < 3) {
      setError("Username must be at least 3 characters long.");
      return;
    }

    setLoading(true);

    try {
      await UserService.changeUsername(newUsername.trim());
      setSuccess("Username updated successfully!");
      if (onSuccess) onSuccess();

      setTimeout(() => {
        setSuccess("");
        onClose();
      }, 1500);
    } catch (err) {
      console.error("Change username error:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "Failed to update username. Please try another username."
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
            <span style={{ fontSize: "1.5rem" }}>✏️</span>
            <h4 className="text-white mb-0 fw-bold">Change Username</h4>
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
              New Username
            </label>
            <input
              type="text"
              className="form-control"
              placeholder="e.g. Kalin99"
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
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
              {loading ? "Updating..." : "Save Username"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default ChangeUsernameModal;
