import axios from "axios";
import { clearInventoryCalculatorSession } from "./InventoryCalculatorSession";

let refreshPromise = null;

class UserService {
  static BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";
  static accessToken = null;
  static authListeners = new Set();

  static saveToken(token) {
    UserService.accessToken = token || null;
    UserService.notifyAuthChanged();
  }

  static getToken() {
    return UserService.accessToken;
  }

  static removeToken() {
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
    if (!refreshPromise) {
      const requestNewToken = () =>
        axios
          .post(
            `${UserService.BASE_URL}/api/v1/auth/refresh`,
            {},
            { withCredentials: true, skipAuthRefresh: true },
          )
          .then((response) => {
            UserService.saveToken(response.data.token);
            return true;
          })
          .catch(() => {
            UserService.removeToken();
            return false;
          });

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
    try {
      const response = await axios.post(
        `${UserService.BASE_URL}/api/v1/auth/authenticate`,
        { email, password, rememberMe },
        { withCredentials: true, skipAuthRefresh: true },
      );
      const { token } = response.data;
      UserService.saveToken(token);
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async logout() {
    try {
      await axios.post(
        `${UserService.BASE_URL}/api/v1/auth/logout`,
        {},
        { withCredentials: true },
      );
    } finally {
      UserService.removeToken();
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
      throw error.response.data;
    }
  }

  static async confirm(token) {
    try {
      const response = await axios.get(
        `${UserService.BASE_URL}/api/v1/auth/activate-account`,
        { params: { token } },
      );
      return response.data;
    } catch (error) {
      throw error.response.data;
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
    try {
      const response = await axios.post(
        `${UserService.BASE_URL}/api/v1/auth/reset-password`,
        { email, code, newPassword },
      );
      UserService.removeToken();
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async changePassword(passwordsData) {
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
      UserService.removeToken();
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async changeUsername(newUsername) {
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

  static async getUserItems(page = 0, size = 10, filter = "newest_date") {
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
        },
      );
      return response.data;
    } catch (error) {
      throw error.response?.data || error;
    }
  }

  static async getPortfolioSummary() {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/portfolio-summary`,
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

  static async getItemByName(name, page = 0, size = 10) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(`${UserService.BASE_URL}/item/history`, {
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
      throw error.response.data;
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

  static async getTrackedItems() {
    try {
      const token = UserService.getToken();
      const response = await axios.get(`${UserService.BASE_URL}/api/items`, {
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

  static async getPriceHistoryByItem(itemId) {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `${UserService.BASE_URL}/api/price-history/${itemId}`,
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

  static async getPriceHistory(itemId) {
    return UserService.getPriceHistoryByItem(itemId);
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

  static async calculateSteamInventory(steamUrl) {
    try {
      const token = UserService.getToken();
      const headers = {};
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      const response = await axios.post(
        `${UserService.BASE_URL}/api/v1/inventory/calculate`,
        { steamUrl },
        { headers }
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
  const isBackendRequest = url.startsWith(UserService.BASE_URL) || url.startsWith("/");
  const isPublicAuthRequest = [
    "/api/v1/auth/authenticate",
    "/api/v1/auth/register",
    "/api/v1/auth/activate-account",
    "/api/v1/auth/forgot-password",
    "/api/v1/auth/reset-password",
    "/api/v1/auth/refresh",
  ].some((path) => url.includes(path));

  if (token && isBackendRequest && !isPublicAuthRequest && !config.headers?.Authorization) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;

    if (
      status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.skipAuthRefresh
    ) {
      originalRequest._retry = true;
      const refreshed = await UserService.refreshAccessToken();
      if (refreshed) {
        originalRequest.headers = originalRequest.headers || {};
        originalRequest.headers.Authorization = `Bearer ${UserService.getToken()}`;
        return axios(originalRequest);
      }
    }

    if (status === 401 && !originalRequest?.skipAuthRefresh) {
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
