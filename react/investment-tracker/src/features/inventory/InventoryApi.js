import { apiClient, apiData } from "../../shared/api/ApiClient";

export default class InventoryApi {
  static calculateSteamInventory(steamUrl, config = {}) {
    return apiData(apiClient.post("/api/v1/inventory/calculate", { steamUrl }, { signal: config.signal }));
  }
}
