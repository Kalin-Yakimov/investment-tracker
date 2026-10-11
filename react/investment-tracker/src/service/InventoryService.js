import { apiClient, apiData } from "./ApiClient";

export default class InventoryService {
  static calculateSteamInventory(steamUrl, config = {}) {
    return apiData(apiClient.post("/api/v1/inventory/calculate", { steamUrl }, { signal: config.signal }));
  }
}
