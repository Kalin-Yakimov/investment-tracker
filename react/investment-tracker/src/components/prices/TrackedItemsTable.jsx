import React from "react";
import { PriceSparkline } from "./PriceCharts";
import { formatCurrency } from "../../utils/formatCurrency";

export default function TrackedItemsTable({ trackedItems, selectedTimeframe, itemPointsMap, itemTrendsMap, setDetailItem, handleToggleBookmark, setDeleteConfirmId, pending }) {
  return (
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
                            disabled={pending}
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
                          <PriceSparkline points={points} strokeColor={strokeColor} />
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
                            disabled={pending}
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
  );
}
