import { useState, useEffect } from "react";
import { Routes, Route } from "react-router-dom";
import "../node_modules/bootstrap/dist/css/bootstrap.min.css";
import Navbar from "./layout/Navbar";
import Home from "./pages/Home";
import PriceTracker from "./pages/PriceTracker";
import InventoryCalculatorPage from "./pages/InventoryCalculatorPage";
import AddItem from "./item/AddItem";
import EditItem from "./item/EditItem";
import ViewItem from "./item/ViewItem";
import NotFoundPage from "./pages/NotFoundPage";
import LoginPage from "./auth/LoginPage";
import RegistrationPage from "./auth/RegistrationPage";
import ActivateAccountPage from "./auth/ActivateAccountPage";
import ForgotPasswordPage from "./auth/ForgotPasswordPage";
import ProtectedRoute from "./service/ProtectedRoutes";
import UserService from "./service/UserService";
import { CookiesProvider } from "react-cookie";

function App() {
  const [refresh, SetRefresh] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [, setAuthVersion] = useState(0);

  useEffect(() => {
    const unsubscribe = UserService.subscribe(() => {
      setAuthVersion((version) => version + 1);
    });
    UserService.initializeSession().finally(() => setAuthReady(true));
    return unsubscribe;
  }, []);

  const handleItemAdded = () => {
    SetRefresh(!refresh);
  };

  if (!authReady) {
    return (
      <div className="min-vh-100 d-flex align-items-center justify-content-center">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <CookiesProvider>
      <div className="App">
        <Navbar />
        <Routes>
          <Route exact path="/login" element={<LoginPage />} />
          <Route exact path="/registration" element={<RegistrationPage />} />
          <Route
            exact
            path="/activate-account"
            element={<ActivateAccountPage />}
          />
          <Route
            exact
            path="/forgot-password"
            element={<ForgotPasswordPage />}
          />
          <Route
            exact
            path="/"
            element={
              <ProtectedRoute>
                <>
                  <AddItem onItemAdded={handleItemAdded} />
                  <Home key={refresh} />
                </>
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/editItem/:id"
            element={
              <ProtectedRoute>
                <EditItem />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/viewItem/:id"
            element={
              <ProtectedRoute>
                <ViewItem />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/tracker"
            element={
              <ProtectedRoute>
                <PriceTracker />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/inventory-calculator"
            element={
              <ProtectedRoute>
                <InventoryCalculatorPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </div>
    </CookiesProvider>
  );
}

export default App;
