import React, { useEffect, useMemo, useState } from "react";
import UserService from "../service/UserService";
import "../styles/PriceTracker.css";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from "chart.js";
import { Line } from "react-chartjs-2";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

const formatCurrency = (value) => {
  if (value == null || Number.isNaN(Number(value))) return "-";
  return `€${Number(value).toFixed(2)}`;
};

/**
 * Formats timestamps cleanly:
 * - 24H and 7D: Includes time ("Jul 28, 20:54")
 * - 1M and 3M: Date only ("Jul 28, 2026")
 */
const formatDateTime = (dateObj, timeframe = "1M") => {
  if (!dateObj || Number.isNaN(dateObj.getTime())) return "";
  
  if (timeframe === "24H" || timeframe === "7D") {
    return dateObj.toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  }

  return dateObj.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
};

/**
 * Builds chronological historical data points and performs smart date/time aggregation
 */
const getItemPoints = (item, history = [], timeframe = "1M") => {
  const rawPoints = [];
  const now = new Date();

  const priceNow = item?.price != null ? Number(item.price) : (history[0]?.price != null ? Number(history[0].price) : 0);
  const p24h = item?.price24h != null ? Number(item.price24h) : null;
  const p7d = item?.price7d != null ? Number(item.price7d) : null;
  const p30d = item?.price30d != null ? Number(item.price30d) : null;
  const p90d = item?.price90d != null ? Number(item.price90d) : null;

  if (p90d !== null && timeframe === "3M") {
    const d90 = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
    rawPoints.push({ dateObj: d90, price: p90d });
  }

  if (p30d !== null && (timeframe === "1M" || timeframe === "3M")) {
    const d30 = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    rawPoints.push({ dateObj: d30, price: p30d });
  }

  if (p7d !== null && (timeframe === "7D" || timeframe === "1M" || timeframe === "3M")) {
    const d7 = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    rawPoints.push({ dateObj: d7, price: p7d });
  }

  if (p24h !== null) {
    const d24 = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    rawPoints.push({ dateObj: d24, price: p24h });
  }

  if (Array.isArray(history) && history.length > 0) {
    history.forEach((h) => {
      if (h?.price != null && h?.dateExtracted) {
        const dt = new Date(h.dateExtracted);
        if (!Number.isNaN(dt.getTime())) {
          rawPoints.push({ dateObj: dt, price: Number(h.price) });
        }
      }
    });
  }

  if (priceNow > 0) {
    rawPoints.push({ dateObj: now, price: priceNow });
  }

  // Sort chronologically (oldest to newest)
  rawPoints.sort((a, b) => a.dateObj.getTime() - b.dateObj.getTime());

  if (rawPoints.length === 0) return [];

  // Filter based on timeframe cutoff
  let cutoffMs = 0;
  if (timeframe === "24H") cutoffMs = 24 * 60 * 60 * 1000;
  else if (timeframe === "7D") cutoffMs = 7 * 24 * 60 * 60 * 1000;
  else if (timeframe === "1M") cutoffMs = 30 * 24 * 60 * 60 * 1000;
  else if (timeframe === "3M") cutoffMs = 90 * 24 * 60 * 60 * 1000;

  let filtered = rawPoints;
  if (cutoffMs > 0 && rawPoints.length > 2) {
    const cutoffTime = now.getTime() - cutoffMs;
    filtered = rawPoints.filter((p, idx) => idx === rawPoints.length - 1 || p.dateObj.getTime() >= cutoffTime);
    if (filtered.length < 2) filtered = rawPoints;
  }

  // Aggregate daily points for 1M & 3M to prevent duplicate date labels
  if (timeframe === "1M" || timeframe === "3M") {
    const dailyMap = new Map();
    filtered.forEach((p) => {
      const dateKey = p.dateObj.toISOString().split("T")[0];
      dailyMap.set(dateKey, p);
    });
    filtered = Array.from(dailyMap.values()).sort((a, b) => a.dateObj.getTime() - b.dateObj.getTime());
  }

  return filtered.map((p) => ({
    ...p,
    dateExtracted: p.dateObj.toISOString(),
    label: formatDateTime(p.dateObj, timeframe),
    price: p.price,
  }));
};

/**
 * Calculates percentage change between earliest and latest point in the selected timeframe
 */
