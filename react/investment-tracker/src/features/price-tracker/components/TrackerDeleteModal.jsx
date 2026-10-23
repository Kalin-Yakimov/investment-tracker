
import { Modal } from "react-bootstrap";
import RequestError from "../../../shared/components/RequestError";

export default function TrackerDeleteModal({ deleteConfirmId, setDeleteConfirmId, handleDeleteItem, pending, error, onRemoved }) {
  return (
      deleteConfirmId && (
        <Modal show onHide={() => { if (!pending) setDeleteConfirmId(null); }} centered className="tracker-modal tracker-delete-modal"
          backdropClassName="tracker-modal-backdrop" style={{ zIndex: 999999 }} aria-labelledby="tracker-delete-title"
          restoreFocusOptions={{ preventScroll: true }}
          contentClassName="modal-content-custom card tracker-card-dark p-4 position-relative text-center shadow-lg border border-secondary border-opacity-25">
            <div className="fs-1 mb-2">🗑️</div>
            <h4 id="tracker-delete-title" className="text-white fw-bold mb-2">Remove Item?</h4>
            <p className="text-secondary small mb-4">
              Remove this item from your price tracker? Shared recorded prices will be kept.
            </p>
            <RequestError message={error} />
            <div className="d-flex align-items-center justify-content-center gap-3">
              <button
                type="button"
                className="btn btn-outline-light px-4"
                onClick={() => setDeleteConfirmId(null)}
                disabled={pending}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-danger px-4"
                disabled={pending}
                onClick={async () => {
                  if (await handleDeleteItem(deleteConfirmId)) {
                    setDeleteConfirmId(null);
                    // The original trigger was deleted; restore focus to a stable control.
                    onRemoved?.();
                  }
                }}
              >
                Remove Item
              </button>
            </div>
        </Modal>
      )
  );
}
