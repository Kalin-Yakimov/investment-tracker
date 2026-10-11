import { apiClient, apiData } from "./ApiClient";

export default class ContactService {
  static createContactTicket(email, message) { return apiData(apiClient.post("/api/contact", { email, message })); }
}
