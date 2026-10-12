// Build-time mapping only. Never spread the process environment into client code.
export function readPublicEnvironment(env) {
  return {
    VITE_API_URL: env.VITE_API_URL || env.REACT_APP_API_URL || "http://localhost:8080",
    VITE_ITEM_SEARCH_DEBOUNCE_MS:
      env.VITE_ITEM_SEARCH_DEBOUNCE_MS ?? env.REACT_APP_ITEM_SEARCH_DEBOUNCE_MS ?? "1000",
  };
}
