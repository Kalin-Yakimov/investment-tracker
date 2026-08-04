import React, { useState, useEffect } from "react";
import { useNavigate, Link, useLocation } from "react-router-dom";
import UserService from "../service/UserService";
import "../styles/LoginPage.css";

// Modern SVG Eye Icons
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

function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState("");
  const [infoMessage, setInfoMessage] = useState("");

  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    if (searchParams.get("expired") === "true") {
      setInfoMessage("Your session has expired. Please sign in again.");
    }
  }, [location]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      const userData = await UserService.login(email, password, rememberMe);
      if (userData.token) {
        navigate("/");
      } else {
        setError("Failed to login. Please check your credentials.");
      }
    } catch (error) {
      console.log(error);
      setError(
        typeof error === "string"
          ? error
          : error.message || "Wrong email or password. Please try again."
      );
    }
  };

  const handleRegister = () => {
    navigate("/registration");
  };

  return (
    <div className="auth-container container">
      <div className="card auth-card text-center">
        <div className="mb-3">
          <span style={{ fontSize: "2.5rem" }}>📈</span>
        </div>
        <h2 className="auth-header">Sign In</h2>
        <p className="auth-subtitle text-white-50">Welcome back to Investment Tracker</p>

        {infoMessage && <p className="alert alert-info py-2 small">{infoMessage}</p>}
        {error && <p className="error-message alert alert-danger py-2">{error}</p>}

        <form className="login-form text-start" onSubmit={handleSubmit}>
          <div className="mb-3">
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
              autoComplete="username"
              required
            />
          </div>

          <div className="mb-3">
            <div className="d-flex justify-content-between align-items-center mb-1">
              <label htmlFor="password" className="form-label text-white small fw-semibold mb-0">
                Password
              </label>
              <Link to="/forgot-password" className="small text-info text-decoration-none">
                Forgot Password?
              </Link>
            </div>
            <div className="input-group password-input-group">
              <input
                type={showPassword ? "text" : "password"}
                id="password"
                className="form-control"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
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

          <div className="form-check mb-4">
            <input
              type="checkbox"
              className="form-check-input"
              id="rememberMe"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
            />
            <label className="form-check-label text-white-50 small ms-1" htmlFor="rememberMe">
              Remember me
            </label>
          </div>

          <div className="d-grid gap-2">
            <button className="login-button btn btn-primary" type="submit">
              Sign In
            </button>
            <button
              className="register-button btn btn-secondary"
              type="button"
              onClick={handleRegister}
            >
              Create Account
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default LoginPage;
