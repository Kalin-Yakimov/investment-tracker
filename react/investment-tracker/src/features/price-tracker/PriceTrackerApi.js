import { apiClient, apiData } from "../../shared/api/ApiClient";

export default class PriceTrackerApi {
  static getTrackedItems({ page = 0, size = 20, dashboard = false, signal } = {}) {
    return apiData(apiClient.get("/api/items", { params: { page, size, dashboard }, signal }));
  }
  static addTrackedItem(itemData) { return apiData(apiClient.post("/api/items", itemData)); }
  static getPriceHistory(itemId, config = {}) { return apiData(apiClient.get(`/api/price-history/${itemId}`, { signal: config.signal })); }
  static toggleBookmark(itemId) { return apiData(apiClient.patch(`/api/items/${itemId}/bookmark`, {})); }
  static deleteTrackedItem(itemId) { return apiData(apiClient.delete(`/api/items/${itemId}`)); }
}
