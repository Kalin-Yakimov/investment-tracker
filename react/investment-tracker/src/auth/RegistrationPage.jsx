import React, { useRef, useState } from "react";
import UserService from "../service/UserService";
import useRequestScope from "../hooks/useRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import { useNavigate, Link } from "react-router-dom";
import "../styles/RegistrationPage.css";
import { authErrorMessage, rememberActivationEmail } from "./authFeedback";

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

function RegistrationPage() {
  const scope = useRequestScope({ guardSession: false });
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  });

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const busy = useRef(false);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (busy.current) return;

    if (formData.username.trim().length < 3) {
      setError("Username must be at least 3 characters long.");
      return;
    }

    if (formData.password.length < 6) {
      setError("Password must be at least 6 characters long.");
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    busy.current = true;
    setLoading(true);
    const activationEmail = formData.email.trim();
    try {
      await UserService.register({
        username: formData.username.trim(),
        email: activationEmail,
        password: formData.password,
      });
      if (!request.isCurrent()) return;
      setFormData({
        username: "",
        email: "",
        password: "",
        confirmPassword: "",
      });
      setError("");
      rememberActivationEmail(activationEmail);
      navigate("/activate-account", { state: { email: activationEmail } });
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) setError(authErrorMessage(err, "An error occurred while registering. Please try again."));
    } finally {
      busy.current = false;
      if (request.isCurrent()) setLoading(false);
      request.finish();
    }
  };

  return (
    <div className="auth-container-reg container">
      <div className="card auth-card-reg">
        <div className="text-center mb-3">
          <h2 className="registration-header text-white">Create Account</h2>
        </div>

        {error && <p className="alert alert-danger py-2 text-center small">{error}</p>}

        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label htmlFor="username" className="form-label text-white small fw-semibold">
              Username
            </label>
            <input
              type="text"
              id="username"
              name="username"
              className="form-control"
              placeholder="e.g. Kalin99"
              value={formData.username}
              onChange={handleInputChange}
              autoComplete="username"
              required
            />
          </div>

          <div className="mb-3">
            <label htmlFor="email" className="form-label text-white small fw-semibold">
              Email Address
            </label>
            <input
              type="email"
              id="email"
              name="email"
              className="form-control"
              placeholder="name@example.com"
              value={formData.email}
              onChange={handleInputChange}
              autoComplete="email"
              required
            />
          </div>

          <div className="mb-3">
            <label htmlFor="password" className="form-label text-white small fw-semibold">
              Password
            </label>
            <div className="input-group password-input-group">
              <input
                type={showPassword ? "text" : "password"}
                id="password"
                name="password"
                className="form-control"
                placeholder="••••••••"
                value={formData.password}
                onChange={handleInputChange}
                autoComplete="new-password"
                required
              />
              <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setShowPassword(!showPassword)}
                title={showPassword ? "Hide Password" : "Show Password"}
                aria-label={showPassword ? "Hide Password" : "Show Password"}
              >
                {showPassword ? <EyeOffIcon /> : <EyeOpenIcon />}
              </button>
            </div>
          </div>

          <div className="mb-4">
            <label htmlFor="confirmPassword" className="form-label text-white small fw-semibold">
              Confirm Password
            </label>
            <div className="input-group password-input-group">
              <input
                type={showConfirmPassword ? "text" : "password"}
                id="confirmPassword"
                name="confirmPassword"
                className="form-control"
                placeholder="••••••••"
                value={formData.confirmPassword}
                onChange={handleInputChange}
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
            <button className="btn btn-primary py-2 fw-semibold" type="submit" disabled={loading}>
              {loading ? "Registering..." : "Register Account"}
            </button>
            <Link to="/login" className="btn btn-secondary py-2 text-center">
              Already have an account? Sign In
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}

export default RegistrationPage;
