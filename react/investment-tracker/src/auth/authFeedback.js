const ACTIVATION_EMAIL_KEY = "investment-tracker.pending-activation-email";

export function authErrorMessage(error, fallback) {
  const data = error?.response?.data || error;
  if (typeof data === "string") return data;
  if (Array.isArray(data?.validationErrors)) return data.validationErrors.join(" ");
  return data?.error || data?.message || fallback;
}

export function pendingActivationEmail() {
  try {
    return sessionStorage.getItem(ACTIVATION_EMAIL_KEY) || "";
  } catch {
    return "";
  }
}

export function rememberActivationEmail(email) {
  try {
    if (email) sessionStorage.setItem(ACTIVATION_EMAIL_KEY, email);
    else sessionStorage.removeItem(ACTIVATION_EMAIL_KEY);
  } catch {
    // Router state still supplies the email when browser storage is unavailable.
  }
}
