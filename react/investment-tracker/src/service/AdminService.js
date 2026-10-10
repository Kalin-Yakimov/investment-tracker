import axios from "axios";
import UserService from "./UserService";

class AdminService {
  static BASE_URL = `${UserService.BASE_URL}/api/admin`;

  static async getOverview(config = {}) {
    return (await axios.get(`${AdminService.BASE_URL}/overview`, { signal: config.signal })).data;
  }

  static async searchUsers(query = "", page = 0, size = 20, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/users`, {
        params: { query, page, size },
        signal: config.signal,
      })
    ).data;
  }

  static supportHeaders(reason) {
    return { "X-Support-Reason": reason };
  }

  static async getUserDetails(userId, reason, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/users/${userId}`, {
        signal: config.signal,
        headers: AdminService.supportHeaders(reason),
      })
    ).data;
  }

  static async getPortfolio(userId, reason, page = 0, size = 20, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/users/${userId}/portfolio`, {
        signal: config.signal,
        headers: AdminService.supportHeaders(reason),
        params: { page, size },
      })
    ).data;
  }

  static async getTrackedItems(userId, reason, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/users/${userId}/tracked-items`, {
        signal: config.signal,
        headers: AdminService.supportHeaders(reason),
      })
    ).data;
  }

  static async getSessions(userId, reason, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/users/${userId}/sessions`, {
        signal: config.signal,
        headers: AdminService.supportHeaders(reason),
      })
    ).data;
  }

  static async getActivity(userId, reason, page = 0, size = 30, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/users/${userId}/activity`, {
        signal: config.signal,
        headers: AdminService.supportHeaders(reason),
        params: { page, size },
      })
    ).data;
  }

  static async getAudit(page = 0, size = 25, config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/audit`, {
        params: { page, size },
        signal: config.signal,
      })
    ).data;
  }

  static async getTickets(status = "", page = 0, size = 15, config = {}) {
    const params = { page, size };
    if (status) params.status = status;
    return (
      await axios.get(`${AdminService.BASE_URL}/tickets`, { params, signal: config.signal })
    ).data;
  }

  static async getNewTicketCount(config = {}) {
    return (
      await axios.get(`${AdminService.BASE_URL}/tickets/new-count`, { signal: config.signal })
    ).data.count;
  }

  static async updateTicketStatus(ticketId, status) {
    return (
      await axios.patch(`${AdminService.BASE_URL}/tickets/${ticketId}/status`, { status })
    ).data;
  }

  static async removeTicket(ticketId) {
    return (
      await axios.delete(`${AdminService.BASE_URL}/tickets/${ticketId}`)
    ).data;
  }

  static async setSupportRole(userId, enabled, reason) {
    return (
      await axios.patch(`${AdminService.BASE_URL}/users/${userId}/support-role`, {
        enabled,
        reason,
      })
    ).data;
  }

  static async setAccountLocked(userId, locked, reason) {
    return (
      await axios.patch(`${AdminService.BASE_URL}/users/${userId}/locked`, {
        locked,
        reason,
      })
    ).data;
  }

  static async revokeSessions(userId, reason) {
    return (
      await axios.post(`${AdminService.BASE_URL}/users/${userId}/revoke-sessions`, {
        reason,
      })
    ).data;
  }

  static async resendActivation(userId, reason) {
    return (
      await axios.post(`${AdminService.BASE_URL}/users/${userId}/resend-activation`, {
        reason,
      })
    ).data;
  }

  static async startCatalogueSync(reason) {
    return (
      await axios.post(`${AdminService.BASE_URL}/system/catalogue-sync`, { reason })
    ).data;
  }

  static async startPriceUpdate(reason) {
    return (
      await axios.post(`${AdminService.BASE_URL}/system/price-update`, { reason })
    ).data;
  }
}

export default AdminService;
