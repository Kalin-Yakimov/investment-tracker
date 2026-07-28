import React, { useEffect, useMemo, useState } from "react";
import UserService from "../service/UserService";
import "../styles/PriceTracker.css";

const formatCurrency = (value) => {
  if (value == null || Number.isNaN(Number(value))) return "-";
  return `€${Number(value).toFixed(2)}`;
};

/**
 * Helper to format a date nicely as a calendar date (e.g. "Jul 23, 2026")
 */
const formatDateOnly = (dateObj) => {
  if (!dateObj || Number.isNaN(dateObj.getTime())) return "";
  return dateObj.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
};

/**
 * Builds chronological historical data points (30D, 7D, 24H, Current + DB history) for an item
 */
const getItemPoints = (item, history = [], timeframe = "1M") => {
  const points = [];
  const now = new Date();

  const priceNow = item?.price != null ? Number(item.price) : (history[0]?.price != null ? Number(history[0].price) : 0);
  const p24h = item?.price24h != null ? Number(item.price24h) : null;
  const p7d = item?.price7d != null ? Number(item.price7d) : null;
  const p30d = item?.price30d != null ? Number(item.price30d) : null;
  const p90d = item?.price90d != null ? Number(item.price90d) : null;

  // Add 90D baseline point if available and applicable (3M timeframe)
  if (p90d !== null && timeframe === "3M") {
    const d90 = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
    points.push({
      dateExtracted: d90.toISOString(),
      label: formatDateOnly(d90),
      price: p90d,
    });
  }

  // Add 30D baseline point if available and applicable (1M or 3M)
  if (p30d !== null && (timeframe === "1M" || timeframe === "3M")) {
    const d30 = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    points.push({
      dateExtracted: d30.toISOString(),
      label: formatDateOnly(d30),
      price: p30d,
    });
  }

  // Add 7D baseline point if available and applicable (7D, 1M, 3M)
  if (p7d !== null && (timeframe === "7D" || timeframe === "1M" || timeframe === "3M")) {
    const d7 = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    points.push({
      dateExtracted: d7.toISOString(),
      label: formatDateOnly(d7),
      price: p7d,
    });
  }

  // Add 24H baseline point if available
  if (p24h !== null) {
    const d24 = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    points.push({
      dateExtracted: d24.toISOString(),
      label: formatDateOnly(d24),
      price: p24h,
    });
  }

  // Add DB PriceHistory records if present
  if (Array.isArray(history) && history.length > 0) {
    history.forEach((h) => {
      if (h?.price != null && h?.dateExtracted) {
        const dt = new Date(h.dateExtracted);
        points.push({
          dateExtracted: dt.toISOString(),
          label: formatDateOnly(dt),
          price: Number(h.price),
        });
      }
    });
  }

  // Add Current Price point
  if (priceNow > 0) {
    points.push({
      dateExtracted: now.toISOString(),
      label: formatDateOnly(now),
      price: priceNow,
    });
  }

  // Sort chronologically (oldest to newest)
  const sorted = points.sort((a, b) => new Date(a.dateExtracted) - new Date(b.dateExtracted));

  // Deduplicate points with identical date label and price
  const uniquePoints = [];
  const seenKeys = new Set();
  sorted.forEach((p) => {
    const key = `${p.label}-${p.price}`;
    if (!seenKeys.has(key)) {
      seenKeys.add(key);
      uniquePoints.push(p);
    }
  });

  // Filter based on selected timeframe window if needed
  let cutoffMs = 0;
  if (timeframe === "24H") cutoffMs = 24 * 60 * 60 * 1000;
  else if (timeframe === "7D") cutoffMs = 7 * 24 * 60 * 60 * 1000;
  else if (timeframe === "1M") cutoffMs = 30 * 24 * 60 * 60 * 1000;
  else if (timeframe === "3M") cutoffMs = 90 * 24 * 60 * 60 * 1000;

  if (cutoffMs > 0 && uniquePoints.length > 2) {
    const cutoffTime = now.getTime() - cutoffMs;
    const filtered = uniquePoints.filter((p, idx) => idx === uniquePoints.length - 1 || new Date(p.dateExtracted).getTime() >= cutoffTime);
    if (filtered.length >= 2) return filtered;
  }

  return uniquePoints;
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
 * Interactive SVG Sparkline Component with hover tooltips
 */
function InteractiveSparkline({ itemId, points, strokeColor }) {
  const [hoverIndex, setHoverIndex] = useState(null);
  const [tooltipX, setTooltipX] = useState(0);

  if (!points || points.length === 0) {
    return <span className="tracker-chart-empty">No data</span>;
  }

  const prices = points.map((p) => p.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;

  const width = 180;
  const height = 44;
  const paddingY = 8;
  const paddingX = 8;
  const usableWidth = width - paddingX * 2;
  const usableHeight = height - paddingY * 2;

  const pointCoords = points.map((point, idx) => {
    const x = points.length === 1 ? width / 2 : paddingX + (idx / (points.length - 1)) * usableWidth;
    const y = height - paddingY - ((point.price - min) / range) * usableHeight;
    return { ...point, x, y };
  });

  const linePath = pointCoords
    .map((p, idx) => `${idx === 0 ? "M" : "L"}${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
    .join(" ");

  const fillPath = `${linePath} L${pointCoords[pointCoords.length - 1].x.toFixed(1)} ${height} L${pointCoords[0].x.toFixed(1)} ${height} Z`;

  const gradId = `spark-grad-${itemId}`;
  const hoverPoint = hoverIndex !== null ? pointCoords[hoverIndex] : null;

  const handleMouseMove = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const relativeX = Math.max(0, Math.min(width, (mouseX / rect.width) * width));
    let closestIdx = 0;
    let minDistance = Infinity;

    pointCoords.forEach((p, idx) => {
      const dist = Math.abs(p.x - relativeX);
      if (dist < minDistance) {
        minDistance = dist;
        closestIdx = idx;
      }
    });

    setHoverIndex(closestIdx);
    setTooltipX((pointCoords[closestIdx].x / width) * 100);
  };

  return (
    <div
      className="sparkline-interactive-wrapper"
      onMouseMove={handleMouseMove}
      onMouseLeave={() => setHoverIndex(null)}
    >
      <svg viewBox={`0 0 ${width} ${height}`} className="tracker-sparkline-svg">
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={strokeColor} stopOpacity="0.3" />
            <stop offset="100%" stopColor={strokeColor} stopOpacity="0.0" />
          </linearGradient>
        </defs>
        <path d={fillPath} fill={`url(#${gradId})`} />
        <path
          d={linePath}
          fill="none"
          stroke={strokeColor}
          strokeWidth="2"
          className="tracker-sparkline-path"
        />

        {/* Pulse dot on current value when idle */}
        {hoverIndex === null && (
          <circle
            cx={pointCoords[pointCoords.length - 1].x}
            cy={pointCoords[pointCoords.length - 1].y}
            r="3.5"
            fill={strokeColor}
            className="tracker-sparkline-dot"
          />
        )}

        {/* Active hover indicator */}
        {hoverPoint && (
          <g>
            <line
              x1={hoverPoint.x}
              y1="0"
              x2={hoverPoint.x}
              y2={height}
              stroke="rgba(255, 255, 255, 0.4)"
              strokeWidth="1"
              strokeDasharray="2,2"
            />
            <circle
              cx={hoverPoint.x}
              cy={hoverPoint.y}
              r="4.5"
              fill="#ffffff"
              stroke={strokeColor}
              strokeWidth="2"
            />
          </g>
        )}
      </svg>

      {/* Floating Hover Tooltip Box */}
      {hoverPoint && (
        <div
          className="sparkline-tooltip-box"
          style={{ left: `${Math.min(85, Math.max(15, tooltipX))}%` }}
        >
          <div className="tooltip-label">{hoverPoint.label}</div>
          <div className="tooltip-price">{formatCurrency(hoverPoint.price)}</div>
        </div>
      )}
    </div>
  );
}

/**
 * Detailed Large Interactive Chart Component
 */
function DetailedInteractiveChart({ points }) {
  const [hoverIdx, setHoverIdx] = useState(null);

  if (!points || points.length === 0) return null;

  const prices = points.map((p) => p.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;

  const width = 800;
  const height = 200;
  const paddingX = 40;
  const paddingY = 25;
  const usableW = width - paddingX * 2;
  const usableH = height - paddingY * 2;

  const coords = points.map((p, idx) => {
    const x = points.length === 1 ? width / 2 : paddingX + (idx / (points.length - 1)) * usableW;
    const y = height - paddingY - ((p.price - min) / range) * usableH;
    return { ...p, x, y };
  });

  const linePath = coords
    .map((p, idx) => `${idx === 0 ? "M" : "L"}${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
    .join(" ");

  const fillPath = `${linePath} L${coords[coords.length - 1].x.toFixed(1)} ${height - paddingY} L${coords[0].x.toFixed(1)} ${height - paddingY} Z`;

  const isPos = prices[prices.length - 1] >= prices[0];
  const strokeColor = isPos ? "var(--tracker-green)" : "var(--tracker-red)";

  const handleMouseMove = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const relX = Math.max(0, Math.min(width, (mouseX / rect.width) * width));
    let closest = 0;
    let minDist = Infinity;
    coords.forEach((p, idx) => {
      const d = Math.abs(p.x - relX);
      if (d < minDist) {
        minDist = d;
        closest = idx;
      }
    });
    setHoverIdx(closest);
  };

  const activePoint = hoverIdx !== null ? coords[hoverIdx] : null;

  return (
    <div className="detailed-chart-wrapper mb-4">
      <div className="d-flex justify-content-between align-items-center mb-2 px-1">
        <span className="small text-muted">Hover chart line to view price timeline</span>
        {activePoint && (
          <div className="active-point-badge">
            <span className="text-muted me-2">{activePoint.label}:</span>
            <strong className="text-white">{formatCurrency(activePoint.price)}</strong>
          </div>
        )}
      </div>
      <div
        className="detailed-chart-container"
        onMouseMove={handleMouseMove}
        onMouseLeave={() => setHoverIdx(null)}
      >
        <svg viewBox={`0 0 ${width} ${height}`} className="detailed-chart-svg">
          <defs>
            <linearGradient id="detail-grad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={strokeColor} stopOpacity="0.3" />
              <stop offset="100%" stopColor={strokeColor} stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          <line x1={paddingX} y1={paddingY} x2={width - paddingX} y2={paddingY} stroke="rgba(255,255,255,0.06)" strokeDasharray="4,4" />
          <line x1={paddingX} y1={height / 2} x2={width - paddingX} y2={height / 2} stroke="rgba(255,255,255,0.06)" strokeDasharray="4,4" />
          <line x1={paddingX} y1={height - paddingY} x2={width - paddingX} y2={height - paddingY} stroke="rgba(255,255,255,0.12)" />

          {/* Graph paths */}
          <path d={fillPath} fill="url(#detail-grad)" />
          <path d={linePath} fill="none" stroke={strokeColor} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />

          {/* Data nodes */}
          {coords.map((c, idx) => (
            <circle
              key={idx}
              cx={c.x}
              cy={c.y}
              r={hoverIdx === idx ? "6" : "3.5"}
              fill={hoverIdx === idx ? "#ffffff" : strokeColor}
              stroke={strokeColor}
              strokeWidth="2"
            />
          ))}

          {/* Crosshair guide line */}
          {activePoint && (
            <line
              x1={activePoint.x}
              y1={paddingY}
              x2={activePoint.x}
              y2={height - paddingY}
              stroke="rgba(255,255,255,0.5)"
              strokeWidth="1.5"
              strokeDasharray="3,3"
            />
          )}
        </svg>
      </div>
    </div>
  );
}

export default function PriceTracker() {
  const [query, setQuery] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [selectedSearchItem, setSelectedSearchItem] = useState(null);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [trackedItems, setTrackedItems] = useState([]);
  const [historyMap, setHistoryMap] = useState({});
  const [selectedItemId, setSelectedItemId] = useState(null);
  
  // Global Timeframe selection ("24H", "7D", "1M", "3M")
  const [selectedTimeframe, setSelectedTimeframe] = useState("1M");

  // Debounced search for CS items in database
  useEffect(() => {
    const controller = new AbortController();
    const searchTerm = query.trim();

    if (!searchTerm || (selectedSearchItem && selectedSearchItem.name === searchTerm)) {
      setSearchResults([]);
      return;
    }

    const timeout = setTimeout(async () => {
      try {
        const results = await UserService.searchCsItems(searchTerm, {
          signal: controller.signal,
        });
        setSearchResults(Array.isArray(results) ? results : []);
      } catch (err) {
        if (err.name !== "CanceledError" && err.name !== "AbortError") {
          console.error("searchCsItems error", err);
        }
      }
    }, 300);

    return () => {
      clearTimeout(timeout);
      controller.abort();
    };
  }, [query, selectedSearchItem]);

  const loadTrackedItems = async () => {
    setLoading(true);
    setError("");
    try {
      const response = await UserService.getTrackedItems();
      const items = Array.isArray(response) ? response : [];
      const validItems = items.filter((item) => item.steamFoundCheck === true);
      setTrackedItems(validItems);
      // Fetch history for all tracked items
      await Promise.all(validItems.map((item) => fetchHistory(item.id, true)));
    } catch (err) {
      setError("Unable to load tracked items. Please try again.");
      console.error("PriceTracker loadTrackedItems", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTrackedItems();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchHistory = async (itemId, force = false) => {
    if (!itemId || (!force && historyMap[itemId])) return;
    try {
      const result = await UserService.getPriceHistoryByItem(itemId);
      setHistoryMap((prev) => ({
        ...prev,
        [itemId]: Array.isArray(result) ? result : [],
      }));
    } catch (err) {
      console.error("Error fetching price history", err);
    }
  };

  const handleAddItem = async () => {
    setStatus("");
    setError("");

    if (!selectedSearchItem) {
      setError("Please select an item from the suggestions list first.");
      return;
    }

    setLoading(true);
    try {
      const payload = {
        itemName: selectedSearchItem.name || selectedSearchItem.marketHashName,
        marketHashName: selectedSearchItem.marketHashName,
        csItemId: selectedSearchItem.id,
        quantity: 1,
        showInDashboard: true,
        priceNotification: 0,
        steamFoundCheck: true,
      };

      const added = await UserService.addSteamTrackerItem(payload);
      const displayName = selectedSearchItem.name || selectedSearchItem.marketHashName;
      setStatus(`Now tracking: ${displayName}!`);
      setQuery("");
      setSelectedSearchItem(null);
      setSearchResults([]);
      await loadTrackedItems();
      if (added?.id) {
        await fetchHistory(added.id, true);
      }
    } catch (err) {
      const msg = err?.error || err?.message || "Failed to add item. Please try again.";
      setError(msg);
      console.error("handleAddItem error", err);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (itemId, itemName) => {
    if (!window.confirm(`Are you sure you want to stop tracking "${itemName}"?`)) {
      return;
    }
    setStatus("");
    setError("");
    setLoading(true);
    try {
      await UserService.deleteTrackedItem(itemId);
      setStatus(`Stopped tracking "${itemName}".`);
      if (selectedItemId === itemId) {
        setSelectedItemId(null);
      }
      setTrackedItems((prev) => prev.filter((item) => item.id !== itemId));
    } catch (err) {
      setError(err?.error || err?.message || "Unable to stop tracking item.");
      console.error("handleDelete error", err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectItem = async (itemId) => {
    setSelectedItemId(itemId);
    await fetchHistory(itemId);
  };

  const currentSelectedHistory = useMemo(
    () => historyMap[selectedItemId] || [],
    [historyMap, selectedItemId]
  );

  const selectedItemMeta = useMemo(() => {
    return trackedItems.find((item) => item.id === selectedItemId);
  }, [trackedItems, selectedItemId]);

  const selectedItemPoints = useMemo(() => {
    if (!selectedItemMeta) return [];
    return getItemPoints(selectedItemMeta, currentSelectedHistory, selectedTimeframe);
  }, [selectedItemMeta, currentSelectedHistory, selectedTimeframe]);

  return (
    <div className="tracker-page-wrapper">
      <div className="container py-4">
        <div className="row justify-content-center">
          <div className="col-12 col-xl-11">
            
            {/* Header & Subtitle */}
            <div className="d-flex justify-content-between align-items-center mb-4">
              <div>
                <h1 className="tracker-title m-0">Steam Price Tracker</h1>
                <p className="tracker-copy m-0 mt-1">
                  Monitor skin prices and view historical trends from the Steam Community Market.
                </p>
              </div>
              <button
                className="tracker-btn-primary d-flex align-items-center gap-2"
                onClick={loadTrackedItems}
                disabled={loading}
              >
                {loading ? <span className="tracker-spinner"></span> : null}
                Refresh Prices
              </button>
            </div>

            {/* Notifications */}
            {status && (
              <div className="tracker-alert tracker-alert-success d-flex align-items-center gap-2">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                  <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>
                <span>{status}</span>
              </div>
            )}
            {error && (
              <div className="tracker-alert tracker-alert-danger d-flex align-items-center gap-2">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <span>{error}</span>
              </div>
            )}

            {/* Search / Track Control Panel */}
            <div className="tracker-card p-4 mb-4">
              <div className="row align-items-center">
                <div className="col-12 col-md-8 col-lg-9 mb-3 mb-md-0">
                  <div className="tracker-search-container">
                    <input
                      type="text"
                      className="form-control tracker-search"
                      placeholder="Type a skin name (e.g. AWP | Dragon Lore)..."
                      value={query}
                      onChange={(e) => {
                        setQuery(e.target.value);
                        setSelectedSearchItem(null);
                      }}
                      disabled={loading}
                    />
                    
                    {searchResults.length > 0 && (
                      <div className="tracker-suggestions list-group position-absolute w-100">
                        {searchResults.slice(0, 8).map((item) => (
                          <button
                            key={item.id || item.marketHashName || item.name}
                            type="button"
                            className="list-group-item list-group-item-action d-flex align-items-center"
                            onClick={() => {
                              setQuery(item.name || item.marketHashName || "");
                              setSelectedSearchItem(item);
                              setSearchResults([]);
                            }}
                          >
                            {item.image ? (
                              <img
                                src={item.image}
                                alt={item.name}
                                className="tracker-suggestion-image"
                              />
                            ) : (
                              <div className="tracker-suggestion-image d-flex align-items-center justify-content-center bg-dark text-white rounded">
                                📷
                              </div>
                            )}
                            <span>{item.name || item.marketHashName}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                <div className="col-12 col-md-4 col-lg-3">
                  <button
                    className="tracker-btn-primary w-100 py-3"
                    onClick={handleAddItem}
                    disabled={loading || !query.trim()}
                  >
                    Track Item
                  </button>
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

            {/* Tracked Items Table */}
            <div className="tracker-table-container">
              <table className="tracker-table">
                <thead>
                  <tr>
                    <th>Skin</th>
                    <th>Name</th>
                    <th>Current Price</th>
                    <th>{selectedTimeframe} Change</th>
                    <th className="tracker-sparkline-cell">Trend ({selectedTimeframe})</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {trackedItems.length > 0 ? (
                    trackedItems.map((item) => {
                      const history = historyMap[item.id] || [];
                      const points = getItemPoints(item, history, selectedTimeframe);
                      const currentPrice = item.price ?? (points[points.length - 1]?.price || 0);
                      const { pct, direction } = getPeriodChange(points);
                      const strokeColor = direction === "negative" ? "var(--tracker-red)" : "var(--tracker-green)";

                      return (
                        <tr
                          key={item.id}
                          className={`tracker-row ${selectedItemId === item.id ? "selected-row" : ""}`}
                          onClick={() => handleSelectItem(item.id)}
                        >
                          <td>
                            {item.image ? (
                              <div className="tracker-img-wrapper">
                                <img src={item.image} alt={item.itemName} className="tracker-img" />
                              </div>
                            ) : (
                              <div className="tracker-img-wrapper">
                                <div className="tracker-image-placeholder">📷</div>
                              </div>
                            )}
                          </td>
                          <td className="tracker-name-cell">
                            {item.itemName || item.marketHashName || "Unnamed skin"}
                          </td>
                          <td className="tracker-price-cell">
                            {formatCurrency(currentPrice)}
                          </td>
                          <td>
                            <span className={`change-badge ${direction}`}>
                              {pct > 0 ? (
                                <>▲ +{pct.toFixed(2)}%</>
                              ) : pct < 0 ? (
                                <>▼ {pct.toFixed(2)}%</>
                              ) : (
                                <>0.00%</>
                              )}
                            </span>
                          </td>
                          <td className="tracker-sparkline-cell">
                            <InteractiveSparkline
                              itemId={item.id}
                              points={points}
                              strokeColor={strokeColor}
                            />
                          </td>
                          <td>
                            <button
                              className="btn-delete-track"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDelete(item.id, item.itemName || item.marketHashName);
                              }}
                              title="Stop Tracking"
                            >
                              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                <line x1="10" y1="11" x2="10" y2="17"></line>
                                <line x1="14" y1="11" x2="14" y2="17"></line>
                              </svg>
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan="6" className="text-center py-5 text-muted">
                        {loading ? "Fetching tracked items..." : "No items are currently tracked. Search above to add one!"}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Detailed Price History Panel */}
            {selectedItemId && selectedItemMeta && (
              <div className="history-panel-card p-4">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <h3 className="history-panel-title m-0">
                    Price History details: {selectedItemMeta.itemName || selectedItemMeta.marketHashName}
                  </h3>
                  <span className="badge bg-secondary">{selectedTimeframe} View</span>
                </div>

                {/* Large Interactive Chart */}
                <DetailedInteractiveChart points={selectedItemPoints} />

                {/* Details Table */}
                <div className="table-responsive">
                  <table className="tracker-table history-details-table">
                    <thead>
                      <tr>
                        <th>Date & Time / Period</th>
                        <th>Price</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedItemPoints.slice().reverse().map((point, idx) => (
                        <tr key={idx}>
                          <td>{point.label}</td>
                          <td className="tracker-price-cell">
                            {formatCurrency(point.price)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

          </div>
        </div>
      </div>
    </div>
  );
}
