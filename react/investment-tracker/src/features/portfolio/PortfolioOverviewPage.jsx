import PortfolioApi from "./PortfolioApi";
import PriceTrackerApi from "../price-tracker/PriceTrackerApi";
import { useEffect, useState, useCallback, useRef } from "react";
import { useNavigate } from "react-router-dom";
import useRequestScope from "../../shared/hooks/useRequestScope";
import usePriceHistories from "../../shared/hooks/usePriceHistories";
import { isRequestCanceled } from "../../shared/utils/RequestScope";
import PortfolioHistorySection from "./components/PortfolioHistorySection";
import PinnedMarketItemsSection from "./components/PinnedMarketItemsSection";
import RequestError from "../../shared/components/RequestError";
import { requestErrorMessage } from "../../shared/api/requestFeedback";
import PriceAttribution from "../../shared/components/PriceAttribution";
import usePortfolioReturnScroll from "./hooks/usePortfolioReturnScroll";
import "./styles/PortfolioOverviewPage.css";
import "../price-tracker/styles/PriceTrackerPage.css";
export default function PortfolioOverviewPage({ initialPage = 0, initialReturnAnchor = null, onPageLoaded, onItemOpen } = {}) {
  const scope = useRequestScope();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(initialPage);
  const portfolioPageToLoad = useRef(initialPage);
  const [itemsPerPage] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [portfolioError, setPortfolioError] = useState("");
  const lastPortfolioRequest = useRef({ page: 0, filter: "newest_date" });
  const [pinnedLoading, setPinnedLoading] = useState(true);
  const [pinnedError, setPinnedError] = useState("");
  const [pinnedActionError, setPinnedActionError] = useState("");
  const [summaryError, setSummaryError] = useState("");
  const navigate = useNavigate();
  const { sectionRef, registerRow, captureAnchor } = usePortfolioReturnScroll(
    initialReturnAnchor, !loading && !pinnedLoading && !scope.isPending("summary"),
  );

  // Portfolio Filtering & Sorting State
  const [filterSortOption, setFilterSortOption] = useState("newest_date");

  // Filtering, order and stable tie-breakers are authoritative on the server.
  const sortedFilteredItems = items;

  // Pinned Items State & Selected Timeframe
  const [selectedTimeframe, setSelectedTimeframe] = useState("7D");
  const [allPinnedItems, setAllPinnedItems] = useState([]);
  const [pinnedPage, setPinnedPage] = useState(0);
  const [pinnedTotal, setPinnedTotal] = useState(0);
  const lastPinnedPage = useRef(0);
  const pinnedVisibleCount = allPinnedItems.length;
  const visiblePinnedItems = allPinnedItems;
  const pinnedHistories = usePriceHistories(visiblePinnedItems);
  const loaderRef = useRef(null);

  const loadItems = useCallback(
    async (page = 0, filter = filterSortOption) => {
      const request = scope.begin("portfolio");
      if (!request) return;
      lastPortfolioRequest.current = { page, filter };
      setPortfolioError("");
      try {
        setLoading(true);
        let requestedPage = page;
        for (;;) {
          lastPortfolioRequest.current = { page: requestedPage, filter };
          const result = await PortfolioApi.getUserItems(requestedPage, itemsPerPage, filter, { signal: request.signal });
          if (!request.isCurrent()) return;
          const total = result.totalPages || 0;
          const lastPage = Math.max(0, total - 1);
          // Deleting the last row may remove a whole page. Fall back only as far
          // as necessary; pinned items keep their independent request/state.
          if (total > 0 && requestedPage > lastPage) {
            requestedPage = lastPage;
            continue;
          }
          const loadedPage = total > 0 ? requestedPage : 0;
          setItems(result.content || []);
          setTotalPages(total);
          setCurrentPage(loadedPage);
          portfolioPageToLoad.current = loadedPage;
          onPageLoaded?.(loadedPage);
          break;
        }
      } catch (error) {
        if (request.isCurrent() && !isRequestCanceled(error)) {
          setPortfolioError(requestErrorMessage(error, "Portfolio history could not be loaded."));
        }
      } finally {
        if (request.isCurrent()) setLoading(false);
        request.finish();
      }
    },
    [itemsPerPage, filterSortOption, scope, onPageLoaded],
  );

  const loadPinnedItems = useCallback(async (page = 0) => {
    const request = scope.begin("pinned");
    if (!request) return;
    lastPinnedPage.current = page;
    setPinnedLoading(true); setPinnedError("");
    try {
      const data = await PriceTrackerApi.getTrackedItems({ page, size: 10, dashboard: true, signal: request.signal });
      if (!request.isCurrent()) return;
      if (!Array.isArray(data?.content)) throw new Error("Invalid pinned-item page");
      setAllPinnedItems(previous => page === 0 ? data.content
        : [...new Map([...previous, ...data.content].map(item => [item.id, item])).values()]);
      setPinnedPage(data.number); setPinnedTotal(data.totalElements);
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) setPinnedError(requestErrorMessage(err, "Pinned items could not be loaded."));
    } finally {
      if (request.isCurrent()) setPinnedLoading(false);
      request.finish();
    }
  }, [scope]);

  useEffect(() => {
    loadItems(portfolioPageToLoad.current, filterSortOption);
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
        if (target.isIntersecting && !pinnedLoading && !pinnedError && pinnedVisibleCount < pinnedTotal) loadPinnedItems(pinnedPage + 1);
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
  }, [pinnedLoading, pinnedError, pinnedVisibleCount, pinnedTotal, pinnedPage, loadPinnedItems]);

  const handleRowClick = (id) => {
    onItemOpen?.(captureAnchor(id));
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
    setPinnedActionError("");
    try {
      await PriceTrackerApi.toggleBookmark(itemId);
      if (!request.isCurrent()) return;
      setAllPinnedItems((prev) => prev.filter((i) => i.id !== itemId));
      // Offset pages shift after unpinning. Reload the first page instead of skipping the next item.
      await loadPinnedItems(0);
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) setPinnedActionError(requestErrorMessage(err, "The item could not be unpinned.", { mutation: true }));
    } finally {
      request.finish();
    }
  };

  // Portfolio summary stats across ALL registered items
  const [stats, setStats] = useState(null);

  const loadPortfolioSummary = useCallback(async () => {
    const request = scope.begin("summary");
    if (!request) return;
    setSummaryError("");
    try {
      const summary = await PortfolioApi.getPortfolioSummary({ signal: request.signal });
      if (!request.isCurrent()) return;
      setStats({
        count: summary.count || 0,
        bought: Number(summary.totalInvestmentValue || 0),
      });
    } catch (err) {
      if (request.isCurrent() && !isRequestCanceled(err)) setSummaryError(requestErrorMessage(err, "Portfolio totals could not be loaded."));
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
            <div className="stat-value text-white">{stats?.count ?? "—"}</div>
          </div>
        </div>
        <div className="col-12 col-sm-6">
          <div className="portfolio-stat-card p-3">
            <span className="stat-label">Total Investment Value</span>
            <div className="stat-value text-info">{stats ? `€${stats.bought.toFixed(2)}` : "—"}</div>
          </div>
        </div>
      </div>
      <RequestError message={summaryError} onRetry={loadPortfolioSummary} retryLabel="Retry portfolio totals" pending={scope.isPending("summary")} />

      {/* Main Portfolio Table Card */}
      <PortfolioHistorySection
        sectionRef={sectionRef}
        registerRow={registerRow}
        currentPage={currentPage}
        totalPages={totalPages}
        handlePageChange={handlePageChange}
        filterSortOption={filterSortOption}
        setFilterSortOption={option => { portfolioPageToLoad.current = 0; setFilterSortOption(option); }}
        loading={loading}
        error={portfolioError}
        onRetry={() => loadItems(lastPortfolioRequest.current.page, lastPortfolioRequest.current.filter)}
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
        hasMore={pinnedVisibleCount < pinnedTotal}
        loaderRef={loaderRef}
        handleUnpin={handleUnpin}
        navigate={navigate}
        loading={pinnedLoading}
        error={pinnedError}
        actionError={pinnedActionError}
        onRetry={() => loadPinnedItems(lastPinnedPage.current)}
        isUnpinPending={id => scope.isPending("unpin:" + id)}
      />
      <PriceAttribution includePriceSource={false} />
    </div>
  );
}
