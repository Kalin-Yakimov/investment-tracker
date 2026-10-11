import { apiClient, apiData } from "./ApiClient";

export default class CatalogueService {
  static searchCsItems(query, config = {}) {
    return apiData(apiClient.get("/api/cs-items/search", { params: { query }, ...config }));
  }
}
