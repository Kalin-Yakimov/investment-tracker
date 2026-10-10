import axios from "axios";
import { clearInventoryCalculatorSession } from "./InventoryCalculatorSession";
import { AUTH_REFRESH_TIMEOUT_MS } from "../config/uiConfig";

let refreshPromise = null;
let refreshVersion = null;

class UserService {
  static BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";
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
        return axios
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
      const response = await axios.post(
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
      await axios.post(
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
      const response = await axios.post(
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
      const response = await axios.post(
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
      const response = await axios.post(
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
      const response = await axios.post(
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
      const response = await axios.post(
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
      const token = UserService.getToken();
      const response = await axios.patch(
        `${UserService.BASE_URL}/api/v1/auth/change-password`,
        passwordsData,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
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
      const token = UserService.getToken();
      const response = await axios.patch(
        `${UserService.BASE_URL}/api/v1/auth/change-username`,
        { newUsername },
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
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

  static async createContactTicket(email, message) {
    try {
      const response = await axios.post(
        `${UserService.BASE_URL}/api/contact`,
        { email, message },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async getUserItems(page = 0, size = 10, filter = "newest_date", config = {}) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/get-user-items`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          params: {
            page,
            size,
            filter,
          },
          signal: config.signal,
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async getPortfolioSummary(config = {}) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/portfolio-summary`,
        {
          signal: config.signal,
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async getItemByName(name, page = 0, size = 10, config = {}) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(`${UserService.BASE_URL}/item/history`, {
        signal: config.signal,
        headers: {
          Authorization: `Bearer ${token}`,
        },
        params: {
          name,
          page,
          size,
        },
      });
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async searchCsItems(query, config = {}) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/api/cs-items/search`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          params: {
            query,
          },
          ...config,
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async getTrackedItems(config = {}) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(`${UserService.BASE_URL}/api/items`, {
        signal: config.signal,
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async validateSteamItem(marketHashName) {
    try {
      const response = await axios.post(
        `${UserService.BASE_URL}/api/prices/validate`,
        { marketHashName },
        {
          headers: {
            "Content-Type": "application/json",
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async addSteamTrackerItem(itemData) {
    try {
      const token = UserService.getToken();
      const response = await axios.post(
        `${UserService.BASE_URL}/api/items`,
        itemData,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async addTrackedItem(itemData) {
    return UserService.addSteamTrackerItem(itemData);
  }

  static async getPriceHistoryByItem(itemId, config = {}) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/api/price-history/${itemId}`,
        {
          signal: config.signal,
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async getPriceHistory(itemId, config = {}) {
    return UserService.getPriceHistoryByItem(itemId, config);
  }

  static async toggleBookmark(itemId) {
    try {
      const token = UserService.getToken();
      const response = await axios.patch(
        `${UserService.BASE_URL}/api/items/${itemId}/bookmark`,
        {},
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async updatePrices(itemIds) {
    try {
      const response = await axios.post(
        `${UserService.BASE_URL}/api/prices/update`,
        { itemIds },
        {
          headers: {
            "Content-Type": "application/json",
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async deleteTrackedItem(itemId) {
    try {
      const token = UserService.getToken();
      const response = await axios.delete(
        `${UserService.BASE_URL}/api/items/${itemId}`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async calculateSteamInventory(steamUrl, config = {}) {
    try {
      const token = UserService.getToken();
      const headers = {};
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      const response = await axios.post(
        `${UserService.BASE_URL}/api/v1/inventory/calculate`,
        { steamUrl },
        { headers, signal: config.signal }
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }
}

axios.defaults.withCredentials = true;

axios.interceptors.request.use((config) => {
  const token = UserService.getToken();
  const url = config.url || "";
  let isBackendRequest = false;
  try {
    isBackendRequest = new URL(url, UserService.BASE_URL).origin === new URL(UserService.BASE_URL).origin;
  } catch { /* Invalid URLs must never receive credentials. */ }
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
    if (config._sessionVersion !== undefined && config._sessionVersion !== UserService.sessionVersion) {
      throw new axios.CanceledError("Session changed");
    }
    config._sessionVersion = UserService.sessionVersion;
    config.headers = config.headers || {};
    if (token) config.headers.Authorization = `Bearer ${token}`;
    else delete config.headers.Authorization;
  }
  return config;
});

axios.interceptors.response.use(
  (response) => {
    const version = response.config?._sessionVersion;
    if (version !== undefined && version !== UserService.sessionVersion) {
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
      (isSessionRequest && (originalRequest._sessionVersion !== UserService.sessionVersion || UserService.sessionChanges > 0))
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
      const refreshed = await UserService.refreshAccessToken();
      if (originalRequest.signal?.aborted || originalRequest._sessionVersion !== UserService.sessionVersion) {
        return Promise.reject(new axios.CanceledError("Session changed"));
      }
      if (refreshed) {
        originalRequest.headers = originalRequest.headers || {};
        originalRequest.headers.Authorization = `Bearer ${UserService.getToken()}`;
        return axios(originalRequest);
      }
    }

    if (status === 401 && isSessionRequest && !originalRequest?.skipAuthRefresh) {
      UserService.removeToken();
      const publicPaths = ["/login", "/registration", "/forgot-password", "/activate-account"];
      if (!publicPaths.includes(window.location.pathname)) {
        window.location.href = "/login?expired=true";
      }
    }
    return Promise.reject(error);
  },
);

export default UserService;
