import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import SupportPanelApi from "../SupportPanelApi";
import AuthService from "../../auth/AuthService";
import useRequestScope from "../../../shared/hooks/useRequestScope";
import { isRequestCanceled } from "../../../shared/utils/RequestScope";
import { SUPPORT_JOB_POLL_MS, SUPPORT_TICKET_POLL_MS } from "../../../config/uiConfig";
import { errorMessage } from "../utils/supportFormat";

const EMPTY_PAGE = { content: [], number: 0, totalPages: 0, totalElements: 0 };

export default function useSupportPanel() {
  const scope = useRequestScope();
  const currentUser = AuthService.getCurrentUser();
  const isAdmin = AuthService.hasAuthority("ADMIN");
  const [section, setSection] = useState("overview");
  const [overview, setOverview] = useState(null);
  const [users, setUsers] = useState(EMPTY_PAGE);
  const [search, setSearch] = useState("");
  const [userPage, setUserPage] = useState(0);
  const [selectedUser, setSelectedUser] = useState(null);
  const [userDetails, setUserDetails] = useState(null);
  const [supportReason, setSupportReason] = useState("");
  const [userTab, setUserTab] = useState("profile");
  const userTabRef = useRef("profile");
  const [portfolio, setPortfolio] = useState(EMPTY_PAGE);
  const [trackedItems, setTrackedItems] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [activity, setActivity] = useState(EMPTY_PAGE);
  const [audit, setAudit] = useState(EMPTY_PAGE);
  const [tickets, setTickets] = useState(EMPTY_PAGE);
  const [ticketStatus, setTicketStatus] = useState("");
  const [newTicketCount, setNewTicketCount] = useState(0);
  const [systemReason, setSystemReason] = useState("");
  const loading = ["users", "audit", "tickets", "details", "tab", "refresh-selected", "user-action", "system-action", "ticket-action"]
    .some((key) => scope.isPending(key));
  const [notice, setNotice] = useState(null);

  const showNotice = useCallback((type, text) => {
    setNotice({ type, text });
  }, []);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(() => setNotice(null), 5000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const read = useCallback(async (key, operation, apply, { replace = true, silent = false } = {}) => {
    const request = scope.begin(key, { replace });
    if (!request) return;
    try {
      const result = await operation({ signal: request.signal });
      if (request.isCurrent()) apply(result);
    } catch (error) {
      if (request.isCurrent() && !silent && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  }, [scope, showNotice]);

  const loadOverview = useCallback((replace = false) =>
    read("overview", (config) => SupportPanelApi.getOverview(config), setOverview, { replace }),
  [read]);

  const loadUsers = useCallback(
    (page = 0, query = "") =>
      read("users", (config) => SupportPanelApi.searchUsers(query, page, 20, config), (result) => {
        setUsers(result);
        setUserPage(page);
      }),
    [read],
  );

  const loadAudit = useCallback(
    (page = 0) => {
      if (!isAdmin) return;
      return read("audit", (config) => SupportPanelApi.getAudit(page, 25, config), setAudit);
    },
    [isAdmin, read],
  );

  const loadTickets = useCallback(
    (page = 0, status = "") =>
      read("tickets", (config) => SupportPanelApi.getTickets(status, page, 15, config), setTickets),
    [read],
  );

  const loadNewTicketCount = useCallback(() =>
    read("ticket-count", (config) => SupportPanelApi.getNewTicketCount(config), setNewTicketCount, { replace: false, silent: true }),
  [read]);

  useEffect(() => {
    loadUsers(0, "");
    loadNewTicketCount();
    return () => { scope.cancel("users"); scope.cancel("ticket-count"); };
  }, [loadUsers, loadNewTicketCount, scope]);

  useEffect(() => {
    const intervalId = window.setInterval(loadNewTicketCount, SUPPORT_TICKET_POLL_MS);
    return () => window.clearInterval(intervalId);
  }, [loadNewTicketCount]);

  useEffect(() => {
    if (section === "audit") loadAudit(0);
    if (section === "tickets") loadTickets(0, ticketStatus);
    if (section === "overview" || section === "system") loadOverview();
    return () => {
      scope.cancel("audit");
      scope.cancel("tickets");
      scope.cancel("ticket-action");
    };
  }, [section, ticketStatus, loadAudit, loadOverview, loadTickets, scope]);

  useEffect(() => {
    const hasRunningJob =
      overview?.catalogueSync?.status === "RUNNING" ||
      overview?.priceUpdate?.status === "RUNNING";
    if (!hasRunningJob) return undefined;

    const intervalId = window.setInterval(loadOverview, SUPPORT_JOB_POLL_MS);
    return () => window.clearInterval(intervalId);
  }, [overview?.catalogueSync?.status, overview?.priceUpdate?.status, loadOverview]);

  const selectUser = (user) => {
    ["details", "tab", "refresh-selected", "user-action"].forEach(scope.cancel);
    setSelectedUser(user);
    setUserDetails(null);
    setSupportReason("");
    setUserTab("profile");
    userTabRef.current = "profile";
    setPortfolio(EMPTY_PAGE);
    setTrackedItems([]);
    setSessions([]);
    setActivity(EMPTY_PAGE);
  };

  const openSupportView = async () => {
    if (!selectedUser || supportReason.trim().length < 5) {
      showNotice("error", "Enter a clear support reason of at least 5 characters.");
      return;
    }
    scope.cancel("tab");
    await read("details", (config) => SupportPanelApi.getUserDetails(selectedUser.id, supportReason.trim(), config), (details) => {
      setUserDetails(details);
      setUserTab("profile");
      userTabRef.current = "profile";
    });
  };

  const loadUserTab = async (tab, page = 0) => {
    if (!userDetails || !selectedUser) return;
    setUserTab(tab);
    userTabRef.current = tab;
    scope.cancel("tab");
    if (tab === "portfolio") {
      await read("tab", (config) => SupportPanelApi.getPortfolio(selectedUser.id, supportReason, page, 20, config), setPortfolio);
    } else if (tab === "tracked") {
      await read("tab", (config) => SupportPanelApi.getTrackedItems(selectedUser.id, supportReason, config), setTrackedItems);
    } else if (tab === "sessions") {
      await read("tab", (config) => SupportPanelApi.getSessions(selectedUser.id, supportReason, config), setSessions);
    } else if (tab === "activity") {
      await read("tab", (config) => SupportPanelApi.getActivity(selectedUser.id, supportReason, page, 30, config), setActivity);
    }
  };

  const refreshSelectedUser = async () => {
    if (!selectedUser || !supportReason) return;
    await read("refresh-selected", (config) => SupportPanelApi.getUserDetails(selectedUser.id, supportReason, config), (details) => {
      setUserDetails(details);
      setSelectedUser(details.user);
    });
  };

  const performUserAction = async (action, confirmation) => {
    if (!selectedUser || !supportReason) return;
    if (scope.isPending("user-action")) return;
    if (confirmation && !window.confirm(confirmation)) return;
    const request = scope.begin("user-action", { replace: false, abortable: false });
    if (!request) return;
    try {
      const result = await action();
      if (!request.isCurrent()) return;
      showNotice("success", result.message);
      await refreshSelectedUser();
      if (!request.isCurrent()) return;
      await Promise.all([loadUsers(userPage, search), loadOverview()]);
      if (!request.isCurrent()) return;
      if (userTabRef.current === "sessions") await loadUserTab("sessions");
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const runSystemAction = async (action) => {
    if (systemReason.trim().length < 5) {
      showNotice("error", "Enter a reason of at least 5 characters.");
      return;
    }
    const request = scope.begin("system-action", { replace: false, abortable: false });
    if (!request) return;
    try {
      const result = await action(systemReason.trim());
      if (!request.isCurrent()) return;
      showNotice("success", result.message);
      setSystemReason("");
      await loadOverview(true);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const changeTicketStatus = async (ticketId, status) => {
    const request = scope.begin("ticket-action", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tickets");
    try {
      await SupportPanelApi.updateTicketStatus(ticketId, status);
      if (!request.isCurrent()) return;
      await Promise.all([
        loadTickets(tickets.number, ticketStatus),
        loadNewTicketCount(),
      ]);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const removeTicket = async (ticketId) => {
    if (scope.isPending("ticket-action")) return;
    if (!window.confirm("Remove this contact request permanently?")) return;
    const request = scope.begin("ticket-action", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tickets");
    try {
      const result = await SupportPanelApi.removeTicket(ticketId);
      if (!request.isCurrent()) return;
      showNotice("success", result.message);
      const targetPage = tickets.content.length === 1 && tickets.number > 0
        ? tickets.number - 1
        : tickets.number;
      await Promise.all([
        loadTickets(targetPage, ticketStatus),
        loadNewTicketCount(),
      ]);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) showNotice("error", errorMessage(error));
    } finally {
      request.finish();
    }
  };

  const navigation = useMemo(
    () => [
      ["overview", "Overview"],
      ["tickets", "Contact requests"],
      ["users", "User support"],
      ["system", "Catalogue & prices"],
      ...(isAdmin ? [["audit", "Audit log"]] : []),
    ],
    [isAdmin],
  );

  return { scope, isAdmin, section, setSection, overview, users, search, setSearch, selectedUser, selectUser, supportReason, setSupportReason, openSupportView, userDetails, userTab, loadUserTab, portfolio, trackedItems, sessions, activity, currentUser, performUserAction, loadUsers, tickets, ticketStatus, setTicketStatus, loadTickets, changeTicketStatus, removeTicket, systemReason, setSystemReason, runSystemAction, audit, loadAudit, navigation, notice, loading, newTicketCount };
}
