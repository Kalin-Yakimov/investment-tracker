import React, { useEffect, useState, useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import PortfolioService from "../service/PortfolioService";
import useRequestScope from "../hooks/useRequestScope";
import { isRequestCanceled } from "../service/RequestScope";
import { Table } from "react-bootstrap";
import Card from "../utils/Card";

export default function ViewItem() {
  const scope = useRequestScope();
  const [item, setItem] = useState({
    itemName: "",
    itemStatus: "",
    date: "",
    quantity: "",
    price: "",
    image: null,
  });
  const [activeItem, setActiveItem] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const { id } = useParams();
  const itemsPerPage = 10;

  const loadItem = useCallback(async () => {
    const request = scope.begin("item");
    if (!request) return;
    setLoading(true);
    setError("");
    scope.cancel("history");
    try {
      const result = await PortfolioService.getItem(id, { signal: request.signal });
      if (!request.isCurrent()) return;
      setItem(result);
      setActiveItem(result);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) {
        console.error("Error fetching item:", error);
        setError("Item not found");
      }
    } finally {
      if (request.isCurrent()) setLoading(false);
      request.finish();
    }
  }, [id, scope]);

  const loadItemHistory = useCallback(
    async (page = 0) => {
      const request = scope.begin("history");
      if (!request) return;
      setHistoryLoading(true);
      try {
        const result = await PortfolioService.getItemByName(
          item.itemName,
          page,
          itemsPerPage,
          { signal: request.signal },
        );
        if (!request.isCurrent()) return;
        setHistory(result.content || []);
        setHistoryTotalPages(result.totalPages || 0);
        setCurrentPage(page);
      } catch (error) {
        if (request.isCurrent() && !isRequestCanceled(error)) console.error("Error fetching item history:", error);
      } finally {
        if (request.isCurrent()) setHistoryLoading(false);
        request.finish();
      }
    },
    [item.itemName, itemsPerPage, scope],
  );

  useEffect(() => {
    loadItem();
    return () => scope.cancel("item");
  }, [loadItem, scope]);

  useEffect(() => {
    if (item.itemName) {
      loadItemHistory();
    }
    return () => scope.cancel("history");
  }, [item.itemName, loadItemHistory, scope]);

  const deleteItem = async () => {
    const request = scope.begin("delete", { replace: false, abortable: false });
    if (!request) return;
    try {
      const targetId = activeItem?.id || id;
      await PortfolioService.deleteItem(targetId);
      if (request.isCurrent()) window.location.href = "/";
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) console.error("Error deleting item:", error);
    } finally { request.finish(); }
  };

  const handleHistoryPageChange = (page) => {
    if (page === currentPage) return;
    loadItemHistory(page);
  };

  const renderPagination = () => (
    <div className="d-flex align-items-center justify-content-between pt-3 px-2">
      <span className="text-white-50 small">
        Page {currentPage + 1} of {historyTotalPages || 1}
      </span>
      <nav aria-label="History pagination">
        <ul className="pagination pagination-sm mb-0">
          <li className={`page-item ${currentPage === 0 ? "disabled" : ""}`}>
            <button
              className="page-link"
              onClick={() => handleHistoryPageChange(currentPage - 1)}
              disabled={currentPage === 0}
            >
              Previous
            </button>
          </li>
          {Array.from({ length: historyTotalPages }, (_, index) => (
            <li
              key={index}
              className={`page-item ${index === currentPage ? "active" : ""}`}
            >
              <button
                className="page-link"
                onClick={() => handleHistoryPageChange(index)}
              >
                {index + 1}
              </button>
            </li>
          ))}
          <li
            className={`page-item ${
              currentPage >= historyTotalPages - 1 ? "disabled" : ""
            }`}
          >
            <button
              className="page-link"
              onClick={() => handleHistoryPageChange(currentPage + 1)}
              disabled={currentPage >= historyTotalPages - 1}
            >
              Next
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );

  if (loading) {
    return (
      <div className="container mt-5 text-center">
        <div className="alert alert-info py-3">⚡ Loading item details...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container mt-5 text-center">
        <div className="alert alert-danger mb-3" role="alert">
          {error}
        </div>
        <Link className="btn btn-primary" to={"/"}>
          Back to Portfolio
        </Link>
      </div>
    );
  }

  const currentItem = activeItem || item;
  const isBought = currentItem.itemStatus === "BOUGHT";
  const totalPrice = Number(currentItem.price || 0) * Number(currentItem.quantity || 1);

  return (
    <div className="container mt-4 mb-5">
      <div className="card shadow-lg p-4">
        {/* Header Title */}
        <div className="d-flex align-items-center justify-content-between mb-4">
          <h2 className="text-white mb-0">Item Details</h2>
        </div>

        {/* Thumbnail & Key Metrics Row */}
        <div className="row g-4 mb-4 align-items-center">
          <div className="col-12 col-md-4 text-center">
            <div className="p-4 rounded-3 d-inline-block shadow-sm" style={{ background: "rgba(255,255,255,0.02)", border: "1px solid rgba(255,255,255,0.08)", minWidth: "220px" }}>
              {currentItem.image ? (
                <img
                  src={currentItem.image}
                  alt={currentItem.itemName}
                  style={{ width: "180px", height: "180px", objectFit: "contain" }}
                />
              ) : (
                <div className="text-muted py-5">📷 No Image</div>
              )}
              <h5 className="text-white mt-3 mb-0 fw-bold">{currentItem.itemName}</h5>
            </div>
          </div>

          <div className="col-12 col-md-8">
            <div className="row g-3">
              <div className="col-6 col-sm-4">
                <div className="portfolio-stat-card p-3">
                  <span className="stat-label">Quantity</span>
                  <div className="stat-value text-white">{currentItem.quantity}</div>
                </div>
              </div>
              <div className="col-6 col-sm-4">
                <div className="portfolio-stat-card p-3">
                  <span className="stat-label">Unit Price</span>
                  <div className="stat-value text-info">€{Number(currentItem.price).toFixed(2)}</div>
                </div>
              </div>
              <div className="col-12 col-sm-4">
                <div className="portfolio-stat-card p-3">
                  <span className="stat-label">Total Value</span>
                  <div className="stat-value text-success">€{totalPrice.toFixed(2)}</div>
                </div>
              </div>
              <div className="col-6">
                <div className="portfolio-stat-card p-3">
                  <span className="stat-label">Record Date</span>
                  <div className="stat-value text-white fs-5">{currentItem.date}</div>
                </div>
              </div>
              <div className="col-6">
                <div className="portfolio-stat-card p-3">
                  <span className="stat-label">Status</span>
                  <div className={`stat-value fs-5 ${isBought ? "text-success" : "text-danger"}`}>
                    {isBought ? "Bought" : "Sold"}
                  </div>
                </div>
              </div>
            </div>

            {/* Action Bar */}
            <div className="d-flex gap-2 mt-4">
              <Link className="btn btn-secondary px-4" to={"/"}>
                ⬅️ Back to Portfolio
              </Link>
              <Link className="btn btn-primary px-4" to={`/editItem/${currentItem.id || id}`}>
                Edit Item
              </Link>
              <button className="btn btn-danger px-4 ms-auto" onClick={deleteItem}>
                🗑️ Delete Item
              </button>
            </div>
          </div>
        </div>

        {/* Transaction History Section */}
        <div className="border-top pt-4 mt-2" style={{ borderColor: "rgba(255,255,255,0.08)" }}>
          <div className="d-flex align-items-center justify-content-between mb-3">
            <h4 className="text-white mb-0">Transaction History for {item.itemName}</h4>
          </div>

          {historyLoading ? (
            <div className="alert alert-info py-2">Loading transaction logs...</div>
          ) : history.length > 0 ? (
            <Card>
              <Table hover responsive className="portfolio-table align-middle">
                <thead>
                  <tr>
                    <th scope="col" style={{ width: "60px" }}>Item</th>
                    <th scope="col">Name</th>
                    <th scope="col">Status</th>
                    <th scope="col">Date</th>
                    <th scope="col" className="text-center">Quantity</th>
                    <th scope="col" className="text-end">Unit Price</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((historyItem, index) => {
                    const hBought = historyItem.itemStatus === "BOUGHT";
                    const isSelected = (activeItem?.id && historyItem.id === activeItem.id) || (!activeItem?.id && index === 0);
                    return (
                      <tr
                        key={historyItem.id || index}
                        onClick={() => setActiveItem(historyItem)}
                        className="portfolio-row-clickable"
                        style={{
                          cursor: "pointer",
                          backgroundColor: isSelected ? "rgba(56, 132, 255, 0.15)" : undefined,
                        }}
                      >
                        <td>
                          {historyItem.image ? (
                            <img
                              src={historyItem.image}
                              alt={historyItem.itemName}
                              className="portfolio-item-thumb"
                            />
                          ) : (
                            <div className="portfolio-item-thumb-placeholder">📷</div>
                          )}
                        </td>
                        <td className="fw-semibold">{historyItem.itemName}</td>
                        <td>
                          <span className={`badge-status ${hBought ? "badge-bought" : "badge-sold"}`}>
                            {hBought ? "Bought" : "Sold"}
                          </span>
                        </td>
                        <td className="text-muted small">{historyItem.date}</td>
                        <td className="text-center font-mono">{historyItem.quantity}</td>
                        <td className="text-end font-mono fw-bold">
                          €{Number(historyItem.price).toFixed(2)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
              {renderPagination()}
            </Card>
          ) : (
            <div className="alert alert-warning py-3" role="alert">
              No previous transaction history records found for this item.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
