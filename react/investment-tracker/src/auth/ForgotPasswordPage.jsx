import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import "../styles/ForgotPasswordPage.css";

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

function ForgotPasswordPage() {
  const [step, setStep] = useState(1); // 1: Request Code, 2: Reset Password, 3: Success
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const navigate = useNavigate();

  const handleRequestCode = async (e) => {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);

    try {
      await UserService.forgotPassword(email);
      setMessage("Reset code sent! Please check your email for the 6-digit code.");
      setStep(2);
    } catch (err) {
      console.error("Forgot password request error:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "Failed to send reset code. Please check your email."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError("");
    setMessage("");

    if (newPassword.length < 6) {
      setError("Password must be at least 6 characters long.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      await UserService.resetPassword(email, code, newPassword);
      setMessage("Your password has been successfully reset!");
      setStep(3);
    } catch (err) {
      console.error("Reset password error:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "Failed to reset password. Please check your reset code."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container container">
      <div className="card auth-card text-center">
        <div className="mb-3">
          <span style={{ fontSize: "2.5rem" }}>🔑</span>
        </div>

        <h2 className="auth-header text-white">Reset Password</h2>
        <p className="auth-subtitle text-white-50">
          {step === 1 && "Enter your email to receive a password reset code"}
          {step === 2 && "Enter the 6-digit code and your new password"}
          {step === 3 && "Password updated successfully!"}
        </p>

        {error && <div className="alert alert-danger py-2 mb-3">{error}</div>}
        {message && <div className="alert alert-success py-2 mb-3">{message}</div>}

        {step === 1 && (
          <form onSubmit={handleRequestCode} className="text-start">
            <div className="mb-4">
              <label htmlFor="email" className="form-label text-white small fw-semibold">
                Email Address
              </label>
              <input
                type="email"
                id="email"
                className="form-control"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="d-grid gap-2">
              <button
                className="btn btn-primary py-2 fw-semibold"
                type="submit"
                disabled={loading}
              >
                {loading ? "Sending Code..." : "Send Reset Code"}
              </button>
              <Link to="/login" className="btn btn-secondary py-2 text-center">
                Back to Sign In
              </Link>
            </div>
          </form>
        )}

        {step === 2 && (
          <form onSubmit={handleResetPassword} className="text-start">
            <div className="mb-3">
              <label htmlFor="email" className="form-label text-white small fw-semibold">
                Email Address
              </label>
              <input
                type="email"
                id="email"
                className="form-control"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="mb-3">
              <label htmlFor="code" className="form-label text-white small fw-semibold">
                6-Digit Reset Code
              </label>
              <input
                type="text"
                id="code"
                className="form-control text-center font-mono fs-5 letter-spacing-2"
                placeholder="------"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
            </div>

            <div className="mb-3">
              <label htmlFor="newPassword" className="form-label text-white small fw-semibold">
                New Password
              </label>
              <div className="input-group password-input-group">
                <input
                  type={showNewPassword ? "text" : "password"}
                  id="newPassword"
                  className="form-control"
                  placeholder="••••••••"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  autoComplete="new-password"
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowNewPassword(!showNewPassword)}
                  title={showNewPassword ? "Hide Password" : "Show Password"}
                  aria-label={showNewPassword ? "Hide Password" : "Show Password"}
                >
                  {showNewPassword ? <EyeOffIcon /> : <EyeOpenIcon />}
                </button>
              </div>
            </div>

            <div className="mb-4">
              <label
                htmlFor="confirmPassword"
                className="form-label text-white small fw-semibold"
              >
                Confirm New Password
              </label>
              <div className="input-group password-input-group">
                <input
                  type={showConfirmPassword ? "text" : "password"}
                  id="confirmPassword"
                  className="form-control"
                  placeholder="••••••••"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  title={showConfirmPassword ? "Hide Password" : "Show Password"}
                  aria-label={showConfirmPassword ? "Hide Password" : "Show Password"}
                >
                  {showConfirmPassword ? <EyeOffIcon /> : <EyeOpenIcon />}
                </button>
              </div>
            </div>

            <div className="d-grid gap-2">
              <button
                className="btn btn-primary py-2 fw-semibold"
                type="submit"
                disabled={loading}
              >
                {loading ? "Resetting..." : "Reset Password"}
              </button>
              <button
                type="button"
                className="btn btn-secondary py-2"
                onClick={() => setStep(1)}
              >
                Change Email / Resend Code
              </button>
            </div>
          </form>
        )}

        {step === 3 && (
          <div className="d-grid gap-2 mt-3">
            <button
              className="btn btn-primary py-2 fw-semibold"
              onClick={() => navigate("/login")}
            >
              Proceed to Sign In
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default ForgotPasswordPage;
