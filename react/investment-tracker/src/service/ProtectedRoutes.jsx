import React from "react";
import { Navigate } from "react-router-dom";
import UserService from "./UserService";

const ProtectedRoute = ({ children }) => {
  if (!UserService.isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  return children;
};

export default ProtectedRoute;
