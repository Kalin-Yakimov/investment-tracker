import React, { useEffect, useState } from "react";
import "../styles/Navbar.css";
import { Link, useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import checkTokenExpiration from "../utils/checkTokenExpiration";

export default function Navbar() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const token = UserService.getToken();
    if (token) {
      const isTokenExpired = checkTokenExpiration(navigate);
      if (!isTokenExpired) {
        setIsLoggedIn(true);
      } else {
        setIsLoggedIn(false);
      }
    }
  }, [navigate]);

  const handleLogout = () => {
    UserService.removeToken();
    setIsLoggedIn(false);
    navigate("/login");
  };

  return (
    <div>
      <nav className="navbar navbar-expand-lg custom-navbar">
        <div className="container-fluid">
          <Link className="logo" to="/">
            Investment-Tracker
          </Link>

          <div className="d-flex justify-content-center align-items-center flex-grow-1">
            <Link className="nav-link me-3" to="/tracker">
              Price Tracker
            </Link>
          </div>
          {isLoggedIn && (
            <button
              className="logout-button btn btn-secondary"
              onClick={handleLogout}
            >
              Logout
            </button>
          )}
        </div>
      </nav>
    </div>
  );
}
