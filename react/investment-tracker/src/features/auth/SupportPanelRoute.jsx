import React from "react";
import { Navigate } from "react-router-dom";
import AuthService from "./AuthService";

export default function SupportPanelRoute({ children }) {
  if (!AuthService.isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  if (!AuthService.hasAnyAuthority("ADMIN", "SUPPORT")) {
    return <Navigate to="/" replace />;
  }

  return children;
}
