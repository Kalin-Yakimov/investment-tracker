export function requestErrorMessage(error, fallback = "The request failed. Please try again.", { mutation = false } = {}) {
  if (error?.code === "ECONNABORTED" || error?.code === "ETIMEDOUT") {
    return mutation
      ? "The request timed out. Check the latest data before trying again; your change may already have been saved."
      : "The request timed out. Please try again.";
  }
  if (error?.code === "ERR_NETWORK") return mutation
    ? "The server response could not be received. Check the latest data before trying again; your change may already have been saved."
    : "The server could not be reached. Check your connection and try again.";
  const data = error?.response?.data || error;
  if (Array.isArray(data?.validationErrors)) return data.validationErrors.filter(value => typeof value === "string").join(" ") || fallback;
  return typeof data?.error === "string" ? data.error : fallback;
}
