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
      setError("Activation failed. Please try again.");
      setTimeout(() => {
        setError("");
      }, 6000);
    }
  };

  return (
    <div className="container">
      <div className="row justify-content-center align-items-start vh-100">
        <div className="col-md-6">
          <div className="activation-box p-4 mb-4 shadow-lg rounded">
            <h2 className="text-center mb-4">Activate Account</h2>
            {error && <div className="alert alert-danger">{error}</div>}
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <input
                  type="text"
                  className="form-control"
                  value={token}
                  maxLength={6}
                  onChange={(e) => setToken(e.target.value)}
                  placeholder="Enter code here"
                  required
                />
              </div>
              <div className="d-grid">
                <button className="btn btn-primary btn-xs" type="submit">
                  Activate
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
