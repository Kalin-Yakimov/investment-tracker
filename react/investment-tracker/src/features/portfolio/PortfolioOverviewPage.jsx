import PortfolioApi from "./PortfolioApi";
import PriceTrackerApi from "../price-tracker/PriceTrackerApi";
import React, { useEffect, useState, useCallback, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import useRequestScope from "../../shared/hooks/useRequestScope";
import usePriceHistories from "../../shared/hooks/usePriceHistories";
import { isRequestCanceled } from "../../shared/utils/RequestScope";
import PortfolioHistorySection from "./components/PortfolioHistorySection";
import PinnedMarketItemsSection from "./components/PinnedMarketItemsSection";
import "./styles/PortfolioOverviewPage.css";
import "../price-tracker/styles/PriceTrackerPage.css";
export default function PortfolioOverviewPage() {
  const scope = useRequestScope();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [itemsPerPage] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const navigate = useNavigate();

  // Portfolio Filtering & Sorting State
  const [filterSortOption, setFilterSortOption] = useState("newest_date");

  const sortedFilteredItems = useMemo(() => {
    let list = [...items];

    if (filterSortOption === "bought") {
      list = list.filter((i) => i.itemStatus === "BOUGHT");
    } else if (filterSortOption === "sold") {
      list = list.filter((i) => i.itemStatus === "SOLD");
    }

    list.sort((a, b) => {
      const aQty = Number(a.quantity || 1);
      const bQty = Number(b.quantity || 1);
      const aUnit = Number(a.price || 0);
      const bUnit = Number(b.price || 0);
      const aTotal = aUnit * aQty;
      const bTotal = bUnit * bQty;

      const parseDate = (d) => {
        if (!d) return 0;
        if (typeof d === "number") return d;
        if (typeof d === "string" && d.includes("/")) {
          const p = d.split("/");
          if (p.length === 3) return new Date(p[2], p[1] - 1, p[0]).getTime();
        }
        return new Date(d).getTime() || 0;
      };

      switch (filterSortOption) {
        case "oldest_date":
          return parseDate(a.date) - parseDate(b.date) || (a.id || 0) - (b.id || 0);
        case "newest_date":
          return parseDate(b.date) - parseDate(a.date) || (b.id || 0) - (a.id || 0);
        case "highest_quantity":
          return bQty - aQty;
        case "highest_total_price":
          return bTotal - aTotal;
        case "lowest_total_price":
          return aTotal - bTotal;
        case "highest_unit_price":
          return bUnit - aUnit;
        case "lowest_unit_price":
          return aUnit - bUnit;
        default:
          return 0;
      }
    });

    return list;
  }, [items, filterSortOption]);

  // Pinned Items State & Selected Timeframe
  const [selectedTimeframe, setSelectedTimeframe] = useState("7D");
  const [allPinnedItems, setAllPinnedItems] = useState([]);
  const [pinnedVisibleCount, setPinnedVisibleCount] = useState(10);
  const visiblePinnedItems = useMemo(() => allPinnedItems.slice(0, pinnedVisibleCount), [allPinnedItems, pinnedVisibleCount]);
  const pinnedHistories = usePriceHistories(visiblePinnedItems);
  const loaderRef = useRef(null);

  const loadItems = useCallback(
    async (page = 0, filter = filterSortOption) => {
      const request = scope.begin("portfolio");
      if (!request) return;
      try {
        setLoading(true);
        const result = await PortfolioApi.getUserItems(page, itemsPerPage, filter, { signal: request.signal });
        if (!request.isCurrent()) return;
        setItems(result.content || []);
        setTotalPages(result.totalPages || 0);
        setCurrentPage(page);
      } catch (error) {
        if (request.isCurrent() && !isRequestCanceled(error)) {
          console.error("Error loading items:", error);
          setItems([]);
        }
      } finally {
        if (request.isCurrent()) setLoading(false);
        request.finish();
      }
    },
    [itemsPerPage, filterSortOption, scope],
  );

  const loadPinnedItems = useCallback(async () => {
    const request = scope.begin("pinned");
    if (!request) return;
    try {
      const data = await PriceTrackerApi.getTrackedItems({ signal: request.signal });
      if (!request.isCurrent()) return;
      if (Array.isArray(data)) {
        const bookmarked = data.filter((item) => item.showInDashboard === true);
        bookmarked.sort((a, b) => (b.id || 0) - (a.id || 0));
        setAllPinnedItems(bookmarked);
      }
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) console.error("Failed to load pinned items:", err);
    } finally {
      request.finish();
    }
  }, [scope]);

  useEffect(() => {
    loadItems(0, filterSortOption);
    return () => scope.cancel("portfolio");
  }, [filterSortOption, loadItems, scope]);

  useEffect(() => {
    loadPinnedItems();
    return () => scope.cancel("pinned");
  }, [loadPinnedItems, scope]);

  // IntersectionObserver for Infinite Scroll on Pinned Items
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const target = entries[0];
        if (target.isIntersecting) {
          setPinnedVisibleCount((prev) => {
            if (prev < allPinnedItems.length) {
              return prev + 10;
            }
            return prev;
          });
        }
      },
      { threshold: 0.2 }
    );

    const currentLoader = loaderRef.current;
    if (currentLoader) {
      observer.observe(currentLoader);
    }

    return () => {
      observer.disconnect();
    };
  }, [allPinnedItems]);

  const handleRowClick = (id) => {
    navigate(`/viewItem/${id}`);
  };

  const handlePageChange = (page) => {
    if (page === currentPage) return;
    setLoading(true);
    loadItems(page);
  };

  const handleUnpin = async (itemId, e) => {
    e.stopPropagation();
    const request = scope.begin("unpin:" + itemId, { replace: false, abortable: false });
    if (!request) return;
    scope.cancel("pinned");
    try {
      await PriceTrackerApi.toggleBookmark(itemId);
      if (!request.isCurrent()) return;
      setAllPinnedItems((prev) => prev.filter((i) => i.id !== itemId));
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) console.error("Failed to unpin item:", err);
    } finally {
      request.finish();
    }
  };

  // Portfolio summary stats across ALL registered items
  const [stats, setStats] = useState({ count: 0, bought: 0 });

  const loadPortfolioSummary = useCallback(async () => {
    const request = scope.begin("summary");
    if (!request) return;
    try {
      const summary = await PortfolioApi.getPortfolioSummary({ signal: request.signal });
      if (!request.isCurrent()) return;
      setStats({
        count: summary.count || 0,
        bought: Number(summary.totalInvestmentValue || 0),
      });
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) console.error("Failed to load portfolio summary:", err);
    } finally {
      request.finish();
    }
  }, [scope]);

  useEffect(() => {
    loadPortfolioSummary();
    return () => scope.cancel("summary");
  }, [loadPortfolioSummary, scope]);
  return (
    <div className="container pb-5">
      {/* Portfolio Summary Metric Cards */}
      <div className="row g-3 mb-4">
        <div className="col-12 col-sm-6">
          <div className="portfolio-stat-card p-3">
            <span className="stat-label">Registered Items</span>
            <div className="stat-value text-white">{stats.count}</div>
          </div>
        </div>
        <div className="col-12 col-sm-6">
          <div className="portfolio-stat-card p-3">
            <span className="stat-label">Total Investment Value</span>
            <div className="stat-value text-info">€{stats.bought.toFixed(2)}</div>
          </div>
        </div>
      </div>

      {/* Main Portfolio Table Card */}
      <PortfolioHistorySection
        currentPage={currentPage}
        totalPages={totalPages}
        handlePageChange={handlePageChange}
        filterSortOption={filterSortOption}
        setFilterSortOption={setFilterSortOption}
        loading={loading}
        items={items}
        sortedFilteredItems={sortedFilteredItems}
        handleRowClick={handleRowClick}
      />

      {/* Pinned Market Items Section (Infinite Scroll & Timeframe Selector) */}
      <PinnedMarketItemsSection
        allPinnedItems={allPinnedItems}
        visiblePinnedItems={visiblePinnedItems}
        selectedTimeframe={selectedTimeframe}
        setSelectedTimeframe={setSelectedTimeframe}
        pinnedHistories={pinnedHistories}
        pinnedVisibleCount={pinnedVisibleCount}
        loaderRef={loaderRef}
        handleUnpin={handleUnpin}
        navigate={navigate}
      />
    </div>
  );
}
