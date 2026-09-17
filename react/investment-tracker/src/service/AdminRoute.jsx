import React from "react";
import { Navigate } from "react-router-dom";
import UserService from "./UserService";

export default function AdminRoute({ children }) {
  if (!UserService.isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  if (!UserService.hasAnyAuthority("ADMIN", "SUPPORT")) {
    return <Navigate to="/" replace />;
  }

  return children;
}
