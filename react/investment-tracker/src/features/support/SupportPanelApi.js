import { apiClient, API_BASE_URL } from "../../shared/api/ApiClient";

class SupportPanelApi {
  static BASE_URL = `${API_BASE_URL}/api/admin`;

  static async getOverview(config = {}) {
    return (await apiClient.get(`${SupportPanelApi.BASE_URL}/overview`, { signal: config.signal })).data;
  }

  static async searchUsers(query = "", page = 0, size = 20, config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/users`, {
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
      await apiClient.get(`${SupportPanelApi.BASE_URL}/users/${userId}`, {
        signal: config.signal,
        headers: SupportPanelApi.supportHeaders(reason),
      })
    ).data;
  }

  static async getPortfolio(userId, reason, page = 0, size = 20, config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/users/${userId}/portfolio`, {
        signal: config.signal,
        headers: SupportPanelApi.supportHeaders(reason),
        params: { page, size },
      })
    ).data;
  }

  static async getTrackedItems(userId, reason, config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/users/${userId}/tracked-items`, {
        signal: config.signal,
        headers: SupportPanelApi.supportHeaders(reason),
      })
    ).data;
  }

  static async getSessions(userId, reason, config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/users/${userId}/sessions`, {
        signal: config.signal,
        headers: SupportPanelApi.supportHeaders(reason),
      })
    ).data;
  }

  static async getActivity(userId, reason, page = 0, size = 30, config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/users/${userId}/activity`, {
        signal: config.signal,
        headers: SupportPanelApi.supportHeaders(reason),
        params: { page, size },
      })
    ).data;
  }

  static async getAudit(page = 0, size = 25, config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/audit`, {
        params: { page, size },
        signal: config.signal,
      })
    ).data;
  }

  static async getTickets(status = "", page = 0, size = 15, config = {}) {
    const params = { page, size };
    if (status) params.status = status;
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/tickets`, { params, signal: config.signal })
    ).data;
  }

  static async getNewTicketCount(config = {}) {
    return (
      await apiClient.get(`${SupportPanelApi.BASE_URL}/tickets/new-count`, { signal: config.signal })
    ).data.count;
  }

  static async updateTicketStatus(ticketId, status) {
    return (
      await apiClient.patch(`${SupportPanelApi.BASE_URL}/tickets/${ticketId}/status`, { status })
    ).data;
  }

  static async removeTicket(ticketId) {
    return (
      await apiClient.delete(`${SupportPanelApi.BASE_URL}/tickets/${ticketId}`)
    ).data;
  }

  static async setSupportRole(userId, enabled, reason) {
    return (
      await apiClient.patch(`${SupportPanelApi.BASE_URL}/users/${userId}/support-role`, {
        enabled,
        reason,
      })
    ).data;
  }

  static async setAccountLocked(userId, locked, reason) {
    return (
      await apiClient.patch(`${SupportPanelApi.BASE_URL}/users/${userId}/locked`, {
        locked,
        reason,
      })
    ).data;
  }

  static async revokeSessions(userId, reason) {
    return (
      await apiClient.post(`${SupportPanelApi.BASE_URL}/users/${userId}/revoke-sessions`, {
        reason,
      })
    ).data;
  }

  static async resendActivation(userId, reason) {
    return (
      await apiClient.post(`${SupportPanelApi.BASE_URL}/users/${userId}/resend-activation`, {
        reason,
      })
    ).data;
  }

  static async startCatalogueSync(reason) {
    return (
      await apiClient.post(`${SupportPanelApi.BASE_URL}/system/catalogue-sync`, { reason })
    ).data;
  }

  static async startPriceUpdate(reason) {
    return (
      await apiClient.post(`${SupportPanelApi.BASE_URL}/system/price-update`, { reason })
    ).data;
  }
}

export default SupportPanelApi;
