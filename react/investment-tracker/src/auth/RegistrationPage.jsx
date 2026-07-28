import React, { useState } from "react";
import UserService from "../service/UserService";
import { useNavigate, Link } from "react-router-dom";
import "../styles/RegistrationPage.css";

function RegistrationPage() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  });

  const [error, setError] = useState("");

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

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

    try {
      await UserService.register({
        username: formData.username.trim(),
        email: formData.email.trim(),
        password: formData.password,
      });

      setFormData({
        username: "",
        email: "",
        password: "",
        confirmPassword: "",
      });
      setError("");
      navigate("/activate-account");
    } catch (err) {
      console.error("Error registering user:", err);
      setError(
        typeof err === "string"
          ? err
          : err.message || "An error occurred while registering. Please try again."
      );
    }
  };

  return (
    <div className="auth-container-reg container">
      <div className="card auth-card-reg">
        <div className="text-center mb-3">
          <span style={{ fontSize: "2.5rem" }}>🚀</span>
          <h2 className="registration-header mt-2 text-white">Create Account</h2>
          <p className="text-white-50 small">Join Investment Tracker to start monitoring your portfolio</p>
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
              required
            />
          </div>

          <div className="mb-3">
            <label htmlFor="password" className="form-label text-white small fw-semibold">
              Password
            </label>
            <input
              type="password"
              id="password"
              name="password"
              className="form-control"
              placeholder="••••••••"
              value={formData.password}
              onChange={handleInputChange}
              required
            />
          </div>

          <div className="mb-4">
            <label htmlFor="confirmPassword" className="form-label text-white small fw-semibold">
              Confirm Password
            </label>
            <input
              type="password"
              id="confirmPassword"
              name="confirmPassword"
              className="form-control"
              placeholder="••••••••"
              value={formData.confirmPassword}
              onChange={handleInputChange}
              required
            />
          </div>

          <div className="d-grid gap-2">
            <button className="btn btn-primary py-2 fw-semibold" type="submit">
              Register Account
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
