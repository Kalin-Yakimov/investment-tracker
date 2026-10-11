import PriceService from "../service/PriceService";
import { useCallback, useEffect, useMemo, useState } from "react";
import useCatalogueSearch from "./useCatalogueSearch";
import usePriceHistories from "./usePriceHistories";
import useRequestScope from "./useRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import { buildDailyPricePoints as getItemPoints, getPeriodChange, sortPriceHistoryNewestFirst } from "../utils/priceHistory";

export default function usePriceTracker() {
  const scope = useRequestScope();
  const [query, setQuery] = useState("");
  const [selectedSearchItem, setSelectedSearchItem] = useState(null);
  const searchResults = useCatalogueSearch(query, !selectedSearchItem);
  const [error, setError] = useState("");
  const [trackedItems, setTrackedItems] = useState([]);
  const priceHistories = usePriceHistories(trackedItems);

  const [selectedTimeframe, setSelectedTimeframe] = useState("7D");
  const [detailItem, setDetailItem] = useState(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState(null);

  // Auto-dismiss error message after 5 seconds
  useEffect(() => {
    if (!error) return;
    const timer = setTimeout(() => {
      setError("");
    }, 5000);
    return () => clearTimeout(timer);
  }, [error]);

  const fetchTrackedItems = useCallback(async () => {
    const request = scope.begin("tracked");
    if (!request) return;
    try {
      const data = await PriceService.getTrackedItems({ signal: request.signal });
      if (!request.isCurrent()) return;
      const list = Array.isArray(data) ? data : [];
      // Sort so newly added items (highest ID) appear on top
      list.sort((a, b) => (b.id || 0) - (a.id || 0));
      setTrackedItems(list);
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) {
        console.error("Failed to load tracked items:", err);
        setError("Failed to load tracked items from backend.");
      }
    } finally {
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
    setSelectedSearchItem(null);
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

      await PriceService.addTrackedItem(payload);
      if (!request.isCurrent()) return;
      await fetchTrackedItems();
    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      console.error("Failed to add item:", err);
      const serverErr = err.error || err.response?.data?.error;
      if (serverErr && (serverErr.includes("already tracking") || serverErr.includes("already tracked"))) {
        setError("Item is already tracked.");
      } else {
        setError(serverErr || err.message || "Failed to add item to tracker.");
      }
    } finally { request.finish(); }
  };

  const handleDeleteItem = async (itemId) => {
    const request = scope.begin("tracked-change", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tracked");
    try {
      await PriceService.deleteTrackedItem(itemId);
      if (!request.isCurrent()) return;
      setTrackedItems((prev) => prev.filter((i) => i.id !== itemId));
      setDetailItem((previous) => previous?.id === itemId ? null : previous);
    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      console.error("Failed to delete item:", err);
      setError("Failed to delete item.");
    } finally { request.finish(); }
  };

  const handleToggleBookmark = async (itemId) => {
    const request = scope.begin("tracked-change", { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("tracked");
    try {
      const res = await PriceService.toggleBookmark(itemId);
      if (!request.isCurrent()) return;
      setTrackedItems((prev) =>
        prev.map((i) => (i.id === itemId ? { ...i, showInDashboard: res.bookmarked } : i))
      );
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) console.error("Failed to toggle bookmark:", err);
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
    scope, query, setQuery, setSelectedSearchItem, searchResults, error,
    trackedItems, selectedTimeframe, setSelectedTimeframe,
    detailItem, setDetailItem, deleteConfirmId, setDeleteConfirmId,
    itemPointsMap, itemTrendsMap, handleSelectSearchItem,
    handleDeleteItem, handleToggleBookmark,
    detailPoints, detailTrend, detailHistoryList,
  };
}
