
export default function TrackerDeleteModal({ deleteConfirmId, setDeleteConfirmId, handleDeleteItem, pending }) {
  return (
      deleteConfirmId && (
        <div
          className="modal-backdrop-custom d-flex align-items-center justify-content-center"
          onClick={() => setDeleteConfirmId(null)}
        >
          <div
            className="modal-content-custom card tracker-card-dark p-4 position-relative text-center shadow-lg border border-secondary border-opacity-25"
            style={{ maxWidth: "420px", width: "90%", borderRadius: "16px" }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="fs-1 mb-2">🗑️</div>
            <h4 className="text-white fw-bold mb-2">Remove Item?</h4>
            <p className="text-secondary small mb-4">
              Are you sure you want to remove this item from your price tracker? Its recorded price history will be deleted.
            </p>
            <div className="d-flex align-items-center justify-content-center gap-3">
              <button
                type="button"
                className="btn btn-outline-light px-4"
                onClick={() => setDeleteConfirmId(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-danger px-4"
                disabled={pending}
                onClick={() => {
                  handleDeleteItem(deleteConfirmId);
                  setDeleteConfirmId(null);
                }}
              >
                Remove Item
              </button>
            </div>
          </div>
        </div>
      )
  );
}
