import React from "react";

export function StatusBadge({ children, tone = "neutral" }) {
  return <span className={`admin-status-badge ${tone}`}>{children}</span>;
}

export function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="admin-pagination">
      <button
        type="button"
        disabled={page.number <= 0}
        onClick={() => onChange(page.number - 1)}
      >
        Previous
      </button>
      <span>
        Page {page.number + 1} of {page.totalPages}
      </span>
      <button
        type="button"
        disabled={page.number + 1 >= page.totalPages}
        onClick={() => onChange(page.number + 1)}
      >
        Next
      </button>
    </div>
  );
}
