import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import "../styles/ActivateAccountPage.css";

function ActivateAccountPage() {
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      await UserService.confirm(token);
      navigate("/");
    } catch (error) {
      console.error("Activation error:", error);
      setError("Activation failed. Please check your activation code.");
      setTimeout(() => {
        setError("");
      }, 6000);
    }
  };

  return (
    <div className="container">
      <div className="row justify-content-center align-items-center min-vh-100">
        <div className="col-12 col-sm-8 col-md-6 col-lg-5">
          <div className="activation-box p-4 shadow-lg rounded">
            <div className="text-center mb-3">
              <span style={{ fontSize: "2.5rem" }}>🔑</span>
              <h2 className="mt-2 text-white">Activate Account</h2>
              <p className="text-white-50 small">Enter the 6-digit activation code sent to your email</p>
            </div>
            {error && <div className="alert alert-danger py-2 text-center">{error}</div>}
            <form onSubmit={handleSubmit}>
              <div className="mb-4">
                <input
                  type="text"
                  className="form-control text-center font-mono fs-4 letter-spacing-2"
                  value={token}
                  maxLength={6}
                  onChange={(e) => setToken(e.target.value)}
                  placeholder="------"
                  required
                />
              </div>
              <div className="d-grid">
                <button className="btn btn-primary py-2" type="submit">
                  Activate Account
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ActivateAccountPage;
