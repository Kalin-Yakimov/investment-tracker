import PriceTrackerApi from "../PriceTrackerApi";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { requestErrorMessage } from "../../../shared/api/requestFeedback";
import usePriceHistories from "../../../shared/hooks/usePriceHistories";
import useRequestScope from "../../../shared/hooks/useRequestScope";
import { isRequestCanceled } from "../../../shared/utils/RequestScope";
import { buildDailyPricePoints as getItemPoints, getPeriodChange, sortPriceHistoryNewestFirst } from "../../../shared/utils/priceHistory";

export default function usePriceTracker() {
  const scope = useRequestScope();
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(true);
  const [trackedItems, setTrackedItems] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const requestedPage = useRef(0);
  const historyState = usePriceHistories(trackedItems);
  const priceHistories = historyState.histories;

  const [selectedTimeframe, setSelectedTimeframe] = useState("7D");
  const [detailSelection, setDetailItem] = useState(null);
  const detailItem = trackedItems.find(item => item.id === detailSelection?.id) || null;
  const [deleteConfirmId, setDeleteConfirmId] = useState(null);

  const fetchTrackedItems = useCallback(async (page = 0) => {
    const request = scope.begin("tracked");
    if (!request) return;
    requestedPage.current = page;
    setLoading(true); setLoadError("");
    try {
      const data = await PriceTrackerApi.getTrackedItems({ page, size: 20, signal: request.signal });
      if (!request.isCurrent()) return;
      if (!Array.isArray(data?.content)) throw new Error("Invalid tracked-item page");
      setTrackedItems(data.content);
      setCurrentPage(data.number);
      setTotalPages(data.totalPages);
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) {
        setLoadError(requestErrorMessage(err, "Tracked items could not be loaded."));
      }
    } finally {
      if (request.isCurrent()) setLoading(false);
      request.finish();
    }
  }, [scope]);

  useEffect(() => {
    fetchTrackedItems();
    return () => scope.cancel("tracked");
  }, [fetchTrackedItems, scope]);

  const itemPointsMap = useMemo(() => {
    const map = {};
    trackedItems.forEach((item) => {
      const history = priceHistories[item.id] || [];
      map[item.id] = getItemPoints(history, selectedTimeframe);
    });
    return map;
  }, [trackedItems, priceHistories, selectedTimeframe]);

  const itemTrendsMap = useMemo(() => {
    const map = {};
    trackedItems.forEach((item) => {
      const points = itemPointsMap[item.id] || [];
      map[item.id] = getPeriodChange(points);
    });
    return map;
  }, [trackedItems, itemPointsMap]);

  const handleSelectSearchItem = async (csItem) => {
    if (scope.isPending("tracked-change")) return;
    setQuery("");
    setError("");

    // Instant client-side check before API call
    const isAlreadyTracked = trackedItems.some(
      (i) => i.marketHashName === csItem.marketHashName || i.itemName === csItem.name
    );
    if (isAlreadyTracked) {
      setError("Item is already tracked.");
      return;
    }

    const request = scope.begin("tracked-change", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tracked");
    try {
      const payload = {
        itemName: csItem.name,
        marketHashName: csItem.marketHashName,
        csItemId: csItem.id,
        quantity: 1,
        showInDashboard: false,
      };

      await PriceTrackerApi.addTrackedItem(payload);
      if (!request.isCurrent()) return;
      await fetchTrackedItems(0);
    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      const serverErr = err.error || err.response?.data?.error;
      if (serverErr && (serverErr.includes("already tracking") || serverErr.includes("already tracked"))) {
        setError("Item is already tracked.");
      } else {
        setError(requestErrorMessage(err, "Failed to add item to tracker.", { mutation: true }));
      }
    } finally { request.finish(); }
  };

  const handleDeleteItem = async (itemId) => {
    const request = scope.begin("tracked-change", { replace: false, abortable: false });
    if (!request) return;
    setError("");
    scope.cancel("tracked");
    try {
      await PriceTrackerApi.deleteTrackedItem(itemId);
      if (!request.isCurrent()) return;
      setTrackedItems((prev) => prev.filter((i) => i.id !== itemId));
      setDetailItem((previous) => previous?.id === itemId ? null : previous);
      await fetchTrackedItems(trackedItems.length === 1 && currentPage > 0 ? currentPage - 1 : currentPage);
      return true;
    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      setError(requestErrorMessage(err, "Failed to delete item.", { mutation: true }));
      return false;
    } finally { request.finish(); }
  };

  const handleToggleBookmark = async (itemId) => {
    const request = scope.begin("tracked-change", { replace: false, abortable: false });
    if (!request) return;
    setError("");
    scope.cancel("tracked");
    try {
      const res = await PriceTrackerApi.toggleBookmark(itemId);
      if (!request.isCurrent()) return;
      setTrackedItems((prev) =>
        prev.map((i) => (i.id === itemId ? { ...i, ...res.item, showInDashboard: res.bookmarked } : i))
      );
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) setError(requestErrorMessage(err, "The bookmark could not be changed.", { mutation: true }));
    } finally { request.finish(); }
  };

  const detailPoints = useMemo(() => {
    if (!detailItem) return [];
    return getItemPoints(priceHistories[detailItem.id] || [], selectedTimeframe);
  }, [detailItem, priceHistories, selectedTimeframe]);

  const detailTrend = useMemo(() => {
    return getPeriodChange(detailPoints);
  }, [detailPoints]);

  const detailHistoryList = useMemo(() => {
    if (!detailItem) return [];
    return sortPriceHistoryNewestFirst(priceHistories[detailItem.id]);
  }, [detailItem, priceHistories]);
  return {
    scope, query, setQuery, error, loading, loadError, fetchTrackedItems, historyState,
    currentPage, totalPages, retryTrackedItems: () => fetchTrackedItems(requestedPage.current),
    trackedItems, selectedTimeframe, setSelectedTimeframe,
    detailItem, setDetailItem, deleteConfirmId, setDeleteConfirmId,
    itemPointsMap, itemTrendsMap, handleSelectSearchItem,
    handleDeleteItem, handleToggleBookmark,
    detailPoints, detailTrend, detailHistoryList,
  };
}
