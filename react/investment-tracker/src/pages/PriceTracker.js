import React, { useEffect, useMemo, useState } from "react";
import { Button, Table } from "react-bootstrap";
import UserService from "../service/UserService";
import "../styles/PriceTracker.css";

const formatCurrency = (value) => {
  if (value == null || Number.isNaN(Number(value))) return "-";
  return `$${Number(value).toFixed(2)}`;
};

const buildSparklinePath = (history = []) => {
  if (!history.length) return "";

  const values = history
    .map((point) => Number(point.price) || 0)
    .filter((price) => !Number.isNaN(price));

  if (!values.length) return "";

  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue || 1;
  const points = history.slice(-8).map((point, index) => {
    const price = Number(point.price) || 0;
    const x = history.length === 1 ? 70 : (index / (history.length - 1)) * 140;
    const y = 36 - ((price - minValue) / range) * 32;
    return { x, y };
  });

  return points
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x} ${point.y}`)
    .join(" ");
};

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

  useEffect(() => {
    const controller = new AbortController();
    const searchTerm = query.trim();

    if (!searchTerm) {
      setSearchResults([]);
      setSelectedSearchItem(null);
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
  }, [query]);

  const loadTrackedItems = async () => {
    setLoading(true);
    setError("");
    try {
      const response = await UserService.getTrackedItems();
      const items = Array.isArray(response) ? response : [];
      const validItems = items.filter((item) => item.marketHashName || item.itemName);
      setTrackedItems(validItems);
      await Promise.all(validItems.slice(0, 8).map((item) => fetchHistory(item.id, true)));
    } catch (err) {
      setError("Unable to load tracked items. Please try again.");
      console.error("PriceTracker loadTrackedItems", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTrackedItems();
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

  const handleValidate = async () => {
    setStatus("");
    setError("");

    const marketHashName = selectedSearchItem?.marketHashName || selectedSearchItem?.name || query.trim();
    if (!marketHashName) {
      setError("Enter an item name first.");
      return;
    }

    setLoading(true);
    try {
      const validation = await UserService.validateSteamItem(marketHashName);
      if (validation && validation.exists) {
        setStatus(`Item found: ${validation.marketHashName}`);
      } else {
        setError("Steam item not found. Please check the name.");
      }
    } catch (err) {
      setError(err?.message || "Validation failed. Please try again.");
      console.error("validateSteamItem error", err);
    } finally {
      setLoading(false);
    }
  };

  const handleAddItem = async () => {
    setStatus("");
    setError("");

    const marketHashName = selectedSearchItem?.marketHashName || selectedSearchItem?.name || query.trim();
    if (!marketHashName) {
      setError("Enter an item name before adding.");
      return;
    }

    setLoading(true);
    try {
      const validation = await UserService.validateSteamItem(marketHashName);
      if (!validation || !validation.exists) {
        setError(
          "Steam item could not be validated. Please verify the item name.",
        );
        return;
      }

      const payload = {
        itemName: validation.marketHashName,
        marketHashName: validation.marketHashName,
        csItemId: selectedSearchItem?.id || null,
        quantity: 1,
        price: 0,
        boughtPrice: 0,
        boughtDate: new Date().toISOString().split("T")[0],
        showInDashboard: true,
        priceNotification: 0,
        steamFoundCheck: true,
        image: selectedSearchItem?.image || null,
      };

      const added = await UserService.addSteamTrackerItem(payload);
      setStatus(`Added ${validation.marketHashName} to tracking list.`);
      setQuery("");
      setSelectedSearchItem(null);
      setSearchResults([]);
      await loadTrackedItems();
      if (added?.id) {
        await fetchHistory(added.id);
      }
    } catch (err) {
      setError(err?.message || "Unable to add item. Please try again.");
      console.error("handleAddItem error", err);
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
    [historyMap, selectedItemId],
  );

  return (
    <div className="container tracker-page py-4">
      <div className="row justify-content-center">
        <div className="col-12 col-lg-10">
          <div className="tracker-card p-4 rounded shadow-sm">
            <h2 className="tracker-title">Steam Price Tracker</h2>
            <p className="tracker-copy">
              Search for a Steam market item name, validate it, then add it to
              your tracker.
            </p>

            <div className="d-flex flex-column flex-md-row gap-2 align-items-start mb-3">
              <div className="position-relative flex-grow-1">
                <input
                  type="text"
                  className="form-control tracker-search"
                  placeholder="Search item name from database..."
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setSelectedSearchItem(null);
                  }}
                  disabled={loading}
                />
                {searchResults.length > 0 && (
                  <div className="tracker-suggestions list-group position-absolute w-100">
                    {searchResults.slice(0, 6).map((item) => (
                      <button
                        key={item.id || item.marketHashName || item.name}
                        type="button"
                        className="list-group-item list-group-item-action d-flex align-items-center"
                        onClick={() => {
                          setQuery(item.marketHashName || item.name || "");
                          setSelectedSearchItem(item);
                          setSearchResults([]);
                        }}
                      >
                        {item.image && (
                          <img
                            src={item.image}
                            alt={item.name || item.marketHashName}
                            className="tracker-suggestion-image"
                          />
                        )}
                        <span>{item.marketHashName || item.name}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <Button
                variant="outline-primary"
                onClick={handleValidate}
                disabled={loading || !query.trim()}
              >
                Validate Steam Item
              </Button>
              <Button
                variant="primary"
                onClick={handleAddItem}
                disabled={loading || !query.trim()}
              >
                Add Steam Item
              </Button>
            </div>

            {status && <div className="alert alert-success">{status}</div>}
            {error && <div className="alert alert-danger">{error}</div>}

            <div className="d-flex justify-content-between align-items-center mb-3">
              <h3 className="tracker-subtitle">Tracked Steam Items</h3>
              <Button variant="secondary" size="sm" onClick={loadTrackedItems}>
                Refresh
              </Button>
            </div>

            <div className="table-responsive">
              <Table bordered hover size="sm" className="tracker-table">
                <thead>
                  <tr>
                    <th>Image</th>
                    <th>Name</th>
                    <th>Current Price</th>
                    <th>Quantity</th>
                    <th>History</th>
                  </tr>
                </thead>
                <tbody>
                  {trackedItems.length > 0 ? (
                    trackedItems.map((item) => {
                      const history = historyMap[item.id] || [];
                      const price = item.price ?? history[0]?.price ?? 0;
                      return (
                        <tr
                          key={item.id}
                          className={
                            selectedItemId === item.id ? "selected-row" : ""
                          }
                          onClick={() => handleSelectItem(item.id)}
                        >
                          <td className="tracker-image-cell">
                            {item.image ? (
                              <img src={item.image} alt={item.itemName} />
                            ) : (
                              <div className="tracker-image-placeholder">
                                No image
                              </div>
                            )}
                          </td>
                          <td>
                            {item.itemName ||
                              item.marketHashName ||
                              "Unnamed item"}
                          </td>
                          <td>{formatCurrency(price)}</td>
                          <td>{item.quantity ?? 1}</td>
                          <td className="tracker-chart-cell">
                            {history.length ? (
                              <svg
                                viewBox="0 0 140 40"
                                className="tracker-sparkline"
                              >
                                <path
                                  d={buildSparklinePath(history)}
                                  fill="none"
                                  stroke="#1f4e78"
                                  strokeWidth="2"
                                />
                              </svg>
                            ) : (
                              <span className="tracker-chart-empty">
                                No history
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan="5" className="text-center py-3">
                        {loading
                          ? "Loading tracked items..."
                          : "No tracked items yet."}
                      </td>
                    </tr>
                  )}
                </tbody>
              </Table>
            </div>

            {selectedItemId && currentSelectedHistory.length > 0 && (
              <div className="history-panel mt-4 p-3 rounded bg-white border">
                <h4>Price history for selected item</h4>
                <div className="history-labels mb-2">
                  <span>{currentSelectedHistory.length} points</span>
                  <span>
                    Latest: {formatCurrency(currentSelectedHistory[0]?.price)}
                  </span>
                </div>
                <Table size="sm" bordered>
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Price</th>
                      <th>Quantity</th>
                      <th>Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {currentSelectedHistory.slice(0, 8).map((point) => (
                      <tr
                        key={point.id || `${point.dateAssigned}-${point.price}`}
                      >
                        <td>
                          {new Date(point.dateAssigned).toLocaleDateString()}
                        </td>
                        <td>{formatCurrency(point.price)}</td>
                        <td>{point.quantity ?? 1}</td>
                        <td>
                          {formatCurrency(
                            point.totalPrice ??
                              point.price * (point.quantity ?? 1),
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
