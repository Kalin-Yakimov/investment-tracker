import { useState, useEffect } from "react";
import { Routes, Route, useNavigate } from "react-router-dom";
import "../node_modules/bootstrap/dist/css/bootstrap.min.css";
import Navbar from "./layout/Navbar";
import Home from "./pages/Home";
import AddItem from "./item/AddItem";
import EditItem from "./item/EditItem";
import ViewItem from "./item/ViewItem";
import NotFoundPage from "./pages/NotFoundPage";
import LoginPage from "./auth/LoginPage";
import RegistrationPage from "./auth/RegistrationPage";
import ActivateAccountPage from "./auth/ActivateAccountPage";
import ProtectedRoute from "./service/ProtectedRoutes";
import checkTokenExpiration from "./utils/checkTokenExpiration";
import { CookiesProvider } from "react-cookie";

function App() {
  const [refresh, SetRefresh] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    checkTokenExpiration(navigate);
  }, [navigate]);

  const handleItemAdded = () => {
    SetRefresh(!refresh);
  };

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
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </div>
    </CookiesProvider>
  );
}

export default App;
