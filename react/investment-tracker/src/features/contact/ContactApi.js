import { apiClient, apiData } from "../../shared/api/ApiClient";

export default class ContactApi {
  static createContactTicket(email, message) { return apiData(apiClient.post("/api/contact", { email, message })); }
}
