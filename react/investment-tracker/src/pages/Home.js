import React, { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import UserService from "../service/UserService";
import { Table } from "react-bootstrap";
import "../styles/Home.css";

export default function Home() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [itemsPerPage] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const navigate = useNavigate();

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

  useEffect(() => {
    loadItems();
  }, [loadItems]);

  const handleRowClick = (id) => {
    navigate(`/viewItem/${id}`);
  };

  const handlePageChange = (page) => {
    if (page === currentPage) return;
    setLoading(true);
    loadItems(page);
  };

  // Calculate portfolio summary stats for display
  const stats = React.useMemo(() => {
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

      {/* Main Table Card */}
      <div className="card portfolio-table-card shadow-lg p-4">
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
                        <td className="text-muted small">{item.date}</td>
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
    </div>
  );
}
