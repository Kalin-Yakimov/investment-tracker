import { useEffect, useState, useRef } from "react";
import "./Navbar.css";
import { Link, useNavigate, useLocation } from "react-router-dom";
import AuthService from "../features/auth/AuthService";
import useRequestScope from "../shared/hooks/useRequestScope";
import ChangePasswordModal from "../features/auth/components/ChangePasswordModal";
import ChangeUsernameModal from "../features/auth/components/ChangeUsernameModal";
import ContactUsModal from "../features/contact/ContactUsModal";

export default function Navbar() {
  const scope = useRequestScope({ guardSession: false });
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showChangePassModal, setShowChangePassModal] = useState(false);
  const [showChangeUserModal, setShowChangeUserModal] = useState(false);
  const [showContactModal, setShowContactModal] = useState(false);
  const [refreshUser, setRefreshUser] = useState(0);
  const navigate = useNavigate();
  const location = useLocation();
  const menuRef = useRef(null);
  const previousOwner = useRef(null);

  useEffect(() => {
    const updateLoginState = () => setIsLoggedIn(AuthService.isAuthenticated());
    updateLoginState();
    return AuthService.subscribe(updateLoginState);
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
    const request = scope.begin("logout", { replace: false, abortable: false });
    if (!request) return;
    setShowProfileMenu(false);
    setShowChangePassModal(false);
    setShowChangeUserModal(false);
    setShowContactModal(false);
    try {
      await AuthService.logout();
    } catch {
      // Axios failures can carry Authorization headers; never log the request object.
      console.error("Sign out failed; server-side revocation could not be confirmed.");
    } finally {
      if (request.isCurrent() && !AuthService.isAuthenticated()) navigate("/login");
      request.finish();
    }
  };

  const getUserInfo = () => {
    return AuthService.getCurrentUser() || {
      name: "User Account",
      email: "user@account.com",
      authorities: [],
    };
  };

  const userInfo = getUserInfo();

  useEffect(() => {
    if (previousOwner.current !== userInfo.id) {
      setShowProfileMenu(false);
      setShowChangeUserModal(false);
      setShowContactModal(false);
      // Password-change success briefly remains visible after its own logout.
      // A new account must never inherit the previous account's modal or timer.
      if (userInfo.id != null) setShowChangePassModal(false);
    }
    previousOwner.current = userInfo.id;
  }, [userInfo.id]);

  return (
    <>
      <header className="custom-navbar-wrapper">
        <nav className="navbar navbar-expand-lg custom-navbar">
          <div className="container">
            <Link className="logo-brand d-flex align-items-center gap-2" to="/">
              <span className="logo-icon">📈</span>
              <span className="logo-text">Investment Tracker</span>
            </Link>

            <div className="d-flex align-items-center gap-2 mx-auto navbar-primary-links">
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

            <div className="d-flex align-items-center gap-3 navbar-account-actions">
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
                        {AuthService.hasAnyAuthority("ADMIN", "SUPPORT") && (
                          <button
                            className="popover-item-btn d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 text-white rounded-2"
                            onClick={() => {
                              setShowProfileMenu(false);
                              navigate("/admin");
                            }}
                          >
                            <span className="small fw-semibold">Support Panel</span>
                          </button>
                        )}

                        <button
                          className="popover-item-btn d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 text-white rounded-2"
                          onClick={() => {
                            setShowProfileMenu(false);
                            setShowChangeUserModal(true);
                          }}
                        >
                          <span className="small fw-semibold">Change Username</span>
                        </button>

                        <button
                          className="popover-item-btn d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 text-white rounded-2"
                          onClick={() => {
                            setShowProfileMenu(false);
                            setShowChangePassModal(true);
                          }}
                        >
                          <span className="small fw-semibold">Change Password</span>
                        </button>

                        <button
                          className="popover-item-btn d-flex align-items-center btn text-start w-100 py-2 px-3 text-white rounded-2"
                          onClick={() => {
                            setShowProfileMenu(false);
                            setShowContactModal(true);
                          }}
                        >
                          <span className="small fw-semibold">Contact Us</span>
                        </button>

                        <div className="border-top border-secondary-subtle my-1"></div>

                        <button
                          className="popover-item-btn text-danger d-flex align-items-center gap-2 btn text-start w-100 py-2 px-3 rounded-2"
                          onClick={handleLogout}
                        >
                          <span className="small fw-semibold">Sign Out</span>
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

      <ContactUsModal
        show={showContactModal}
        onClose={() => setShowContactModal(false)}
        defaultEmail={userInfo.email}
      />
    </>
  );
}
