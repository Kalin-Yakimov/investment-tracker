import axios from "axios";
import React, { useEffect, useState, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import UserService from "../service/UserService";
import DatePicker from "react-datepicker";
import { format } from "date-fns";

export default function EditItem() {
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
    try {
      const token = UserService.getToken();
      const result = await axios.get(`http://localhost:8080/item/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      let item = result.data;

      if (item.date) {
        const [day, month, year] = item.date.split(".");
        item.date = new Date(`${year}-${month}-${day}`);
      }

      setItem(item);
    } catch (error) {
      console.error("Error loading item:", error);
    }
  }, [id]);

  useEffect(() => {
    loadItem();
  }, [loadItem]);

  const onSubmit = async (e) => {
    e.preventDefault();
    if (itemStatus === "PICK" || !itemName || !date || !quantity || !price) {
      setError("Please fill out all fields correctly.");
      return;
    }

    try {
      const formattedDate = format(date, "dd.MM.yyyy");
      const token = UserService.getToken();
      await axios.put(
        `http://localhost:8080/item/${id}`,
        { ...item, date: formattedDate },
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        }
      );
      // Navigate back to the Item Details / Item History page
      navigate(`/viewItem/${id}`);
    } catch (error) {
      console.error("There was an error submitting the form!", error);
    }
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
            <h2 className="text-center mb-4 text-white">✏️ Edit Portfolio Item</h2>
            {error && <div className="alert alert-danger mb-3">{error}</div>}
            <form onSubmit={onSubmit} className="d-flex flex-column gap-3">
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
                  onChange={onInputChange}
                  id="itemName"
                  required
                />
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
                    selected={date}
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
                    onChange={onInputChange}
                    id="price"
                    step="0.01"
                    min="0"
                    required
                  />
                </div>
              </div>

              <div className="d-flex justify-content-end gap-2 mt-4">
                <button
                  type="button"
                  className="btn btn-secondary px-4"
                  onClick={() => navigate(`/viewItem/${id}`)}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary px-4">
                  Update Item
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
