import { useEffect, useState, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import PortfolioApi from "./PortfolioApi";
import useRequestScope from "../../shared/hooks/useRequestScope";
import { isRequestCanceled } from "../../shared/utils/RequestScope";
import DatePicker from "react-datepicker";
import { parseCalendarDate } from "../../shared/utils/calendarDate";
import { portfolioFormError, portfolioFormPayload } from "./utils/portfolioForm";
import { requestErrorMessage } from "../../shared/api/requestFeedback";
import RequestError from "../../shared/components/RequestError";

export default function EditPortfolioItemPage() {
  const scope = useRequestScope();
  const navigate = useNavigate();
  const { id } = useParams();

  const [item, setItem] = useState({
    itemName: "",
    itemStatus: "PICK",
    date: new Date(),
    quantity: "",
    price: "",
  });

  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(true);

  const { itemName, itemStatus, date, quantity, price } = item;

  const onInputChange = (e) => {
    const { name, value } = e.target;
    setItem((prevItem) => ({
      ...prevItem,
      [name]: name === "itemStatus" ? value.toUpperCase() : value,
    }));
    setError("");
  };

  const loadItem = useCallback(async () => {
    const request = scope.begin("item");
    if (!request) return;
    setLoading(true); setLoadError("");
    try {
      const result = await PortfolioApi.getItem(id, { signal: request.signal });
      if (!request.isCurrent()) return;
      setItem({ ...result, date: parseCalendarDate(result.date) });
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) setLoadError(requestErrorMessage(error, "The item could not be loaded."));
    } finally { if (request.isCurrent()) setLoading(false); request.finish(); }
  }, [id, scope]);

  useEffect(() => {
    loadItem();
    return () => scope.cancel("item");
  }, [loadItem, scope]);

  const onSubmit = async (e) => {
    e.preventDefault();
    if (loading || loadError) return;
    const validationError = portfolioFormError(item);
    if (validationError) { setError(validationError); return; }

    const request = scope.begin("submit", { replace: false, abortable: false });
    if (!request) return;
    try {
      await PortfolioApi.updateItem(id, portfolioFormPayload(item));
      // Navigate back to the Item Details / Item History page
      if (request.isCurrent()) navigate(`/viewItem/${id}`);
    } catch (error) {
      if (request.isCurrent() && !isRequestCanceled(error)) {
        setError(requestErrorMessage(error, "The item could not be updated.", { mutation: true }));
      }
    } finally { request.finish(); }
  };

  const handleDateChange = (date) => {
    setItem((prevItem) => ({
      ...prevItem,
      date: date,
    }));
    setError("");
  };

  return (
    <div className="container mt-5 mb-5">
      <div className="row justify-content-center">
        <div className="col-12 col-md-8 col-lg-6">
          <div className="card shadow-lg p-4">
            <h2 className="text-center mb-4 text-white">Edit Portfolio Item</h2>
            <RequestError message={loadError} onRetry={loadItem} retryLabel="Retry loading item" pending={loading} />
            {loading && <div role="status">Loading item...</div>}
            {error && <div className="alert alert-danger mb-3" role="alert">{error}</div>}
            <form onSubmit={onSubmit} className="d-flex flex-column gap-3">
              <fieldset disabled={loading || Boolean(loadError) || scope.isPending("submit")} className="d-flex flex-column gap-3">
              <div>
                <label htmlFor="itemName" className="form-label text-white small fw-semibold">
                  Item Name
                </label>
                <input
                  type="text"
                  className="form-control"
                  placeholder="Enter item name"
                  name="itemName"
                  value={itemName}
                  readOnly={Boolean(item.csItemId)}
                  aria-describedby={item.csItemId ? "catalogue-name-note" : undefined}
                  disabled={scope.isPending("item") || scope.isPending("submit")}
                  onChange={onInputChange}
                  id="itemName"
                  required
                />
                {item.csItemId && <small id="catalogue-name-note" className="text-secondary">Name is linked to the selected catalogue item.</small>}
              </div>

              <div className="row g-3">
                <div className="col-6">
                  <label htmlFor="itemStatus" className="form-label text-white small fw-semibold">
                    Status
                  </label>
                  <select
                    className="form-select"
                    name="itemStatus"
                    value={itemStatus}
                    disabled={scope.isPending("item") || scope.isPending("submit")}
                    onChange={onInputChange}
                    id="itemStatus"
                    required
                  >
                    <option value="PICK">-- Select --</option>
                    <option value="BOUGHT">Bought</option>
                    <option value="SOLD">Sold</option>
                  </select>
                </div>

                <div className="col-6">
                  <label htmlFor="date" className="form-label text-white small fw-semibold">
                    Date
                  </label>
                  <DatePicker
                    id="date"
                    selected={date}
                    disabled={scope.isPending("item") || scope.isPending("submit")}
                    onChange={handleDateChange}
                    dateFormat={"dd.MM.yyyy"}
                    className="form-control"
                    required
                  />
                </div>
              </div>

              <div className="row g-3">
                <div className="col-6">
                  <label htmlFor="quantity" className="form-label text-white small fw-semibold">
                    Quantity
                  </label>
                  <input
                    type="number"
                    className="form-control"
                    placeholder="Quantity"
                    name="quantity"
                    value={quantity}
                    disabled={scope.isPending("item") || scope.isPending("submit")}
                    onChange={onInputChange}
                    id="quantity"
                    min="1"
                    required
                  />
                </div>

                <div className="col-6">
                  <label htmlFor="price" className="form-label text-white small fw-semibold">
                    Unit Price (€)
                  </label>
                  <input
                    type="number"
                    className="form-control"
                    placeholder="Price in Euro"
                    name="price"
                    value={price}
                    disabled={scope.isPending("item") || scope.isPending("submit")}
                    onChange={onInputChange}
                    id="price"
                    step="0.01"
                    min="0"
                    required
                  />
                </div>
              </div>

              </fieldset>
              <div className="d-flex justify-content-end gap-2 mt-4">
                <button type="submit" className="btn btn-primary px-4" disabled={loading || Boolean(loadError) || scope.isPending("submit")}>
                  Update Item
                </button>
                <button
                  type="button"
                  className="btn btn-secondary px-4"
                  disabled={scope.isPending("submit")}
                  onClick={() => navigate(`/viewItem/${id}`)}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
