import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
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
    <div className="auth-container container mt-5">
      <div className="row justify-content-center">
        <div className="col-12 col-sm-10 col-md-8 col-lg-6">
          <h2 className="login-header text-center mb-4">Login</h2>
          {error && <p className="error-message alert alert-danger">{error}</p>}
          <form className="login-form" onSubmit={handleSubmit}>
            <div className="form-group mb-3">
              <label htmlFor="email">Email:</label>
              <input
                type="email"
                id="email"
                className="form-control"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="form-group mb-3">
              <label htmlFor="password">Password:</label>
              <input
                type="password"
                id="password"
                className="form-control"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div className="d-grid gap-2">
              <button className="login-button btn btn-primary" type="submit">
                Login
              </button>
              <button
                className="register-button btn btn-secondary"
                type="button"
                onClick={handleRegister}
              >
                Register
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
