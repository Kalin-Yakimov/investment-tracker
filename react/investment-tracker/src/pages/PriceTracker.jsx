import React, { useEffect, useMemo, useState } from "react";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../config/uiConfig";
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

const formatDateTime = (dateObj) => {
  if (!dateObj || Number.isNaN(dateObj.getTime())) return "";

  const day = String(dateObj.getDate()).padStart(2, "0");
  const month = String(dateObj.getMonth() + 1).padStart(2, "0");
  const year = dateObj.getFullYear();
  return `${day}/${month}/${year}`;
};

const formatDateDDMMYYYY = (dateStr) => {
  if (!dateStr) return "-";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return dateStr;
  const day = String(d.getDate()).padStart(2, "0");
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const year = d.getFullYear();
  const hours = String(d.getHours()).padStart(2, "0");
  const minutes = String(d.getMinutes()).padStart(2, "0");
  return `${day}/${month}/${year} ${hours}:${minutes}`;
};

/**
 * Builds one real, chronological point per day from stored CSGOTrader observations.
 */
const getItemPoints = (history = [], timeframe = "7D") => {
  const now = new Date();
  const periodDays = timeframe === "3M" ? 90 : timeframe === "1M" ? 30 : 7;
  const cutoffTime = now.getTime() - periodDays * 24 * 60 * 60 * 1000;

  const validPoints = (Array.isArray(history) ? history : [])
    .filter((entry) => entry?.price != null && entry?.dateExtracted)
    .map((entry) => ({
      dateObj: new Date(entry.dateExtracted),
      price: Number(entry.price),
    }))
    .filter((point) => !Number.isNaN(point.dateObj.getTime()) && point.dateObj.getTime() >= cutoffTime)
    .sort((a, b) => a.dateObj.getTime() - b.dateObj.getTime());

  // If the provider publishes more than once in a day, use that day's latest observation.
  const dailyMap = new Map();
  validPoints.forEach((point) => {
    const dateKey = `${point.dateObj.getFullYear()}-${String(point.dateObj.getMonth() + 1).padStart(2, "0")}-${String(point.dateObj.getDate()).padStart(2, "0")}`;
    dailyMap.set(dateKey, point);
  });

  return Array.from(dailyMap.values()).map((p) => ({
    ...p,
    dateExtracted: p.dateObj.toISOString(),
    label: formatDateTime(p.dateObj),
    price: p.price,
  }));
};

/**
 * Calculates percentage change between earliest and latest point in the selected timeframe
 */
