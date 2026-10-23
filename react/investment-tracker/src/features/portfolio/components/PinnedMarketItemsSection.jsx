import PriceTimeframeSelector from "../../../shared/components/PriceTimeframeSelector";
import { Table } from "react-bootstrap";
import { PriceSparkline } from "../../../shared/components/PriceCharts";
import { buildDailyPricePoints as getItemPoints, getPeriodChange } from "../../../shared/utils/priceHistory";
import PriceValue from "../../../shared/components/PriceValue";
import RequestError from "../../../shared/components/RequestError";
import PriceTrend from "../../../shared/components/PriceTrend";

export default function PinnedMarketItemsSection({ allPinnedItems, visiblePinnedItems, selectedTimeframe, setSelectedTimeframe, pinnedHistories, hasMore, loaderRef, handleUnpin, navigate, loading, error, actionError, onRetry, isUnpinPending }) {
  return (
      <div className="card portfolio-table-card shadow-lg p-4">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
          <h3 className="portfolio-table-title mb-0">Pinned Market Items</h3>

          {/* Trend Period Control Group in Header */}
          <PriceTimeframeSelector selectedTimeframe={selectedTimeframe} onSelect={setSelectedTimeframe} />
        </div>

        <RequestError message={actionError} />
        <RequestError message={error} onRetry={onRetry} retryLabel="Retry pinned items" pending={loading} />
        {loading && allPinnedItems.length === 0 ? <div className="p-4 text-secondary" role="status">Loading pinned items...</div> : error && allPinnedItems.length === 0 ? null : allPinnedItems.length === 0 ? (
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
                  const history = pinnedHistories.histories[item.id] || [];
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
                          aria-label={`Unpin ${item.itemName}`}
                          disabled={isUnpinPending?.(item.id)}
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
                        <button type="button" className="fw-semibold text-white item-name-action" onClick={event => { event.stopPropagation(); navigate("/tracker"); }}>{item.itemName}</button>
                      </td>
                      <td className="font-mono fw-bold text-success">
                        <PriceValue item={item} />
                      </td>
                      <td>
                        <PriceTrend trend={trend} points={points} />
                      </td>
                      <td>
                        <PriceSparkline emptyClassName="" points={points} strokeColor={strokeColor} state={pinnedHistories.states[item.id] || { status: "loading" }} onRetry={() => pinnedHistories.retry(item.id)} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>

            {/* Infinite Scroll Sentinel / Loader */}
            {hasMore && (
              <div ref={loaderRef} className="text-center py-3 text-muted small">
                {loading ? "Loading more pinned items..." : "Scroll to load more pinned items"}
              </div>
            )}
          </div>
        )}
      </div>
  );
}
