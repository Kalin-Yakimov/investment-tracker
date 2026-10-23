
// Shared by portfolio records and transaction history; the markup stays identical.
export default function TablePagination({ currentPage, totalPages, onPageChange, label = "Table pagination", disabled = false }) {
  const pages = [...new Set([0, totalPages - 1, currentPage - 2, currentPage - 1, currentPage, currentPage + 1, currentPage + 2])]
    .filter(page => page >= 0 && page < totalPages).sort((a, b) => a - b);
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
              disabled={disabled || currentPage === 0}
            >
              Previous
            </button>
          </li>
          {pages.map((page, index) => (
            <li key={page} className={`d-flex ${page === currentPage ? "page-item active" : "page-item"}`}>
              {index > 0 && page - pages[index - 1] > 1 && <span className="page-link text-secondary" aria-hidden="true">…</span>}
              <button type="button" className="page-link" aria-current={page === currentPage ? "page" : undefined}
                disabled={disabled} onClick={() => onPageChange(page)}>{page + 1}</button>
            </li>
          ))}
          <li className={`page-item ${currentPage >= totalPages - 1 ? "disabled" : ""}`}>
            <button
              className="page-link"
              onClick={() => onPageChange(currentPage + 1)}
              disabled={disabled || currentPage >= totalPages - 1}
            >
              Next
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
}
