const configuredSearchDebounce = Number(
  import.meta.env.VITE_ITEM_SEARCH_DEBOUNCE_MS,
);

export const ITEM_SEARCH_DEBOUNCE_MS =
  Number.isFinite(configuredSearchDebounce) && configuredSearchDebounce >= 0
    ? configuredSearchDebounce
    : 1000;

// Transport/work limits, not cache TTLs or token expiration settings.
export const AUTH_REFRESH_TIMEOUT_MS = 15000;
export const API_REQUEST_TIMEOUT_MS = 120000;
// Includes bounded Steam pagination, profile resolution and cold price-feed preparation.
export const INVENTORY_REQUEST_TIMEOUT_MS = 300000;
export const PRICE_HISTORY_CONCURRENCY = 4;
export const SUPPORT_JOB_POLL_MS = 2000;
export const SUPPORT_TICKET_POLL_MS = 30000;
