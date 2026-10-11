import React from "react";
import { Table } from "react-bootstrap";
import { PriceSparkline } from "../prices/PriceCharts";
import { buildDailyPricePoints as getItemPoints, getPeriodChange } from "../../utils/priceHistory";
import { formatCurrency } from "../../utils/formatCurrency";

export default function PinnedMarketItemsSection({ allPinnedItems, visiblePinnedItems, selectedTimeframe, setSelectedTimeframe, pinnedHistories, pinnedVisibleCount, loaderRef, handleUnpin, navigate }) {
  return (
      <div className="card portfolio-table-card shadow-lg p-4">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
          <h3 className="portfolio-table-title mb-0">Pinned Market Items</h3>

          {/* Trend Period Control Group in Header */}
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
                  const points = getItemPoints(history, selectedTimeframe);
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
                          {trend.pct == null
                            ? "—"
                            : `${trend.direction === "positive" ? "▲ +" : trend.direction === "negative" ? "▼ " : ""}${trend.pct.toFixed(2)}%`}
                        </span>
                      </td>
                      <td>
                        <PriceSparkline emptyClassName="" points={points} strokeColor={strokeColor} />
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
  );
}
