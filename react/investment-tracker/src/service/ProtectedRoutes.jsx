import React from "react";
import { Navigate, useNavigate } from "react-router-dom";
import UserService from "./UserService";
import checkTokenExpiration from "../utils/checkTokenExpiration";

const ProtectedRoute = ({ children }) => {
  const navigate = useNavigate();
  const token = UserService.getToken();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  checkTokenExpiration(navigate);

  return children;
};

export default ProtectedRoute;
