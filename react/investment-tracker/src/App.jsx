import { useState, useEffect, useLayoutEffect, useCallback, lazy, Suspense } from "react";
import { Routes, Route, useParams, useLocation } from "react-router-dom";
import RouteErrorBoundary from "./shared/components/RouteErrorBoundary";
import "bootstrap/dist/css/bootstrap.min.css";
import Navbar from "./layout/Navbar";
import NotFoundPage from "./shared/NotFoundPage";
import LoginPage from "./features/auth/pages/LoginPage";
import RegistrationPage from "./features/auth/pages/RegistrationPage";
import ActivateAccountPage from "./features/auth/pages/ActivateAccountPage";
import ForgotPasswordPage from "./features/auth/pages/ForgotPasswordPage";
import ProtectedRoute from "./features/auth/ProtectedRoute";
import AuthService from "./features/auth/AuthService";
import SupportPanelRoute from "./features/auth/SupportPanelRoute";
import { CookiesProvider } from "react-cookie";

// Load feature code only when its route is opened; public sign-in does not need charts or support tables.
const PortfolioOverviewPage = lazy(() => import("./features/portfolio/PortfolioOverviewPage"));
const PriceTrackerPage = lazy(() => import("./features/price-tracker/PriceTrackerPage"));
const InventoryCalculatorPage = lazy(() => import("./features/inventory/InventoryCalculatorPage"));
const RegisterPortfolioItem = lazy(() => import("./features/portfolio/components/RegisterPortfolioItem"));
const EditPortfolioItemPage = lazy(() => import("./features/portfolio/EditPortfolioItemPage"));
const PortfolioItemDetailsPage = lazy(() => import("./features/portfolio/PortfolioItemDetailsPage"));
const SupportPanelPage = lazy(() => import("./features/support/SupportPanelPage"));

function ItemRoute({ component: Component }) {
  const { id } = useParams();
  return <Component key={id} />;
}

function App() {
  const location = useLocation();
  const [refresh, SetRefresh] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [, setAuthVersion] = useState(0);
  const [portfolioPosition, setPortfolioPosition] = useState({ owner: null, page: 0, anchor: null });

  // A temporary return position for the portfolio -> details -> edit workflow,
  // not URL/F5 state or a cache of user records.
  const rememberPortfolioPage = useCallback((page) => {
    const owner = AuthService.getCurrentUser()?.id;
    setPortfolioPosition(previous => previous.owner === owner && previous.page === page
      ? previous : { owner, page, anchor: previous.owner === owner ? previous.anchor : null });
  }, []);

  const rememberPortfolioAnchor = useCallback((anchor) => {
    const owner = AuthService.getCurrentUser()?.id;
    setPortfolioPosition(previous => ({
      owner, page: previous.owner === owner ? previous.page : 0, anchor,
    }));
  }, []);

  useEffect(() => {
    if (location.pathname !== "/" && !/^\/(viewItem|editItem)\/[^/]+\/?$/.test(location.pathname)) {
      setPortfolioPosition(previous => previous.owner === null ? previous : { owner: null, page: 0, anchor: null });
    }
  }, [location.pathname]);

  // The application owns history scrolling; native Back restoration must not
  // compete with our asynchronous list restoration.
  useLayoutEffect(() => {
    if (!("scrollRestoration" in window.history)) return;
    const previous = window.history.scrollRestoration;
    window.history.scrollRestoration = "manual";
    return () => { window.history.scrollRestoration = previous; };
  }, []);

  // Main routes start at the top. Portfolio restores its own return position
  // only after its asynchronously loaded content is ready.
  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "instant" });
  }, [location.pathname]);

  useEffect(() => {
    let mounted = true;
    const unsubscribe = AuthService.subscribe(() => {
      setAuthVersion((version) => version + 1);
    });
    AuthService.initializeSession().finally(() => { if (mounted) setAuthReady(true); });
    return () => { mounted = false; unsubscribe(); };
  }, []);

  const handleItemAdded = () => {
    setPortfolioPosition({ owner: null, page: 0, anchor: null });
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
        <Suspense fallback={<div className="container py-5 text-secondary" role="status">Loading page...</div>}>
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
                  <PortfolioOverviewPage key={refresh}
                    initialPage={portfolioPosition.owner === owner ? portfolioPosition.page : 0}
                    initialReturnAnchor={portfolioPosition.owner === owner ? portfolioPosition.anchor : null}
                    onItemOpen={rememberPortfolioAnchor}
                    onPageLoaded={rememberPortfolioPage} />
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
        </Suspense>
        </RouteErrorBoundary>
      </div>
    </CookiesProvider>
  );
}

export default App;