const getPeriodChange = (points) => {
  if (!points || points.length < 2) {
    return { pct: 0, direction: "neutral" };
  }

  const latest = points[points.length - 1].price;
  const first = points[0].price;

  if (!first || first === 0) {
    return { pct: 0, direction: "neutral" };
  }

  const pct = ((latest - first) / first) * 100;
  const direction = pct > 0 ? "positive" : pct < 0 ? "negative" : "neutral";
  return { pct, direction };
};

/**
 * Chart.js Sparkline Component for Table Rows
 */
function InteractiveSparkline({ points, strokeColor }) {
  if (!points || points.length === 0) {
    return <span className="tracker-chart-empty">No data</span>;
  }

  const prices = points.map((p) => p.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const priceRange = maxPrice - minPrice;
  const padding = priceRange > 0 ? priceRange * 0.15 : (minPrice > 0 ? minPrice * 0.05 : 0.5);

  const isPositive = prices[prices.length - 1] >= prices[0];
  const mainColor = strokeColor || (isPositive ? "#10b981" : "#ef4444");

  const data = {
    labels: points.map((p) => p.label),
    datasets: [
      {
        data: prices,
        borderColor: mainColor,
        borderWidth: 2,
        tension: 0.35,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHoverBackgroundColor: "#ffffff",
        pointHoverBorderColor: mainColor,
        pointHoverBorderWidth: 2,
        fill: true,
        backgroundColor: (context) => {
          const chart = context.chart;
          const { ctx, chartArea } = chart;
          if (!chartArea) return null;
          const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
          gradient.addColorStop(0, mainColor + "33");
          gradient.addColorStop(1, mainColor + "00");
          return gradient;
        },
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        enabled: true,
        mode: "index",
        intersect: false,
        backgroundColor: "rgba(15, 23, 42, 0.9)",
        titleColor: "#94a3b8",
        bodyColor: "#f8fafc",
        titleFont: { size: 11, weight: "500" },
        bodyFont: { size: 12, weight: "bold" },
        borderColor: "rgba(255, 255, 255, 0.1)",
        borderWidth: 1,
        padding: 8,
        displayColors: false,
        callbacks: {
          label: (context) => formatCurrency(context.parsed.y),
        },
      },
    },
    scales: {
      x: { display: false },
      y: {
        display: false,
        suggestedMin: Math.max(0, minPrice - padding),
        suggestedMax: maxPrice + padding,
      },
    },
    interaction: {
      mode: "nearest",
      axis: "x",
      intersect: false,
    },
  };

  return (
    <div className="sparkline-chartjs-wrapper" style={{ width: "180px", height: "44px" }}>
      <Line data={data} options={options} />
    </div>
  );
}

/**
 * Detailed Large Chart Component built with Chart.js
 */
function DetailedInteractiveChart({ points }) {
  if (!points || points.length === 0) return null;

  const prices = points.map((p) => p.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const priceRange = maxPrice - minPrice;
  const padding = priceRange > 0 ? priceRange * 0.15 : (minPrice > 0 ? minPrice * 0.05 : 0.5);

  const isPositive = prices[prices.length - 1] >= prices[0];
  const mainColor = isPositive ? "#10b981" : "#ef4444";

  const data = {
    labels: points.map((p) => p.label),
    datasets: [
      {
        data: prices,
        borderColor: mainColor,
        borderWidth: 2.5,
        tension: 0.35,
        pointRadius: points.length <= 10 ? 3 : 0,
        pointHoverRadius: 6,
        pointHoverBackgroundColor: "#ffffff",
        pointHoverBorderColor: mainColor,
        pointHoverBorderWidth: 3,
        fill: true,
        backgroundColor: (context) => {
          const chart = context.chart;
          const { ctx, chartArea } = chart;
          if (!chartArea) return null;
          const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
          gradient.addColorStop(0, mainColor + "44");
          gradient.addColorStop(1, mainColor + "00");
          return gradient;
        },
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        enabled: true,
        mode: "index",
        intersect: false,
        backgroundColor: "rgba(15, 23, 42, 0.95)",
        titleColor: "#94a3b8",
        bodyColor: "#f8fafc",
        titleFont: { size: 12 },
        bodyFont: { size: 14, weight: "bold" },
        borderColor: "rgba(255, 255, 255, 0.12)",
        borderWidth: 1,
        padding: 10,
        displayColors: false,
        callbacks: {
          label: (context) => `Price: ${formatCurrency(context.parsed.y)}`,
        },
      },
    },
    scales: {
      x: {
        grid: { color: "rgba(255, 255, 255, 0.05)" },
        ticks: { color: "#64748b", font: { size: 11 } },
      },
      y: {
        grid: { color: "rgba(255, 255, 255, 0.05)" },
        ticks: {
          color: "#64748b",
          font: { size: 11 },
          callback: (val) => `€${val.toFixed(2)}`,
        },
        suggestedMin: Math.max(0, minPrice - padding),
        suggestedMax: maxPrice + padding,
      },
    },
  };

  return (
    <div className="detailed-chart-wrapper mb-4">
      <div className="d-flex justify-content-between align-items-center mb-2 px-1">
        <span className="small text-muted">Hover chart line to view price timeline</span>
      </div>
      <div className="detailed-chart-container p-2" style={{ height: "220px" }}>
        <Line data={data} options={options} />
      </div>
    </div>
  );
}

export default function PriceTracker() {
  const [query, setQuery] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [selectedSearchItem, setSelectedSearchItem] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [trackedItems, setTrackedItems] = useState([]);
  const [priceHistories, setPriceHistories] = useState({});

  const [selectedTimeframe, setSelectedTimeframe] = useState("1M");
  const [detailItem, setDetailItem] = useState(null);

  const fetchTrackedItems = async () => {
    try {
      const data = await UserService.getTrackedItems();
      const list = Array.isArray(data) ? data : [];
      // Sort so newly added items (highest ID) appear on top
      list.sort((a, b) => (b.id || 0) - (a.id || 0));
      setTrackedItems(list);
    } catch (err) {
      console.error("Failed to load tracked items:", err);
      setError("Failed to load tracked items from backend.");
    }
  };

  useEffect(() => {
    fetchTrackedItems();
  }, []);

  useEffect(() => {
    if (!trackedItems || trackedItems.length === 0) return;

    trackedItems.forEach((item) => {
      if (!priceHistories[item.id]) {
        UserService.getPriceHistory(item.id)
          .then((history) => {
            setPriceHistories((prev) => ({
              ...prev,
              [item.id]: Array.isArray(history) ? history : [],
            }));
          })
          .catch((err) => {
            console.error(`Failed to load history for item ${item.id}:`, err);
          });
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trackedItems]);

  const itemPointsMap = useMemo(() => {
    const map = {};
    trackedItems.forEach((item) => {
      const history = priceHistories[item.id] || [];
      map[item.id] = getItemPoints(item, history, selectedTimeframe);
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

  useEffect(() => {
    if (!query.trim()) {
      setSearchResults([]);
      setSelectedSearchItem(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const results = await UserService.searchCsItems(query.trim());
        setSearchResults(Array.isArray(results) ? results : []);
      } catch (err) {
        console.error("Search failed:", err);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [query]);

  const handleSelectSearchItem = (csItem) => {
    setSelectedSearchItem(csItem);
    setQuery(csItem.name);
    setSearchResults([]);
  };

  const handleAddItem = async (e) => {
    e.preventDefault();
    if (!query.trim()) return;

    setLoading(true);
    setError("");

    try {
      const payload = {
        itemName: selectedSearchItem ? selectedSearchItem.name : query.trim(),
        marketHashName: selectedSearchItem ? selectedSearchItem.marketHashName : query.trim(),
        csItemId: selectedSearchItem ? selectedSearchItem.id : null,
        quantity: 1,
        showInDashboard: false,
      };

      await UserService.addTrackedItem(payload);
      setQuery("");
      setSelectedSearchItem(null);
      setSearchResults([]);
      await fetchTrackedItems();
    } catch (err) {
      console.error("Failed to add item:", err);
      setError(err.response?.data?.error || err.message || "Failed to add item to tracker.");
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteItem = async (itemId) => {
    if (!window.confirm("Are you sure you want to remove this item from your tracker?")) return;

    try {
      await UserService.deleteTrackedItem(itemId);
      setTrackedItems((prev) => prev.filter((i) => i.id !== itemId));
      if (detailItem && detailItem.id === itemId) {
        setDetailItem(null);
      }
    } catch (err) {
      console.error("Failed to delete item:", err);
      setError("Failed to delete item.");
    }
  };

  const handleToggleBookmark = async (itemId) => {
    try {
      const res = await UserService.toggleBookmark(itemId);
      setTrackedItems((prev) =>
        prev.map((i) => (i.id === itemId ? { ...i, showInDashboard: res.bookmarked } : i))
      );
    } catch (err) {
      console.error("Failed to toggle bookmark:", err);
    }
  };

  const detailPoints = useMemo(() => {
    if (!detailItem) return [];
    return getItemPoints(detailItem, priceHistories[detailItem.id] || [], selectedTimeframe);
  }, [detailItem, priceHistories, selectedTimeframe]);

  const detailTrend = useMemo(() => {
    return getPeriodChange(detailPoints);
  }, [detailPoints]);

  const detailHistoryList = useMemo(() => {
    if (!detailItem) return [];
    return priceHistories[detailItem.id] || [];
  }, [detailItem, priceHistories]);

  return (
    <div className="tracker-page-container container-fluid py-4 px-md-5">
      {/* Page Header */}
      <div className="row mb-4 align-items-center">
        <div className="col-12">
          <h2 className="tracker-header-title text-white fw-bold mb-1">
            📈 Steam Community Market Price Tracker
          </h2>
          <p className="text-secondary mb-0">
            Track real-time market trends, historical price charts, and value changes for CS items.
          </p>
        </div>
      </div>

      {/* Alert Messages */}
      {error && <div className="alert alert-danger border-0 shadow-sm mb-4">{error}</div>}

      {/* Search & Add Item Section */}
      <div className="card tracker-card-dark mb-4">
        <div className="card-body p-4">
          <h5 className="card-title text-white mb-3">Add CS Item to Tracker</h5>
          <div className="row g-3">
            <div className="col-lg-9 position-relative">
              <input
                type="text"
                className="form-control tracker-search-input"
                placeholder="Search CS item (e.g. AK-47 | Redline, Case, Sticker)..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              {searchResults.length > 0 && (
                <div className="tracker-autocomplete-dropdown">
                  {searchResults.map((item) => (
                    <div
                      key={item.id}
                      className="tracker-autocomplete-item d-flex align-items-center gap-3 p-2"
                      onClick={() => handleSelectSearchItem(item)}
                    >
                      {item.image && (
                        <img
                          src={item.image}
                          alt={item.name}
                          className="tracker-autocomplete-img"
                          style={{ width: "40px", height: "30px", objectFit: "contain" }}
                        />
                      )}
                      <div>
                        <div className="fw-semibold text-white">{item.name}</div>
                        <div className="small text-muted">{item.marketHashName}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="col-lg-3">
              <button
                type="button"
                className="btn btn-primary w-100 tracker-add-btn"
                onClick={handleAddItem}
                disabled={loading || !query.trim()}
              >
                Track Item
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Global Timeframe Control Bar */}
      <div className="timeframe-bar-card p-3 mb-4 d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div className="d-flex align-items-center gap-2">
          <span className="timeframe-bar-title">📊 Trend Period:</span>
        </div>
        <div className="btn-group timeframe-toggle-group" role="group">
          {["24H", "7D", "1M", "3M"].map((tf) => (
            <button
              key={tf}
              type="button"
              className={`btn btn-sm timeframe-toggle-btn ${selectedTimeframe === tf ? "active" : ""}`}
              onClick={() => setSelectedTimeframe(tf)}
            >
              {tf}
            </button>
          ))}
        </div>
      </div>

      {/* Focused Overlay Modal for Item Detail View */}
      {detailItem && (
        <div className="modal-backdrop-custom" onClick={() => setDetailItem(null)}>
          <div className="modal-content-custom card tracker-card-dark p-4 position-relative" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="btn-close btn-close-white position-absolute top-0 end-0 m-3"
              onClick={() => setDetailItem(null)}
              title="Close Detail View"
            ></button>
            
            <div className="d-flex align-items-center gap-4 mb-4">
              {detailItem.image && (
                <img
                  src={detailItem.image}
                  alt={detailItem.itemName}
                  style={{ width: "100px", height: "75px", objectFit: "contain" }}
                />
              )}
              <div>
                <h4 className="text-white mb-1 fw-bold">{detailItem.itemName}</h4>
                <div className="text-muted small mb-2">{detailItem.marketHashName}</div>
                <div className="d-flex align-items-center gap-3">
                  <span className="fs-4 fw-bold text-success">{formatCurrency(detailItem.price)}</span>
                  <span className={`badge ${detailTrend.direction === "positive" ? "bg-success" : detailTrend.direction === "negative" ? "bg-danger" : "bg-secondary"}`}>
                    {detailTrend.direction === "positive" ? "▲ +" : detailTrend.direction === "negative" ? "▼ " : ""}
                    {detailTrend.pct.toFixed(2)}% ({selectedTimeframe})
                  </span>
                </div>
              </div>
            </div>

            <DetailedInteractiveChart points={detailPoints} />

            {/* Historical Price Log Table (2 Clean Columns: Date Recorded & Market Price) */}
            <div className="mt-4">
              <h5 className="text-white fw-bold mb-3">📋 Historical Price Log</h5>
              {detailHistoryList.length === 0 ? (
                <div className="text-muted small p-3 bg-dark rounded text-center">
                  No historical price logs recorded yet. Automated price updates run hourly.
                </div>
              ) : (
                <div className="table-responsive rounded border border-secondary border-opacity-25" style={{ maxHeight: "250px", overflowY: "auto" }}>
                  <table className="table table-dark table-sm table-striped table-hover align-middle mb-0">
                    <thead>
                      <tr>
                        <th scope="col" className="ps-3">Date Recorded</th>
                        <th scope="col" className="text-end pe-3">Recorded Market Price (€)</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detailHistoryList.map((h) => (
                        <tr key={h.id || h.dateExtracted}>
                          <td className="ps-3 small text-secondary">
                            {h.dateExtracted ? new Date(h.dateExtracted).toLocaleString() : "-"}
                          </td>
                          <td className="text-end pe-3 font-mono text-success fw-bold">
                            {formatCurrency(h.price)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="text-end mt-4">
              <button className="btn btn-outline-light btn-sm px-4" onClick={() => setDetailItem(null)}>
                ✕ Close Detail View
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Tracked Items Table */}
      <div className="card tracker-card-dark">
        <div className="card-body p-0">
          {trackedItems.length === 0 ? (
            <div className="p-5 text-center text-muted">
              <h5>No items tracked yet.</h5>
              <p>Search and add CS items above to track real-time Steam Community Market prices.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-dark table-hover tracker-table mb-0 align-middle">
                <thead>
                  <tr>
                    <th scope="col" style={{ width: "45px" }} className="text-center">Pin</th>
                    <th scope="col" style={{ width: "60px" }}>Image</th>
                    <th scope="col">Item Name</th>
                    <th scope="col">Market Price</th>
                    <th scope="col" style={{ width: "200px" }}>Sparkline ({selectedTimeframe})</th>
                    <th scope="col">% Change</th>
                    <th scope="col" className="text-end" style={{ width: "80px" }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {trackedItems.map((item) => {
                    const points = itemPointsMap[item.id] || [];
                    const trend = itemTrendsMap[item.id] || { pct: 0, direction: "neutral" };
                    const strokeColor = trend.direction === "positive" ? "#10b981" : trend.direction === "negative" ? "#ef4444" : "#6b7280";

                    return (
                      <tr key={item.id} className="tracker-row">
                        <td className="text-center" onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            className={`btn btn-sm p-1 border-0 ${item.showInDashboard ? "text-warning" : "text-secondary"}`}
                            title={item.showInDashboard ? "Unpin from Main Dashboard" : "Pin to Main Dashboard"}
                            onClick={() => handleToggleBookmark(item.id)}
                            style={{ fontSize: "1.2rem", background: "transparent" }}
                          >
                            {item.showInDashboard ? "⭐" : "☆"}
                          </button>
                        </td>
                        <td onClick={() => setDetailItem(item)}>
                          {item.image ? (
                            <img
                              src={item.image}
                              alt={item.itemName}
                              style={{ width: "48px", height: "36px", objectFit: "contain" }}
                            />
                          ) : (
                            <div className="tracker-no-img">📦</div>
                          )}
                        </td>
                        <td onClick={() => setDetailItem(item)}>
                          <div className="fw-bold text-white cursor-pointer">
                            {item.itemName}
                          </div>
                          <div className="small text-muted">{item.marketHashName}</div>
                        </td>
                        <td className="fw-bold text-success fs-6" onClick={() => setDetailItem(item)}>
                          {formatCurrency(item.price)}
                        </td>
                        <td onClick={() => setDetailItem(item)}>
                          <InteractiveSparkline points={points} strokeColor={strokeColor} />
                        </td>
                        <td onClick={() => setDetailItem(item)}>
                          <span
                            className={`badge ${
                              trend.direction === "positive"
                                ? "bg-success"
                                : trend.direction === "negative"
                                ? "bg-danger"
                                : "bg-secondary"
                            }`}
                          >
                            {trend.direction === "positive" ? "▲ +" : trend.direction === "negative" ? "▼ " : ""}
                            {trend.pct.toFixed(2)}%
                          </span>
                        </td>
                        <td className="text-end" onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-danger"
                            title="Delete item from tracker"
                            onClick={() => handleDeleteItem(item.id)}
                          >
                            🗑️
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
