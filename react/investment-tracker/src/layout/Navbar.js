import React, { useEffect, useState } from "react";
import "../styles/Navbar.css";
import { Link, useNavigate, useLocation } from "react-router-dom";
import UserService from "../service/UserService";
import checkTokenExpiration from "../utils/checkTokenExpiration";

export default function Navbar() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const token = UserService.getToken();
    if (token) {
      const isTokenExpired = checkTokenExpiration(navigate);
      if (!isTokenExpired) {
        setIsLoggedIn(true);
      } else {
        setIsLoggedIn(false);
      }
    } else {
      setIsLoggedIn(false);
    }
  }, [navigate, location]);

  const handleLogout = () => {
    UserService.removeToken();
    setIsLoggedIn(false);
    navigate("/login");
  };

  return (
    <header className="custom-navbar-wrapper">
      <nav className="navbar navbar-expand-lg custom-navbar">
        <div className="container">
          <Link className="logo-brand d-flex align-items-center gap-2" to="/">
            <span className="logo-icon">📈</span>
            <span className="logo-text">Investment Tracker</span>
          </Link>

          <div className="d-flex align-items-center gap-2 mx-auto">
            {isLoggedIn && (
              <>
                <Link
                  className={`nav-pill-link ${location.pathname === "/" ? "active" : ""}`}
                  to="/"
                >
                  📁 Portfolio Overview
                </Link>
                <Link
                  className={`nav-pill-link ${location.pathname === "/tracker" ? "active" : ""}`}
                  to="/tracker"
                >
                  📈 Steam Price Tracker
                </Link>
              </>
            )}
          </div>

          <div className="d-flex align-items-center gap-3">
            {isLoggedIn ? (
              <button
                className="logout-button btn btn-secondary btn-sm px-3"
                onClick={handleLogout}
              >
                Logout
              </button>
            ) : (
              <Link to="/login" className="btn btn-primary btn-sm px-3">
                Sign In
              </Link>
            )}
          </div>
        </div>
      </nav>
    </header>
  );
}
