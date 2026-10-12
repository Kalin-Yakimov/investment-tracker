import TablePagination from "../../../shared/components/TablePagination";
import { Table } from "react-bootstrap";
import { formatDateDDMMYYYY } from "../utils/portfolioFormat";

export default function PortfolioHistorySection({ currentPage, totalPages, handlePageChange, filterSortOption, setFilterSortOption, loading, items, sortedFilteredItems, handleRowClick }) {
  return (
      <div className="card portfolio-table-card shadow-lg p-4 mb-5">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
          <h3 className="portfolio-table-title mb-0">Portfolio History</h3>

          {/* Dynamic Portfolio Filter Dropdown */}
          <div className="d-flex align-items-center gap-2">
            <select
              className="form-select form-select-sm text-white border-0 shadow-sm"
              value={filterSortOption}
              onChange={(e) => setFilterSortOption(e.target.value)}
              style={{
                backgroundColor: "#161b26",
                border: "1px solid rgba(255, 255, 255, 0.15)",
                borderRadius: "8px",
                padding: "0.45rem 2.2rem 0.45rem 0.85rem",
                fontWeight: "600",
                fontSize: "0.875rem",
                cursor: "pointer",
                minWidth: "220px",
                color: "#ffffff",
              }}
            >
              <option value="newest_date">Filter by newest date</option>
              <option value="oldest_date">Filter by oldest date</option>
              <option value="bought">Filter by bought</option>
              <option value="sold">Filter by sold</option>
              <option value="highest_quantity">Filter by highest quantity</option>
              <option value="highest_total_price">Filter by highest total price</option>
              <option value="lowest_total_price">Filter by lowest total price</option>
              <option value="highest_unit_price">Filter by highest unit price</option>
              <option value="lowest_unit_price">Filter by lowest unit price</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div className="d-flex justify-content-center align-items-center py-5">
            <div className="spinner-border text-info" role="status" style={{ width: "2.2rem", height: "2.2rem" }}>
              <span className="visually-hidden">Loading...</span>
            </div>
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
                  {sortedFilteredItems.map((item) => {
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
                        <td className="text-muted small">{formatDateDDMMYYYY(item.date)}</td>
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
            <TablePagination currentPage={currentPage} totalPages={totalPages} onPageChange={handlePageChange} label="Portfolio pagination" />
          </>
        ) : (
          <div className="alert alert-warning text-center py-4 mb-0" role="alert">
            No portfolio items registered yet. Use the form above to register your first item!
          </div>
        )}
      </div>
  );
}