const getPeriodChange = (points) => {
  if (!points || points.length < 2) {
    return { pct: null, direction: "neutral" };
  }

  const latest = points[points.length - 1].price;
  const first = points[0].price;

  if (!first || first === 0) {
    return { pct: null, direction: "neutral" };
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
        pointRadius: points.length === 1 ? 3 : 0,
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
  if (!points || points.length === 0) {
    return <div className="tracker-chart-empty text-center py-5">No price history yet</div>;
  }

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
  const [trackedItems, setTrackedItems] = useState([]);
  const [priceHistories, setPriceHistories] = useState({});

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

  useEffect(() => {
    // If an item was selected or query is empty, do not trigger search dropdown
    if (!query.trim() || selectedSearchItem) {
      setSearchResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const results = await UserService.searchCsItems(query.trim());
        setSearchResults(Array.isArray(results) ? results : []);
      } catch (err) {
        console.error("Search failed:", err);
      }
    }, ITEM_SEARCH_DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [query, selectedSearchItem]);

  const handleSelectSearchItem = async (csItem) => {
    setSearchResults([]);
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

    try {
      const payload = {
        itemName: csItem.name,
        marketHashName: csItem.marketHashName,
        csItemId: csItem.id,
        quantity: 1,
        showInDashboard: false,
      };

      await UserService.addTrackedItem(payload);
      await fetchTrackedItems();
    } catch (err) {
      console.error("Failed to add item:", err);
      const serverErr = err.response?.data?.error;
      if (serverErr && (serverErr.includes("already tracking") || serverErr.includes("already tracked"))) {
        setError("Item is already tracked.");
      } else {
        setError(serverErr || err.message || "Failed to add item to tracker.");
      }
    }
  };

  const handleDeleteItem = async (itemId) => {
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
    return getItemPoints(priceHistories[detailItem.id] || [], selectedTimeframe);
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
            Track CSGOTrader 24-hour average prices and their recorded history for CS items.
          </p>
        </div>
      </div>

      {/* Notification Messages */}
      {error && (
        <div
          className="mb-4 p-3 rounded-3 shadow-lg text-white"
          style={{
            background: "rgba(22, 27, 38, 0.95)",
            border: "1px solid rgba(255, 255, 255, 0.15)",
            color: "#ffffff",
            backdropFilter: "blur(12px)",
            fontSize: "0.95rem",
            fontWeight: "500",
          }}
        >
          {error}
        </div>
      )}

      {/* Search & Add Item Section */}
      <div className="card tracker-card-dark mb-4 position-relative" style={{ overflow: "visible", zIndex: 100 }}>
        <div className="card-body p-4" style={{ overflow: "visible" }}>
          <h5 className="card-title text-white mb-3 fw-bold">Add CS Item to Tracker</h5>
          <div className="position-relative w-100">
            <input
              type="text"
              className="form-control tracker-search"
              placeholder="Type CS2 skin or item name to track (e.g. AK-47 | Redline, Case)..."
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setSelectedSearchItem(null);
              }}
              autoComplete="off"
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
        </div>
      </div>

      {/* Global Timeframe Control Bar */}
      <div className="timeframe-bar-card p-3 mb-4 d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div className="d-flex align-items-center gap-2">
          <span className="timeframe-bar-title">📊 Trend Period:</span>
        </div>
        <div className="btn-group timeframe-toggle-group" role="group">
          {["7D", "1M", "3M"].map((tf) => (
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
                <div className="d-flex align-items-center gap-3 mt-2">
                  <span className="fs-4 fw-bold text-success">{formatCurrency(detailItem.price)}</span>
                  <span className={`badge ${detailTrend.direction === "positive" ? "bg-success" : detailTrend.direction === "negative" ? "bg-danger" : "bg-secondary"}`}>
                    {detailTrend.pct == null
                      ? "—"
                      : `${detailTrend.direction === "positive" ? "▲ +" : detailTrend.direction === "negative" ? "▼ " : ""}${detailTrend.pct.toFixed(2)}%`}
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
                  No price history yet.
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
                            {formatDateDDMMYYYY(h.dateExtracted)}
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
              <p>Search and add CS items above to track CSGOTrader 24-hour average prices.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-dark table-hover tracker-table mb-0 align-middle">
                <thead>
                  <tr>
                    <th scope="col" style={{ width: "45px" }} className="text-center">Pin</th>
                    <th scope="col" style={{ width: "60px" }}>Image</th>
                    <th scope="col">Item Name</th>
                    <th scope="col">Current Price</th>
                    <th scope="col" style={{ width: "200px" }}>Chart ({selectedTimeframe})</th>
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
                            {trend.pct == null
                              ? "—"
                              : `${trend.direction === "positive" ? "▲ +" : trend.direction === "negative" ? "▼ " : ""}${trend.pct.toFixed(2)}%`}
                          </span>
                        </td>
                        <td className="text-end" onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-danger"
                            title="Delete item from tracker"
                            onClick={() => setDeleteConfirmId(item.id)}
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
      {/* Centered Custom Delete Confirmation Modal */}
      {deleteConfirmId && (
        <div
          className="modal-backdrop-custom d-flex align-items-center justify-content-center"
          onClick={() => setDeleteConfirmId(null)}
        >
          <div
            className="modal-content-custom card tracker-card-dark p-4 position-relative text-center shadow-lg border border-secondary border-opacity-25"
            style={{ maxWidth: "420px", width: "90%", borderRadius: "16px" }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="fs-1 mb-2">🗑️</div>
            <h4 className="text-white fw-bold mb-2">Remove Item?</h4>
            <p className="text-secondary small mb-4">
              Are you sure you want to remove this item from your price tracker? Its recorded price history will be deleted.
            </p>
            <div className="d-flex align-items-center justify-content-center gap-3">
              <button
                type="button"
                className="btn btn-outline-light px-4"
                onClick={() => setDeleteConfirmId(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-danger px-4"
                onClick={() => {
                  handleDeleteItem(deleteConfirmId);
                  setDeleteConfirmId(null);
                }}
              >
                Remove Item
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
