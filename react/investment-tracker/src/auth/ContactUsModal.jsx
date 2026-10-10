import React, { useEffect, useState } from "react";
import UserService from "../service/UserService";
import useModalRequestScope from "../hooks/useModalRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import "../styles/ContactUsModal.css";

function getErrorMessage(error) {
  const validationErrors = error?.response?.data?.validationErrors || error?.validationErrors;
  if (validationErrors && validationErrors.length > 0) {
    return Array.from(validationErrors).join(" ");
  }
  return (
    error?.response?.data?.error ||
    error?.error ||
    error?.message ||
    "Your message could not be sent. Please try again."
  );
}

function ContactUsModal({ show, onClose, defaultEmail }) {
  const scope = useModalRequestScope(show);
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const loading = scope.isPending("submit");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (show) {
      setEmail(defaultEmail || "");
      setMessage("");
      setError("");
      setSuccess(false);
    }
  }, [show, defaultEmail]);

  if (!show) return null;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");

    if (message.trim().length < 10) {
      setError("Please describe the problem in at least 10 characters.");
      return;
    }

    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    try {
      await UserService.createContactTicket(email.trim(), message.trim());
      if (!request.isCurrent()) return;
      setSuccess(true);
      setMessage("");
    } catch (requestError) {
      if (request.isCurrent() && !isRequestCanceled(requestError)) setError(getErrorMessage(requestError));
    } finally {
      request.finish();
    }
  };

  return (
    <div className="change-pass-modal-backdrop" onClick={onClose}>
      <div className="change-pass-modal-card contact-us-modal-card card shadow-lg" onClick={(event) => event.stopPropagation()}>
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h4 className="text-white mb-0 fw-bold">Contact Us</h4>
          <button
            type="button"
            className="btn-close btn-close-white"
            onClick={onClose}
            aria-label="Close"
          />
        </div>

        <p className="contact-us-intro">
          Describe the problem or question you have. If we need additional information,
          we will contact you at the email address you provide below.
        </p>

        {error && <div className="alert alert-danger py-2 mb-3 small">{error}</div>}
        {success && (
          <div className="alert alert-success py-2 mb-3 small">
            Your message was sent successfully. If necessary, we will contact you by email.
          </div>
        )}

        {!success && (
          <form onSubmit={handleSubmit} className="d-flex flex-column gap-3">
            <div>
              <label className="form-label text-white small fw-semibold" htmlFor="contact-email">
                Contact email
              </label>
              <input
                id="contact-email"
                disabled={loading}
                type="email"
                className="form-control"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                maxLength={255}
                autoComplete="email"
                required
              />
            </div>

            <div>
              <label className="form-label text-white small fw-semibold" htmlFor="contact-message">
                Message
              </label>
              <textarea
                id="contact-message"
                disabled={loading}
                className="form-control contact-us-message"
                rows="7"
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                maxLength={2000}
                placeholder="Describe what happened and what you need help with."
                required
              />
            </div>

            <div className="d-flex justify-content-end gap-2 mt-2">
              <button type="button" className="btn btn-secondary px-3" onClick={onClose}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary px-4" disabled={loading}>
                {loading ? "Sending..." : "Send Message"}
              </button>
            </div>
          </form>
        )}

        {success && (
          <div className="d-flex justify-content-end">
            <button type="button" className="btn btn-primary px-4" onClick={onClose}>
              Close
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default ContactUsModal;
