import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import App from "./App";
import RouteErrorBoundary from "./shared/components/RouteErrorBoundary";
import { BrowserRouter as Router } from "react-router-dom";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <React.StrictMode>
    <Router>
      <RouteErrorBoundary><App /></RouteErrorBoundary>
    </Router>
  </React.StrictMode>
);
