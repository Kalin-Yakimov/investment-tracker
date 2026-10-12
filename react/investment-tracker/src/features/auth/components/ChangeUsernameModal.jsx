import { useState, useEffect } from "react";
import AuthService from "../AuthService";
import useModalRequestScope from "../../../shared/hooks/useModalRequestScope";
import { isRequestCanceled } from "../../../shared/utils/RequestScope";

function ChangeUsernameModal({ show, onClose, currentUsername, onSuccess }) {
  const scope = useModalRequestScope(show);
  const [newUsername, setNewUsername] = useState("");
  const loading = scope.isPending("submit");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    if (currentUsername) {
      setNewUsername(currentUsername);
    }
  }, [currentUsername, show]);

  useEffect(() => {
    if (!show) { setSuccess(""); setError(""); }
    if (!show || !success) return undefined;
    const timer = setTimeout(() => { setSuccess(""); onClose(); }, 1500);
    return () => clearTimeout(timer);
  }, [show, success, onClose]);

  if (!show) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (newUsername.trim().length < 3) {
      setError("Username must be at least 3 characters long.");
      return;
    }

    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    try {
      await AuthService.changeUsername(newUsername.trim());
      if (!request.isCurrent()) return;
      setSuccess("Username updated successfully!");
      if (onSuccess) onSuccess();

    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      console.error("Change username error:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "Failed to update username. Please try another username."
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
              disabled={loading}
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
