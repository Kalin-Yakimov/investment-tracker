import React, { useEffect, useState, useRef } from "react";
import "../styles/Navbar.css";
import { Link, useNavigate, useLocation } from "react-router-dom";
import UserService from "../service/UserService";
import ChangePasswordModal from "../auth/ChangePasswordModal";
import ChangeUsernameModal from "../auth/ChangeUsernameModal";

export default function Navbar() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showChangePassModal, setShowChangePassModal] = useState(false);
  const [showChangeUserModal, setShowChangeUserModal] = useState(false);
  const [refreshUser, setRefreshUser] = useState(0);
  const navigate = useNavigate();
  const location = useLocation();
  const menuRef = useRef(null);

  useEffect(() => {
    const updateLoginState = () => setIsLoggedIn(UserService.isAuthenticated());
    updateLoginState();
    return UserService.subscribe(updateLoginState);
  }, [location, refreshUser]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setShowProfileMenu(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleLogout = async () => {
    setShowProfileMenu(false);
    await UserService.logout();
    navigate("/login");
  };

  const handleLogoutAll = async () => {
    const confirmed = window.confirm(
      "Sign out from all devices? You will need to sign in again.",
    );
    if (!confirmed) return;

    setShowProfileMenu(false);
    await UserService.logoutAll();
    navigate("/login");
  };

  const getUserInfo = () => {
    try {
      const token = UserService.getToken();
      if (!token) return { name: "User Account", email: "user@account.com" };
      const payload = JSON.parse(atob(token.split(".")[1]));
      return {
        name: payload.fullName || payload.email || "User Account",
        email: payload.email || "user@account.com",
      };
    } catch {
      return { name: "User Account", email: "user@account.com" };
    }
  };

  const userInfo = getUserInfo();

  return (
    <>
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
                    Portfolio Overview
                  </Link>
                  <Link
                    className={`nav-pill-link ${location.pathname === "/tracker" ? "active" : ""}`}
                    to="/tracker"
                  >
                    Steam Price Tracker
                  </Link>
                  <Link
                    className={`nav-pill-link ${location.pathname === "/inventory-calculator" ? "active" : ""}`}
                    to="/inventory-calculator"
                  >
                    Inventory Calculator
                  </Link>
                </>
              )}
            </div>

            <div className="d-flex align-items-center gap-3">
              {isLoggedIn ? (
                <div className="position-relative" ref={menuRef}>
                  <button
                    className="profile-nav-btn d-flex align-items-center gap-2 btn btn-outline-light border-0 py-1 px-2 rounded-pill"
                    onClick={() => setShowProfileMenu(!showProfileMenu)}
                    aria-expanded={showProfileMenu}
                  >
                    <div className="profile-avatar d-flex align-items-center justify-content-center rounded-circle bg-primary text-white fw-bold">
                      {userInfo.name.charAt(0).toUpperCase()}
                    </div>
                    <span className="small fw-semibold text-white d-none d-md-inline">
                      {userInfo.name}
                    </span>
                    <span className="small text-white-50 ms-1">▼</span>
                  </button>

                  {showProfileMenu && (
                    <div className="profile-popover-card position-absolute end-0 mt-2 shadow-lg p-3 rounded-3">
                      <div className="d-flex align-items-center gap-3 pb-3 mb-2 border-bottom border-secondary-subtle">
                        <div className="profile-avatar-lg d-flex align-items-center justify-content-center rounded-circle bg-primary text-white fs-4 fw-bold">
                          {userInfo.name.charAt(0).toUpperCase()}
                        </div>
                        <div className="overflow-hidden">
                          <h6 className="text-white mb-0 fw-bold text-truncate">{userInfo.name}</h6>
                          <span className="text-white-50 small text-truncate d-block">{userInfo.email}</span>
                        </div>
                      </div>

                      <div className="d-flex flex-column gap-1">
                        <button
                          className="popover-item-btn d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 text-white rounded-2"
                          onClick={() => {
                            setShowProfileMenu(false);
                            setShowChangeUserModal(true);
                          }}
                        >
                          <span>✏️</span>
                          <span className="small fw-semibold">Change Username</span>
                        </button>

                        <button
                          className="popover-item-btn d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 text-white rounded-2"
                          onClick={() => {
                            setShowProfileMenu(false);
                            setShowChangePassModal(true);
                          }}
                        >
                          <span>🔒</span>
                          <span className="small fw-semibold">Change Password</span>
                        </button>

                        <div className="border-top border-secondary-subtle my-1"></div>

                        <button
                          className="popover-item-btn text-danger d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 rounded-2"
                          onClick={handleLogout}
                        >
                          <span>🚪</span>
                          <span className="small fw-semibold">Sign Out</span>
                        </button>
                        <button
                          className="popover-item-btn text-danger d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 rounded-2"
                          onClick={handleLogoutAll}
                        >
                          <span>🛡️</span>
                          <span className="small fw-semibold">Sign Out All Devices</span>
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <Link to="/login" className="btn btn-primary btn-sm px-3">
                  Sign In
                </Link>
              )}
            </div>
          </div>
        </nav>
      </header>

      <ChangeUsernameModal
        show={showChangeUserModal}
        onClose={() => setShowChangeUserModal(false)}
        currentUsername={userInfo.name}
        onSuccess={() => setRefreshUser((prev) => prev + 1)}
      />

      <ChangePasswordModal
        show={showChangePassModal}
        onClose={() => setShowChangePassModal(false)}
      />
    </>
  );
}
