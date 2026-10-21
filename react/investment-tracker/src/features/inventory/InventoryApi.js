import { apiClient, apiData } from "../../shared/api/ApiClient";
import { INVENTORY_REQUEST_TIMEOUT_MS } from "../../config/uiConfig";

export default class InventoryApi {
  static calculateSteamInventory(steamUrl, config = {}) {
    return apiData(apiClient.post("/api/v1/inventory/calculate", { steamUrl }, { signal: config.signal, timeout: INVENTORY_REQUEST_TIMEOUT_MS }));
  }
}
