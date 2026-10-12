import axios from "axios";

export const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";
export const apiClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true });

// Called once by the authentication service. Domain services never own sessions.
let sessionInterceptorsInstalled = false;
export function configureSessionInterceptors(auth) {
  if (sessionInterceptorsInstalled) return;
  sessionInterceptorsInstalled = true;
  apiClient.interceptors.request.use((config) => {
    const token = auth.getToken();
    const url = config.url || "";
    let isBackendRequest = false;
    try {
      isBackendRequest = new URL(url, config.baseURL || API_BASE_URL).origin === new URL(API_BASE_URL).origin;
    } catch { /* Invalid URLs must never receive credentials. */ }
    if (!isBackendRequest) {
      throw new axios.AxiosError("The application API client only accepts the configured backend origin", "ERR_BAD_REQUEST", config);
    }
    const isPublicAuthRequest = [
      "/api/v1/auth/authenticate",
      "/api/v1/auth/register",
      "/api/v1/auth/activate-account",
      "/api/v1/auth/resend-activation",
      "/api/v1/auth/forgot-password",
      "/api/v1/auth/reset-password",
      "/api/v1/auth/refresh",
    ].some((path) => url.includes(path));

    if (isPublicAuthRequest) config.skipAuthRefresh = true;
    if (isBackendRequest && !isPublicAuthRequest) {
      if (config._sessionVersion !== undefined && config._sessionVersion !== auth.sessionVersion) {
        throw new axios.CanceledError("Session changed");
      }
      config._sessionVersion = auth.sessionVersion;
      config.headers = config.headers || {};
      if (token) config.headers.Authorization = `Bearer ${token}`;
      else delete config.headers.Authorization;
    }
    return config;
  });

  apiClient.interceptors.response.use(
    (response) => {
      const version = response.config?._sessionVersion;
      if (version !== undefined && version !== auth.sessionVersion) {
        throw new axios.CanceledError("Session changed");
      }
      return response;
    },
    async (error) => {
      const originalRequest = error.config;
      const status = error.response?.status;
      const isSessionRequest = originalRequest?._sessionVersion !== undefined;
      if (
        axios.isCancel(error) || originalRequest?.signal?.aborted ||
        (isSessionRequest && (originalRequest._sessionVersion !== auth.sessionVersion || auth.sessionChanges > 0))
      ) {
        return Promise.reject(new axios.CanceledError("Request no longer belongs to the active session"));
      }

      if (
        status === 401 &&
        isSessionRequest &&
        originalRequest &&
        !originalRequest._retry &&
        !originalRequest.skipAuthRefresh
      ) {
        originalRequest._retry = true;
        const refreshed = await auth.refreshAccessToken();
        if (originalRequest.signal?.aborted || originalRequest._sessionVersion !== auth.sessionVersion) {
          return Promise.reject(new axios.CanceledError("Session changed"));
        }
        if (refreshed) {
          originalRequest.headers = originalRequest.headers || {};
          originalRequest.headers.Authorization = `Bearer ${auth.getToken()}`;
          return apiClient(originalRequest);
        }
      }

      if (status === 401 && isSessionRequest && !originalRequest?.skipAuthRefresh) {
        auth.removeToken();
        const publicPaths = ["/login", "/registration", "/forgot-password", "/activate-account"];
        if (!publicPaths.includes(window.location.pathname)) {
          window.location.href = "/login?expired=true";
        }
      }
      return Promise.reject(error);
    },
  );

}

export function apiError(error) {
  return error.response?.data || error;
}

export async function apiData(request) {
  try { return (await request).data; }
  catch (error) { throw apiError(error); }
}
