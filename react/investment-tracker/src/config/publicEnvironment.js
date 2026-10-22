// Build-time mapping only. Never spread the process environment into client code.
export function readPublicEnvironment(env) {
  if (env.VITE_DEPLOYMENT_TARGET === "production") {
    // No silent localhost fallback or legacy URL in a deployable artifact.
    let url;
    try { url = new URL(env.VITE_API_URL); } catch { /* fail below without disclosing the value */ }
    if (!url || url.protocol !== "https:" || !url.hostname || url.hostname === "localhost"
        || url.username || url.password || url.search || url.hash || url.pathname !== "/"
        || env.VITE_API_URL.endsWith("/")) {
      throw new Error("Production build requires an explicit HTTPS origin in VITE_API_URL (without a trailing slash).");
    }
  }
  return {
    VITE_API_URL: env.VITE_API_URL || env.REACT_APP_API_URL || "http://localhost:8080",
    VITE_ITEM_SEARCH_DEBOUNCE_MS:
      env.VITE_ITEM_SEARCH_DEBOUNCE_MS ?? env.REACT_APP_ITEM_SEARCH_DEBOUNCE_MS ?? "1000",
  };
}
