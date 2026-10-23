import { DetailedPriceChart } from "../../../shared/components/PriceCharts";
import { formatCurrency } from "../../../shared/utils/formatCurrency";
import { formatPriceDateTime as formatDateDDMMYYYY } from "../../../shared/utils/priceHistory";
import { Modal } from "react-bootstrap";
import RequestError from "../../../shared/components/RequestError";
import PriceTrend from "../../../shared/components/PriceTrend";
import PriceValue from "../../../shared/components/PriceValue";

export default function TrackerDetailModal({ detailItem, setDetailItem, detailTrend, detailPoints, detailHistoryList, historyState }) {
  return (
      detailItem && (
        <Modal show onHide={() => setDetailItem(null)} centered className="tracker-modal" backdropClassName="tracker-modal-backdrop"
          style={{ zIndex: 999999 }} aria-labelledby="tracker-detail-title" restoreFocusOptions={{ preventScroll: true }}
          contentClassName="modal-content-custom card tracker-card-dark p-4 position-relative">
            <button
              type="button"
              className="btn-close btn-close-white position-absolute top-0 end-0 m-3"
              onClick={() => setDetailItem(null)}
              title="Close Detail View"
              aria-label="Close detail view"
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
                <h4 id="tracker-detail-title" className="text-white mb-1 fw-bold">{detailItem.itemName}</h4>
                <div className="d-flex align-items-center gap-3 mt-2">
                  <span className="fs-4 fw-bold text-success"><PriceValue item={detailItem} /></span>
                  <PriceTrend trend={detailTrend} points={detailPoints} />
                </div>
              </div>
            </div>

            {historyState.states[detailItem.id]?.status === "error"
              ? <RequestError message={historyState.states[detailItem.id].error} onRetry={() => historyState.retry(detailItem.id)} retryLabel="Retry price history" />
              : historyState.states[detailItem.id]?.status !== "success" && !detailHistoryList.length
              ? <div className="py-4 text-secondary" role="status">Loading price history...</div>
              : <DetailedPriceChart points={detailPoints} />}

            {/* Historical Price Log Table (2 Clean Columns: Date Recorded & Market Price) */}
            <div className="mt-4">
              <h5 className="text-white fw-bold mb-3">📋 Historical Price Log</h5>
              {historyState.states[detailItem.id]?.status !== "success" && !detailHistoryList.length ? null : detailHistoryList.length === 0 ? (
                <div className="text-muted small p-3 bg-dark rounded text-center">
                  No price history yet.
                </div>
              ) : (
                <div className="table-responsive rounded border border-secondary border-opacity-25" style={{ maxHeight: "250px", overflowY: "auto" }}>
                  <table className="table table-dark table-sm table-striped table-hover align-middle mb-0">
                    <thead>
                      <tr>
                        <th scope="col" className="ps-3">Observation Date (UTC)</th>
                        <th scope="col" className="text-end pe-3">Recorded Market Price (€)</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detailHistoryList.map((h) => (
                        <tr key={h.id || h.dateExtracted}>
                          <td className="ps-3 small text-secondary" title={h.sourcePublishedAt ? "Source publication date" : "First observed date; the source publication date was not recorded"}>
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
        </Modal>
      )
  );
}
