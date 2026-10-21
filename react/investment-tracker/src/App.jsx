import { useState, useEffect } from "react";
import { Routes, Route, useParams, useLocation } from "react-router-dom";
import RouteErrorBoundary from "./shared/components/RouteErrorBoundary";
import "bootstrap/dist/css/bootstrap.min.css";
import Navbar from "./layout/Navbar";
import PortfolioOverviewPage from "./features/portfolio/PortfolioOverviewPage";
import PriceTrackerPage from "./features/price-tracker/PriceTrackerPage";
import InventoryCalculatorPage from "./features/inventory/InventoryCalculatorPage";
import RegisterPortfolioItem from "./features/portfolio/components/RegisterPortfolioItem";
import EditPortfolioItemPage from "./features/portfolio/EditPortfolioItemPage";
import PortfolioItemDetailsPage from "./features/portfolio/PortfolioItemDetailsPage";
import NotFoundPage from "./shared/NotFoundPage";
import LoginPage from "./features/auth/pages/LoginPage";
import RegistrationPage from "./features/auth/pages/RegistrationPage";
import ActivateAccountPage from "./features/auth/pages/ActivateAccountPage";
import ForgotPasswordPage from "./features/auth/pages/ForgotPasswordPage";
import ProtectedRoute from "./features/auth/ProtectedRoute";
import AuthService from "./features/auth/AuthService";
import SupportPanelRoute from "./features/auth/SupportPanelRoute";
import SupportPanelPage from "./features/support/SupportPanelPage";
import { CookiesProvider } from "react-cookie";

function ItemRoute({ component: Component }) {
  const { id } = useParams();
  return <Component key={id} />;
}

function App() {
  const location = useLocation();
  const [refresh, SetRefresh] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [, setAuthVersion] = useState(0);

  useEffect(() => {
    let mounted = true;
    const unsubscribe = AuthService.subscribe(() => {
      setAuthVersion((version) => version + 1);
    });
    AuthService.initializeSession().finally(() => { if (mounted) setAuthReady(true); });
    return () => { mounted = false; unsubscribe(); };
  }, []);

  const handleItemAdded = () => {
    SetRefresh((previous) => !previous);
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
  const owner = AuthService.getCurrentUser()?.id;

  return (
    <CookiesProvider>
      <div className="App">
        <Navbar />
        <RouteErrorBoundary key={`${owner ?? "public"}:${location.pathname}`}>
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
              <ProtectedRoute key={owner}>
                <>
                  <RegisterPortfolioItem onItemAdded={handleItemAdded} />
                  <PortfolioOverviewPage key={refresh} />
                </>
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/editItem/:id"
            element={
              <ProtectedRoute key={owner}>
                <ItemRoute component={EditPortfolioItemPage} />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/viewItem/:id"
            element={
              <ProtectedRoute key={owner}>
                <ItemRoute component={PortfolioItemDetailsPage} />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/tracker"
            element={
              <ProtectedRoute key={owner}>
                <PriceTrackerPage />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/inventory-calculator"
            element={
              <ProtectedRoute key={owner}>
                <InventoryCalculatorPage />
              </ProtectedRoute>
            }
          />
          <Route
            exact
            path="/admin"
            element={
              <SupportPanelRoute key={owner}>
                <SupportPanelPage />
              </SupportPanelRoute>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </RouteErrorBoundary>
      </div>
    </CookiesProvider>
  );
}

export default App;
