import axios from "axios";
import { apiClient, API_BASE_URL, apiData, apiError, configureSessionInterceptors } from "../../shared/api/ApiClient";
import { clearInventoryCalculatorSession } from "../inventory/InventoryCalculatorSession";
import { AUTH_REFRESH_TIMEOUT_MS } from "../../config/uiConfig";

let refreshPromise = null;
let refreshVersion = null;

class AuthService {
  static BASE_URL = API_BASE_URL;
  static accessToken = null;
  static authListeners = new Set();
  static sessionVersion = 0;
  static sessionChanges = 0;

  static getSessionVersion() {
    return AuthService.sessionVersion;
  }

  static saveToken(token) {
    const previousUser = AuthService.getCurrentUser()?.id;
    AuthService.accessToken = token || null;
    if (previousUser !== AuthService.getCurrentUser()?.id) AuthService.sessionVersion += 1;
    AuthService.notifyAuthChanged();
  }

  static getToken() {
    return AuthService.accessToken;
  }

  static removeToken() {
    AuthService.sessionVersion += 1;
    AuthService.accessToken = null;
    clearInventoryCalculatorSession();
    AuthService.notifyAuthChanged();
  }

  static isAuthenticated() {
    return Boolean(AuthService.accessToken);
  }

  static getCurrentUser() {
    try {
      const token = AuthService.getToken();
      if (!token) return null;
      const payload = JSON.parse(atob(token.split(".")[1]));
      return {
        id: payload.sub ? Number(payload.sub) : null,
        email: payload.email || "",
        name: payload.fullName || payload.email || "User Account",
        authorities: Array.isArray(payload.authorities) ? payload.authorities : [],
      };
    } catch {
      return null;
    }
  }

  static hasAuthority(authority) {
    return AuthService.getCurrentUser()?.authorities.includes(authority) || false;
  }

  static hasAnyAuthority(...authorities) {
    const currentAuthorities = AuthService.getCurrentUser()?.authorities || [];
    return authorities.some((authority) => currentAuthorities.includes(authority));
  }

  static subscribe(listener) {
    AuthService.authListeners.add(listener);
    return () => AuthService.authListeners.delete(listener);
  }

  static notifyAuthChanged() {
    AuthService.authListeners.forEach((listener) => listener());
  }

  static async initializeSession() {
    if (AuthService.accessToken) return true;
    return AuthService.refreshAccessToken();
  }

  static async refreshAccessToken() {
    if (AuthService.sessionChanges > 0) return false;
    if (refreshPromise && refreshVersion !== AuthService.sessionVersion) {
      await refreshPromise;
      return AuthService.refreshAccessToken();
    }
    if (!refreshPromise) {
      const version = AuthService.sessionVersion;
      refreshVersion = version;
      const requestNewToken = () => {
        if (version !== AuthService.sessionVersion || AuthService.sessionChanges > 0) return Promise.resolve(false);
        return apiClient
          .post(
            `${AuthService.BASE_URL}/api/v1/auth/refresh`,
            {},
            { withCredentials: true, skipAuthRefresh: true, timeout: AUTH_REFRESH_TIMEOUT_MS },
          )
          .then((response) => {
            if (version !== AuthService.sessionVersion || AuthService.sessionChanges > 0) return false;
            AuthService.saveToken(response.data.token);
            return true;
          })
          .catch(() => {
            if (version === AuthService.sessionVersion && AuthService.sessionChanges === 0) AuthService.removeToken();
            return false;
          });
      };

      const guardedRequest =
        typeof navigator !== "undefined" && navigator.locks
          ? navigator.locks.request("investment-tracker-refresh", requestNewToken)
          : requestNewToken();

      refreshPromise = guardedRequest.finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  static async login(email, password, rememberMe = true) {
    const version = ++AuthService.sessionVersion;
    AuthService.sessionChanges += 1;
    try {
      if (refreshPromise) await refreshPromise;
      if (version !== AuthService.sessionVersion) throw new axios.CanceledError("Session changed");
      const response = await apiClient.post(
        `${AuthService.BASE_URL}/api/v1/auth/authenticate`,
        { email, password, rememberMe },
        { withCredentials: true, skipAuthRefresh: true },
      );
      const { token } = response.data;
      if (version !== AuthService.sessionVersion) throw new axios.CanceledError("Session changed");
      AuthService.saveToken(token);
      return response.data;
    } catch (error) {
      throw apiError(error);
    } finally {
      AuthService.sessionChanges -= 1;
    }
  }

  static async logout() {
    const version = ++AuthService.sessionVersion;
    AuthService.sessionChanges += 1;
    try {
      if (refreshPromise) await refreshPromise;
      if (version !== AuthService.sessionVersion) return;
      await apiClient.post(
        `${AuthService.BASE_URL}/api/v1/auth/logout`,
        {},
        { withCredentials: true, skipAuthRefresh: true },
      );
    } finally {
      if (version === AuthService.sessionVersion) AuthService.removeToken();
      AuthService.sessionChanges -= 1;
    }
  }

  static async register(userData) {
    return apiData(apiClient.post(`${AuthService.BASE_URL}/api/v1/auth/register`, userData));
  }

  static async confirm(code, email) {
    return apiData(apiClient.post(
      `${AuthService.BASE_URL}/api/v1/auth/activate-account`,
      { email: email.trim(), code },
    ));
  }

  static async resendActivation(email) {
    return apiData(apiClient.post(
      `${AuthService.BASE_URL}/api/v1/auth/resend-activation`,
      { email: email.trim() },
    ));
  }

  static async forgotPassword(email) {
    return apiData(apiClient.post(`${AuthService.BASE_URL}/api/v1/auth/forgot-password`, { email }));
  }

  static async resetPassword(email, code, newPassword) {
    const version = AuthService.sessionVersion;
    try {
      const response = await apiClient.post(
        `${AuthService.BASE_URL}/api/v1/auth/reset-password`,
        { email, code, newPassword },
      );
      if (version === AuthService.sessionVersion) AuthService.removeToken();
      return response.data;
    } catch (error) {
      throw apiError(error);
    }
  }

  static async changePassword(passwordsData) {
    const version = AuthService.sessionVersion;
    try {
      const response = await apiClient.patch(
        `${AuthService.BASE_URL}/api/v1/auth/change-password`,
        passwordsData,
      );
      if (version !== AuthService.sessionVersion) throw new axios.CanceledError("Session changed");
      AuthService.removeToken();
      return response.data;
    } catch (error) {
      throw apiError(error);
    }
  }

  static async changeUsername(newUsername) {
    const version = AuthService.sessionVersion;
    try {
      const response = await apiClient.patch(
        `${AuthService.BASE_URL}/api/v1/auth/change-username`,
        { newUsername },
      );
      if (version !== AuthService.sessionVersion) throw new axios.CanceledError("Session changed");
      if (response.data && response.data.token) {
        AuthService.saveToken(response.data.token);
      }
      return response.data;
    } catch (error) {
      throw apiError(error);
    }
  }

}

configureSessionInterceptors(AuthService);

export default AuthService;
