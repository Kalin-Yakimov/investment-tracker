import { useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import AuthService from "../AuthService";
import useRequestScope from "../../../shared/hooks/useRequestScope";
import { isRequestCanceled } from "../../../shared/utils/RequestScope";
import "../styles/ActivateAccountPage.css";
import { authErrorMessage, pendingActivationEmail, rememberActivationEmail } from "../authFeedback";

function ActivateAccountPage() {
  const scope = useRequestScope({ guardSession: false });
  const location = useLocation();
  const [email, setEmail] = useState(() => location.state?.email || pendingActivationEmail());
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const busy = useRef(false);
  const emailInput = useRef(null);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (busy.current) return;
    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    busy.current = true;
    setLoading(true);
    setError("");
    setMessage("");

    try {
      await AuthService.confirm(token, email);
      if (!request.isCurrent()) return;
      rememberActivationEmail("");
      navigate("/login");
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) setError(authErrorMessage(error, "Activation failed. Please check your email and code."));
    } finally {
      busy.current = false;
      if (request.isCurrent()) setLoading(false);
      request.finish();
    }
  };

  const handleResend = async () => {
    if (busy.current || !emailInput.current.reportValidity()) return;
    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    busy.current = true;
    setLoading(true);
    setError("");
    setMessage("");
    try {
      await AuthService.resendActivation(email);
      if (!request.isCurrent()) return;
      rememberActivationEmail(email.trim());
      setToken("");
      setMessage("If this account needs activation, a new code has been requested. Check your inbox and spam folder.");
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) setError(authErrorMessage(error, "Could not request another code. Please try again later."));
    } finally {
      busy.current = false;
      if (request.isCurrent()) setLoading(false);
      request.finish();
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
            {message && <div className="alert alert-success py-2 text-center">{message}</div>}
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label htmlFor="activation-email" className="form-label text-white">Email Address</label>
                <input id="activation-email" ref={emailInput} type="email" className="form-control"
                  value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email"
                  maxLength={254} disabled={loading} required />
              </div>
              <div className="mb-4">
                <label htmlFor="activation-code" className="form-label text-white">Activation Code</label>
                <input
                  id="activation-code"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  disabled={loading}
                  className="form-control text-center font-mono fs-4 letter-spacing-2"
                  value={token}
                  maxLength={6}
                  onChange={(e) => setToken(e.target.value)}
                  placeholder="------"
                  required
                />
              </div>
              <div className="d-grid gap-2">
                <button className="btn btn-primary py-2" type="submit" disabled={loading}>
                  {loading ? "Please wait..." : "Activate Account"}
                </button>
                <button className="btn btn-secondary py-2" type="button" onClick={handleResend} disabled={loading}>
                  Request New Code
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
