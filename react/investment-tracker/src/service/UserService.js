import axios from "axios";
import { apiClient, API_BASE_URL, configureSessionInterceptors } from "./ApiClient";
import { clearInventoryCalculatorSession } from "./InventoryCalculatorSession";
import { AUTH_REFRESH_TIMEOUT_MS } from "../config/uiConfig";

let refreshPromise = null;
let refreshVersion = null;

class UserService {
  static BASE_URL = API_BASE_URL;
  static accessToken = null;
  static authListeners = new Set();
  static sessionVersion = 0;
  static sessionChanges = 0;

  static getSessionVersion() {
    return UserService.sessionVersion;
  }

  static saveToken(token) {
    const previousUser = UserService.getCurrentUser()?.id;
    UserService.accessToken = token || null;
    if (previousUser !== UserService.getCurrentUser()?.id) UserService.sessionVersion += 1;
    UserService.notifyAuthChanged();
  }

  static getToken() {
    return UserService.accessToken;
  }

  static removeToken() {
    UserService.sessionVersion += 1;
    UserService.accessToken = null;
    clearInventoryCalculatorSession();
    UserService.notifyAuthChanged();
  }

  static isAuthenticated() {
    return Boolean(UserService.accessToken);
  }

  static getCurrentUser() {
    try {
      const token = UserService.getToken();
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
    return UserService.getCurrentUser()?.authorities.includes(authority) || false;
  }

  static hasAnyAuthority(...authorities) {
    const currentAuthorities = UserService.getCurrentUser()?.authorities || [];
    return authorities.some((authority) => currentAuthorities.includes(authority));
  }

  static subscribe(listener) {
    UserService.authListeners.add(listener);
    return () => UserService.authListeners.delete(listener);
  }

  static notifyAuthChanged() {
    UserService.authListeners.forEach((listener) => listener());
  }

  static async initializeSession() {
    if (UserService.accessToken) return true;
    return UserService.refreshAccessToken();
  }

  static async refreshAccessToken() {
    if (UserService.sessionChanges > 0) return false;
    if (refreshPromise && refreshVersion !== UserService.sessionVersion) {
      await refreshPromise;
      return UserService.refreshAccessToken();
    }
    if (!refreshPromise) {
      const version = UserService.sessionVersion;
      refreshVersion = version;
      const requestNewToken = () => {
        if (version !== UserService.sessionVersion || UserService.sessionChanges > 0) return Promise.resolve(false);
        return apiClient
          .post(
            `${UserService.BASE_URL}/api/v1/auth/refresh`,
            {},
            { withCredentials: true, skipAuthRefresh: true, timeout: AUTH_REFRESH_TIMEOUT_MS },
          )
          .then((response) => {
            if (version !== UserService.sessionVersion || UserService.sessionChanges > 0) return false;
            UserService.saveToken(response.data.token);
            return true;
          })
          .catch(() => {
            if (version === UserService.sessionVersion && UserService.sessionChanges === 0) UserService.removeToken();
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
    const version = ++UserService.sessionVersion;
    UserService.sessionChanges += 1;
    try {
      if (refreshPromise) await refreshPromise;
      if (version !== UserService.sessionVersion) throw new axios.CanceledError("Session changed");
      const response = await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/authenticate`,
        { email, password, rememberMe },
        { withCredentials: true, skipAuthRefresh: true },
      );
      const { token } = response.data;
      if (version !== UserService.sessionVersion) throw new axios.CanceledError("Session changed");
      UserService.saveToken(token);
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    } finally {
      UserService.sessionChanges -= 1;
    }
  }

  static async logout() {
    const version = ++UserService.sessionVersion;
    UserService.sessionChanges += 1;
    try {
      if (refreshPromise) await refreshPromise;
      if (version !== UserService.sessionVersion) return;
      await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/logout`,
        {},
        { withCredentials: true, skipAuthRefresh: true },
      );
    } finally {
      if (version === UserService.sessionVersion) UserService.removeToken();
      UserService.sessionChanges -= 1;
    }
  }

  static async register(userData) {
    try {
      const response = await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/register`,
        userData,
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async confirm(code, email) {
    try {
      const response = await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/activate-account`,
        { email: email.trim(), code },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async resendActivation(email) {
    try {
      const response = await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/resend-activation`,
        { email: email.trim() },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async forgotPassword(email) {
    try {
      const response = await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/forgot-password`,
        { email },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async resetPassword(email, code, newPassword) {
    const version = UserService.sessionVersion;
    try {
      const response = await apiClient.post(
        `${UserService.BASE_URL}/api/v1/auth/reset-password`,
        { email, code, newPassword },
      );
      if (version === UserService.sessionVersion) UserService.removeToken();
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async changePassword(passwordsData) {
    const version = UserService.sessionVersion;
    try {
      const response = await apiClient.patch(
        `${UserService.BASE_URL}/api/v1/auth/change-password`,
        passwordsData,
      );
      if (version !== UserService.sessionVersion) throw new axios.CanceledError("Session changed");
      UserService.removeToken();
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async changeUsername(newUsername) {
    const version = UserService.sessionVersion;
    try {
      const response = await apiClient.patch(
        `${UserService.BASE_URL}/api/v1/auth/change-username`,
        { newUsername },
      );
      if (version !== UserService.sessionVersion) throw new axios.CanceledError("Session changed");
      if (response.data && response.data.token) {
        UserService.saveToken(response.data.token);
      }
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

}

configureSessionInterceptors(UserService);

export default UserService;
