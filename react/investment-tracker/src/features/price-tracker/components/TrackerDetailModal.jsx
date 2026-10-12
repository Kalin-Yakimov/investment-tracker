import React from "react";
import { DetailedPriceChart } from "../../../shared/components/PriceCharts";
import { formatCurrency } from "../../../shared/utils/formatCurrency";
import { formatPriceDateTime as formatDateDDMMYYYY } from "../../../shared/utils/priceHistory";

export default function TrackerDetailModal({ detailItem, setDetailItem, detailTrend, detailPoints, detailHistoryList }) {
  return (
      detailItem && (
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

            <DetailedPriceChart points={detailPoints} />

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
                        <th scope="col" className="ps-3">Publication Date (UTC)</th>
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
      )
  );
}
