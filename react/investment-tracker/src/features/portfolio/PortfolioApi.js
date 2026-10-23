import { apiClient, apiData } from "../../shared/api/ApiClient";

export default class PortfolioApi {
  static getUserItems(page = 0, size = 10, filter = "newest_date", config = {}) {
    return apiData(apiClient.get("/get-user-items", { params: { page, size, filter }, signal: config.signal }));
  }
  static getPortfolioSummary(config = {}) { return apiData(apiClient.get("/portfolio-summary", { signal: config.signal })); }
  static getItemByName(name, page = 0, size = 10, config = {}) {
    return apiData(apiClient.get("/item/history", { params: { name, page, size }, signal: config.signal }));
  }
  // Item forms historically receive Axios errors, so keep that existing contract.
  static async getItem(itemId, config = {}) { return (await apiClient.get(`/item/${itemId}`, { signal: config.signal })).data; }
  static async createItem(itemData) { return (await apiClient.post("/item", itemData)).data; }
  static async updateItem(itemId, itemData) { return (await apiClient.put(`/item/${itemId}`, itemData)).data; }
  static async deleteItem(itemId) { return (await apiClient.delete(`/item/${itemId}`)).data; }
}
