import React, { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import UserService from "../service/UserService";
import CookieService from "../service/CookieService";
import "../styles/LoginPage.css";

function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      const userData = await UserService.login(email, password);
      if (userData.token) {
        CookieService.setCookie("role", userData.role);
        navigate("/");
      } else {
        setError("Failed to login. Please check your credentials.");
      }
    } catch (error) {
      console.log(error);
      setError("Wrong email or password. Please try again.");
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
              required
            />
          </div>

          <div className="mb-4">
            <div className="d-flex justify-content-between align-items-center mb-1">
              <label htmlFor="password" className="form-label text-white small fw-semibold mb-0">
                Password
              </label>
              <Link to="/forgot-password" className="small text-info text-decoration-none">
                Forgot Password?
              </Link>
            </div>
            <input
              type="password"
              id="password"
              className="form-control"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
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
