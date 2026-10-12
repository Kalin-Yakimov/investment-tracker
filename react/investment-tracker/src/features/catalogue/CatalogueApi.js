import { apiClient, apiData } from "../../shared/api/ApiClient";

export default class CatalogueApi {
  static searchCsItems(query, config = {}) {
    return apiData(apiClient.get("/api/cs-items/search", { params: { query }, ...config }));
  }
}
