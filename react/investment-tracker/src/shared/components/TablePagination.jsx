
// Shared by portfolio records and transaction history; the markup stays identical.
export default function TablePagination({ currentPage, totalPages, onPageChange, label = "Table pagination" }) {
  return (
    <div className="d-flex align-items-center justify-content-between pt-3 px-2">
      <span className="text-white-50 small">
        Page {currentPage + 1} of {totalPages || 1}
      </span>
      <nav aria-label={label}>
        <ul className="pagination pagination-sm mb-0">
          <li className={`page-item ${currentPage === 0 ? "disabled" : ""}`}>
            <button
              className="page-link"
              onClick={() => onPageChange(currentPage - 1)}
              disabled={currentPage === 0}
            >
              Previous
            </button>
          </li>
          {Array.from({ length: totalPages }, (_, index) => (
            <li key={index} className={`page-item ${index === currentPage ? "active" : ""}`}>
              <button className="page-link" onClick={() => onPageChange(index)}>
                {index + 1}
              </button>
            </li>
          ))}
          <li className={`page-item ${currentPage >= totalPages - 1 ? "disabled" : ""}`}>
            <button
              className="page-link"
              onClick={() => onPageChange(currentPage + 1)}
              disabled={currentPage >= totalPages - 1}
            >
              Next
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
}
