import React, { useState } from "react";
import UserService from "../service/UserService";
import useRequestScope from "../hooks/useRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import {
  loadInventoryCalculatorSession,
  saveInventoryCalculatorSession,
} from "../service/InventoryCalculatorSession";
import "../styles/InventoryCalculatorPage.css";

const formatPrice = (value) =>
  value != null && value !== "" && Number.isFinite(Number(value))
    ? `€${Number(value).toFixed(2)}`
    : "N/A";

function InventoryCalculatorPage() {
  const scope = useRequestScope();
  const [savedCalculation] = useState(() =>
    loadInventoryCalculatorSession(UserService.getCurrentUser()?.id)
  );
  const [steamUrl, setSteamUrl] = useState(savedCalculation?.steamUrl || "");
  const loading = scope.isPending("calculate");
  const [error, setError] = useState(null);
  const [privacyError, setPrivacyError] = useState(false);
  const [result, setResult] = useState(savedCalculation?.result || null);

  const [activeCategory, setActiveCategory] = useState("All");
  const hasUnpricedItems = (result?.unpricedItemCount || 0) > 0;

  const handleCalculate = async (e) => {
    if (e) e.preventDefault();
    const requestedSteamUrl = steamUrl.trim();
    if (!requestedSteamUrl) return;
    const userId = UserService.getCurrentUser()?.id;
    const request = scope.begin("calculate", { replace: false });
    if (!request) return;
    setError(null);
    setPrivacyError(false);

    try {
      const data = await UserService.calculateSteamInventory(requestedSteamUrl, { signal: request.signal });
      if (!request.isCurrent()) return;
      setResult(data);
      setActiveCategory("All");
      saveInventoryCalculatorSession(userId, requestedSteamUrl, data);
    } catch (err) {
      if (!request.isCurrent() || isRequestCanceled(err)) return;
      console.error("Error calculating inventory:", err);
      const errMsg = err.error || err.message || "Failed to calculate inventory value";
      
      if (err.code === "PRIVACY_PRIVATE" || errMsg.toLowerCase().includes("private")) {
        setPrivacyError(true);
        setError("Steam inventory is set to Private or Friends Only.");
      } else {
        setError(errMsg);
      }
    } finally {
      request.finish();
    }
  };

  // Filter items by active category pill
  const filteredItems = result?.items
    ? result.items.filter((item) => {
        return activeCategory === "All" || item.typeCategory === activeCategory;
      })
    : [];

  const categories = [
    "All",
    "Weapon Skins",
    "Cases",
    "Stickers & Patches",
    "Capsules",
    "Knives & Gloves",
    "Souvenirs",
    "Other",
  ];

  return (
    <div className="container py-4 pb-5" style={{ maxWidth: "1140px" }}>
      {/* Top Hero / Search Card */}
      <div className="card calc-hero-card p-4 p-md-5 mb-4">
        <div className="text-center max-w-2xl mx-auto mb-4">
          <h2 className="calc-title mb-2 fs-1">
            CS2 Steam Inventory Calculator
          </h2>
          <p className="calc-subtitle">
            Paste a Steam profile link or SteamID to estimate the value of a public CS2 inventory.
          </p>
        </div>

        {/* Input Form */}
        <form onSubmit={handleCalculate} className="mb-4">
          <div className="input-group calc-input-group">
            <input
              type="text"
              className="form-control calc-input"
              placeholder="Paste Steam Profile URL or SteamID64 (e.g. https://steamcommunity.com/id/gabelogannewell)"
              value={steamUrl}
              onChange={(e) => setSteamUrl(e.target.value)}
              disabled={loading}
            />
            <button
              type="submit"
              className="btn calc-btn d-flex align-items-center gap-2"
              disabled={loading || !steamUrl.trim()}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  Calculating...
                </>
              ) : (
                <>🔍 Calculate Value</>
              )}
            </button>
          </div>
        </form>

        {/* Public Inventory Privacy Notice Banner */}
        <div className="privacy-notice-box p-3 d-flex align-items-start gap-3">
          <span className="fs-5">ℹ️</span>
          <div>
            <strong className="text-white">Steam Privacy Requirement:</strong>
            <div className="small mt-1 text-white-50">
              Steam requires your profile's <strong>Inventory Privacy</strong> to be set to <strong>Public</strong> to view items.
              To check or update yours: Go to your <strong>Steam Profile → Edit Profile → Privacy Settings → Set "Inventory" to Public</strong>.
            </div>
          </div>
        </div>
      </div>

      {/* Error Message Card */}
      {error && (
        <div className={`card p-4 mb-4 ${privacyError ? "border-warning bg-dark" : "border-danger bg-dark"}`}>
          <div className="d-flex align-items-center gap-3">
            <span className="fs-2">{privacyError ? "🔒" : "⚠️"}</span>
            <div>
              <h5 className={privacyError ? "text-warning mb-1" : "text-danger mb-1"}>
                {privacyError ? "Steam Inventory is Private" : "Calculation Error"}
              </h5>
              <p className="text-white-50 mb-0 small">{error}</p>
              {result && (
                <p className="text-white-50 mb-0 small mt-1">
                  The previous successful result is still shown below.
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Loading Spinner View */}
      {loading && (
        <div className="text-center py-5">
          <div className="spinner-border text-primary mb-3" style={{ width: "3rem", height: "3rem" }} role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
          <h5 className="text-white font-medium">Fetching CS2 Inventory from Steam...</h5>
          <p className="text-white-50 small">Matching item quantities with stored market prices.</p>
        </div>
      )}

      {/* Valuation Results View */}
      {result && !loading && (
        <>
          {/* Profile Header Bar */}
          <div className="card calc-hero-card p-4 mb-4">
            <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
              <div className="d-flex align-items-center gap-3">
                {result.avatarUrl ? (
                  <img src={result.avatarUrl} alt="Avatar" className="profile-avatar-thumb" />
                ) : (
                  <div className="profile-avatar-thumb bg-secondary d-flex align-items-center justify-content-center text-white fs-4">
                    👤
                  </div>
                )}
                <div>
                  <h3 className="text-white fw-bold mb-0">{result.profileName}</h3>
                  <span className="text-white-50 small">SteamID64: {result.steamId64}</span>
                </div>
              </div>

              <div className="text-end">
                <span className="badge bg-success bg-gradient fs-6 px-3 py-2">
                  {hasUnpricedItems ? "Known Value" : "Total Value"}: {formatPrice(result.totalValue)}
                </span>
              </div>
            </div>
          </div>

          {/* 4 Summary Metric Cards */}
          {hasUnpricedItems && (
            <p className="text-white-50 small mb-3" role="status">
              Prices are unavailable for {result.unpricedItemCount} of {result.totalItemCount} items.
              The total includes only items with available prices.
            </p>
          )}
          <div className="row g-3 mb-4">
            <div className="col-12 col-sm-6 col-md-3">
              <div className="calc-stat-card">
                <span className="calc-stat-label">
                  💰 {hasUnpricedItems ? "Estimated Value (priced items)" : "Estimated Inventory Value"}
                </span>
                <div className="calc-stat-value text-success">
                  {formatPrice(result.totalValue)}
                </div>
              </div>
            </div>

            <div className="col-12 col-sm-6 col-md-3">
              <div className="calc-stat-card">
                <span className="calc-stat-label">📦 Total Items Owned</span>
                <div className="calc-stat-value text-info">
                  {result.totalItemCount}
                </div>
              </div>
            </div>

            <div className="col-12 col-sm-6 col-md-3">
              <div className="calc-stat-card">
                <span className="calc-stat-label">🏷️ Unique Item Types</span>
                <div className="calc-stat-value text-warning">
                  {result.uniqueItemsCount}
                </div>
              </div>
            </div>

            <div className="col-12 col-sm-6 col-md-3">
              <div className="calc-stat-card">
                <span className="calc-stat-label">🏆 Most Valuable Item</span>
                <div className="calc-stat-value text-truncate text-primary fs-6 mt-2" title={result.mostValuableItem?.itemName}>
                  {result.mostValuableItem ? (
                    <>
                      <div>{result.mostValuableItem.itemName}</div>
                      <span className="text-success small fw-bold">
                        {formatPrice(result.mostValuableItem.unitPrice)}
                      </span>
                    </>
                  ) : (
                    "N/A"
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Detailed Inventory Table Card */}
          <div className="card calc-hero-card p-4">
            {/* Table Control Header */}
            <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
              {/* Category Filters */}
              <div className="d-flex flex-wrap align-items-center gap-2">
                {categories.map((cat) => (
                  <button
                    key={cat}
                    type="button"
                    className={`category-pill-btn ${activeCategory === cat ? "active" : ""}`}
                    onClick={() => setActiveCategory(cat)}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>

            {/* Inventory Table */}
            {filteredItems.length > 0 ? (
              <div className="table-responsive">
                <table className="table table-dark table-hover align-middle mb-0">
                  <thead>
                    <tr className="text-white-50 small">
                      <th scope="col" style={{ width: "65px" }}>Image</th>
                      <th scope="col">Item Name</th>
                      <th scope="col">Category</th>
                      <th scope="col" className="text-center">Quantity</th>
                      <th scope="col" className="text-end">Unit Price</th>
                      <th scope="col" className="text-end">Total Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredItems.map((item, idx) => (
                      <tr key={idx}>
                        <td>
                          {item.image ? (
                            <img src={item.image} alt={item.itemName} className="inv-item-thumb" />
                          ) : (
                            <div className="inv-item-thumb d-flex align-items-center justify-content-center bg-secondary text-white rounded">
                              🎮
                            </div>
                          )}
                        </td>
                        <td className="fw-semibold text-white">{item.itemName}</td>
                        <td>
                          <span className="category-badge">{item.typeCategory}</span>
                        </td>
                        <td className="text-center font-mono text-white fw-bold">{item.quantity}</td>
                        <td className="text-end font-mono text-white">{formatPrice(item.unitPrice)}</td>
                        <td className="text-end font-mono text-success fw-bold">
                          {formatPrice(item.totalPrice)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="text-center py-4 text-white-50">
                {result.totalItemCount === 0
                  ? "This public CS2 inventory contains no items."
                  : 'No items found matching category "' + activeCategory + '".'}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

export default InventoryCalculatorPage;
