import axios from "axios";
import CookieService from "./CookieService";

class UserService {
  static BASE_URL = "http://localhost:8080";
  static TOKEN_KEY = "user_token";

  static saveToken(token) {
    CookieService.setCookie(UserService.TOKEN_KEY, token, {
      secure: true,
      sameSite: "strict",
    });
  }

  static getToken() {
    return CookieService.getCookie(UserService.TOKEN_KEY);
  }

  static removeToken() {
    CookieService.removeCookie(UserService.TOKEN_KEY);
  }

  static async login(email, password) {
    try {
      const response = await axios.post(
        `${UserService.BASE_URL}/api/v1/auth/authenticate`,
        { email, password },
      );
      const { token } = response.data;
      UserService.saveToken(token);
      return response.data;
    } catch (error) {
      throw error.response.data;
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

  static async getUserItems(page = 0, size = 10) {
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
          },
        },
      );
      return response.data;
    } catch (error) {
      throw error.response.data;
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
        `${UserService.BASE_URL}/cs-items/search`,
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
}

export default UserService;
