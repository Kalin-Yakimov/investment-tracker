import React, { useEffect, useState, useCallback, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import { Table } from "react-bootstrap";
import "../styles/Home.css";
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
/**
 * Formats timestamps strictly as DD/MM/YYYY (with optional HH:mm for 24H/7D)
 */
const formatDateTime = (dateObj, timeframe = "1M") => {
  if (!dateObj || Number.isNaN(dateObj.getTime())) return "";
  
  const day = String(dateObj.getDate()).padStart(2, "0");
  const month = String(dateObj.getMonth() + 1).padStart(2, "0");
  const year = dateObj.getFullYear();

  if (timeframe === "24H" || timeframe === "7D") {
    const hours = String(dateObj.getHours()).padStart(2, "0");
    const minutes = String(dateObj.getMinutes()).padStart(2, "0");
    return `${day}/${month}/${year} ${hours}:${minutes}`;
  }

  return `${day}/${month}/${year}`;
};

const formatDateDDMMYYYY = (dateStr) => {
  if (!dateStr) return "-";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return dateStr;
  const day = String(d.getDate()).padStart(2, "0");
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const year = d.getFullYear();
  return `${day}/${month}/${year}`;
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

  // Sort chronologically
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

  // Aggregate daily points for 1M & 3M
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
 * Compact Sparkline Component for Home Pinned Items Table
 */
function HomePinnedSparkline({ points, strokeColor }) {
  if (!points || points.length === 0) {
    return <span className="text-muted small">No chart</span>;
  }

  const prices = points.map((p) => p.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const priceRange = maxPrice - minPrice;
  const padding = priceRange > 0 ? priceRange * 0.15 : (minPrice > 0 ? minPrice * 0.05 : 0.5);

  const isPositive = prices[prices.length - 1] >= prices[0];
  const mainColor = strokeColor || (isPositive ? "#10b981" : "#ef4444");

  const data = {
    labels: points.map((p) => p.label || ""),
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
    <div style={{ width: "180px", height: "44px" }}>
      <Line data={data} options={options} />
    </div>
  );
}

export default function Home() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [itemsPerPage] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const navigate = useNavigate();

  // Pinned Items State & Selected Timeframe
  const [selectedTimeframe, setSelectedTimeframe] = useState("1M");
  const [allPinnedItems, setAllPinnedItems] = useState([]);
  const [pinnedVisibleCount, setPinnedVisibleCount] = useState(10);
  const [pinnedHistories, setPinnedHistories] = useState({});
  const loaderRef = useRef(null);

  const loadItems = useCallback(
    async (page = 0) => {
      try {
        const result = await UserService.getUserItems(page, itemsPerPage);
        setItems(result.content || []);
        setTotalPages(result.totalPages || 0);
        setCurrentPage(page);
      } catch (error) {
        console.error("Error loading items:", error);
        setItems([]);
      } finally {
        setLoading(false);
      }
    },
    [itemsPerPage],
  );

  const loadPinnedItems = useCallback(async () => {
    try {
      const data = await UserService.getTrackedItems();
      if (Array.isArray(data)) {
        const bookmarked = data.filter((item) => item.showInDashboard === true);
        bookmarked.sort((a, b) => (b.id || 0) - (a.id || 0));
        setAllPinnedItems(bookmarked);
      }
    } catch (err) {
      console.error("Failed to load pinned items:", err);
    }
  }, []);

  useEffect(() => {
    loadItems();
    loadPinnedItems();
  }, [loadItems, loadPinnedItems]);

  // Load price histories for visible pinned items
  useEffect(() => {
    const visibleItems = allPinnedItems.slice(0, pinnedVisibleCount);
    visibleItems.forEach((item) => {
      if (!pinnedHistories[item.id]) {
        UserService.getPriceHistory(item.id)
          .then((history) => {
            setPinnedHistories((prev) => ({
              ...prev,
              [item.id]: Array.isArray(history) ? history : [],
            }));
          })
          .catch((err) => {
            console.error(`Failed to load history for pinned item ${item.id}:`, err);
          });
      }
    });
  }, [allPinnedItems, pinnedVisibleCount, pinnedHistories]);

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
      if (currentLoader) {
        observer.unobserve(currentLoader);
      }
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
    try {
      await UserService.toggleBookmark(itemId);
      setAllPinnedItems((prev) => prev.filter((i) => i.id !== itemId));
    } catch (err) {
      console.error("Failed to unpin item:", err);
    }
  };

  // Calculate portfolio summary stats for display
  const stats = useMemo(() => {
    let totalBought = 0;
    items.forEach((item) => {
      const itemTotal = Number(item.price || 0) * Number(item.quantity || 1);
      if (item.itemStatus === "BOUGHT") {
        totalBought += itemTotal;
      }
    });
    return {
      count: items.length,
      bought: totalBought,
    };
  }, [items]);

  const visiblePinnedItems = useMemo(() => {
    return allPinnedItems.slice(0, pinnedVisibleCount);
  }, [allPinnedItems, pinnedVisibleCount]);

  const renderPagination = () => (
    <div className="d-flex align-items-center justify-content-between pt-3 px-2">
      <span className="text-white-50 small">
        Page {currentPage + 1} of {totalPages || 1}
      </span>
      <nav aria-label="Portfolio pagination">
        <ul className="pagination pagination-sm mb-0">
          <li className={`page-item ${currentPage === 0 ? "disabled" : ""}`}>
            <button
              className="page-link"
              onClick={() => handlePageChange(currentPage - 1)}
              disabled={currentPage === 0}
            >
              Previous
            </button>
          </li>
          {Array.from({ length: totalPages }, (_, index) => (
            <li
              key={index}
              className={`page-item ${index === currentPage ? "active" : ""}`}
            >
              <button
                className="page-link"
                onClick={() => handlePageChange(index)}
              >
                {index + 1}
              </button>
            </li>
          ))}
          <li
            className={`page-item ${
              currentPage >= totalPages - 1 ? "disabled" : ""
            }`}
          >
            <button
              className="page-link"
              onClick={() => handlePageChange(currentPage + 1)}
              disabled={currentPage >= totalPages - 1}
            >
              Next
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );

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
      <div className="card portfolio-table-card shadow-lg p-4 mb-5">
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h3 className="portfolio-table-title mb-0">📁 Portfolio History</h3>
          <span className="text-white-50 small">Click any item row to view details or edit</span>
        </div>

        {loading ? (
          <div className="alert alert-info py-3 text-center mb-0" role="alert">
            ⚡ Loading portfolio items...
          </div>
        ) : items.length > 0 ? (
          <>
            <div className="table-responsive">
              <Table hover responsive className="portfolio-table align-middle">
                <thead>
                  <tr>
                    <th scope="col" style={{ width: "70px" }}>Item</th>
                    <th scope="col">Name</th>
                    <th scope="col">Status</th>
                    <th scope="col">Date</th>
                    <th scope="col" className="text-center">Quantity</th>
                    <th scope="col" className="text-end">Unit Price</th>
                    <th scope="col" className="text-end">Total Price</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => {
                    const isBought = item.itemStatus === "BOUGHT";
                    const totalPrice = Number(item.price || 0) * Number(item.quantity || 1);
                    return (
                      <tr
                        key={item.id}
                        onClick={() => handleRowClick(item.id)}
                        className="portfolio-row-clickable"
                      >
                        <td>
                          {item.image ? (
                            <img
                              src={item.image}
                              alt={item.itemName}
                              className="portfolio-item-thumb"
                            />
                          ) : (
                            <div className="portfolio-item-thumb-placeholder">📷</div>
                          )}
                        </td>
                        <td className="fw-semibold">{item.itemName}</td>
                        <td>
                          <span className={`badge-status ${isBought ? "badge-bought" : "badge-sold"}`}>
                            {isBought ? "Bought" : "Sold"}
                          </span>
                        </td>
                        <td className="text-muted small">{formatDateDDMMYYYY(item.date)}</td>
                        <td className="text-center font-mono">{item.quantity}</td>
                        <td className="text-end font-mono">€{Number(item.price).toFixed(2)}</td>
                        <td className="text-end font-mono fw-bold">
                          €{totalPrice.toFixed(2)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            </div>
            {renderPagination()}
          </>
        ) : (
          <div className="alert alert-warning text-center py-4 mb-0" role="alert">
            No portfolio items registered yet. Use the form above to register your first item!
          </div>
        )}
      </div>

      {/* Pinned Market Items Section (Infinite Scroll & Timeframe Selector) */}
      <div className="card portfolio-table-card shadow-lg p-4">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
          <h3 className="portfolio-table-title mb-0">⭐ Pinned Market Items</h3>
          
          {/* Trend Period Control Group in Header */}
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

        {allPinnedItems.length === 0 ? (
          <div className="p-4 text-center text-white my-2" style={{ background: "rgba(255, 255, 255, 0.02)", borderRadius: "12px", border: "1px dashed rgba(255, 255, 255, 0.1)" }}>
            <h6 className="mb-0 text-white fw-normal">No pinned items at the moment.</h6>
          </div>
        ) : (
          <div className="table-responsive">
            <Table hover responsive className="portfolio-table align-middle mb-0">
              <thead>
                <tr>
                  <th scope="col" style={{ width: "45px" }} className="text-center">Unpin</th>
                  <th scope="col" style={{ width: "60px" }}>Image</th>
                  <th scope="col">Item Name</th>
                  <th scope="col">Current Price</th>
                  <th scope="col">% Change</th>
                  <th scope="col" style={{ width: "200px" }}>Chart ({selectedTimeframe})</th>
                </tr>
              </thead>
              <tbody>
                {visiblePinnedItems.map((item) => {
                  const history = pinnedHistories[item.id] || [];
                  const points = getItemPoints(item, history, selectedTimeframe);
                  const trend = getPeriodChange(points);
                  const strokeColor = trend.direction === "positive" ? "#10b981" : trend.direction === "negative" ? "#ef4444" : "#6b7280";

                  return (
                    <tr key={item.id} className="portfolio-row-clickable" onClick={() => navigate("/tracker")}>
                      <td className="text-center" onClick={(e) => handleUnpin(item.id, e)}>
                        <button
                          type="button"
                          className="btn btn-sm p-1 border-0 text-warning"
                          title="Unpin from Main Dashboard"
                          style={{ fontSize: "1.2rem", background: "transparent" }}
                        >
                          ⭐
                        </button>
                      </td>
                      <td>
                        {item.image ? (
                          <img
                            src={item.image}
                            alt={item.itemName}
                            className="portfolio-item-thumb"
                          />
                        ) : (
                          <div className="portfolio-item-thumb-placeholder">📷</div>
                        )}
                      </td>
                      <td>
                        <div className="fw-semibold text-white">{item.itemName}</div>
                      </td>
                      <td className="font-mono fw-bold text-success">
                        {formatCurrency(item.price)}
                      </td>
                      <td>
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
                      <td>
                        <HomePinnedSparkline points={points} strokeColor={strokeColor} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>

            {/* Infinite Scroll Sentinel / Loader */}
            {pinnedVisibleCount < allPinnedItems.length && (
              <div ref={loaderRef} className="text-center py-3 text-muted small">
                ⚡ Loading more pinned items...
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
