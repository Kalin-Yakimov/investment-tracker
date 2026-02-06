import axios from "axios";
import React, { useState, useEffect } from "react";
import UserService from "../service/UserService";
import DatePicker from "react-datepicker";
import { format } from "date-fns";
import { useNavigate } from "react-router-dom";
import checkTokenExpiration from "../utils/checkTokenExpiration";
import "react-datepicker/dist/react-datepicker.module.css";
//import "../styles/AddItem.css";

export default function AddItem({ onItemAdded }) {
  const [item, setItem] = useState({
    itemName: "",
    csItemId: null,
    itemStatus: "PICK",
    date: new Date(),
    quantity: "",
    price: "",
  });

  const [csItems, setCsItems] = useState([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const { itemName, itemStatus, date, quantity, price } = item;

  useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      if (itemName && itemName.length > 1) {
        fetchCsItems(itemName);
      } else {
        setCsItems([]);
        setShowDropdown(false);
      }
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [itemName]);

  const fetchCsItems = async (query) => {
    try {
      const token = UserService.getToken();
      const response = await axios.get(
        `http://localhost:8080/cs-items/search`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          params: {
            query: query,
          },
        }
      );
      setCsItems(response.data);
      setShowDropdown(true);
    } catch (error) {
      console.error("Error fetching CS items:", error);
    }
  };

  const onInputChange = (e) => {
    const { name, value } = e.target;
    setItem((prevItem) => ({
      ...prevItem,
      [name]: value,
      // If user types in name manually, clear the ID to ensure we don't link to wrong item unless they select it
      ...(name === 'itemName' ? { csItemId: null } : {})
    }));
    setError("");
  };

  const selectCsItem = (selectedItem) => {
    setItem((prevItem) => ({
      ...prevItem,
      itemName: selectedItem.name,
      csItemId: selectedItem.id
    }));
    setShowDropdown(false);
  };

  const handleDateChange = (date) => {
    setItem((prevItem) => ({
      ...prevItem,
      date: date,
    }));
    setError("");
  };

  const clearForm = () => {
    setItem({
      itemName: "",
      csItemId: null,
      itemStatus: "PICK",
      date: new Date(),
      quantity: "",
      price: "",
    });
    setError("");
    setCsItems([]);
    setShowDropdown(false);
  };

  const onSubmit = async (e) => {
    e.preventDefault();

    checkTokenExpiration(navigate);

    if (itemStatus === "PICK" || !itemName || !date || !quantity || !price) {
      setError("Please fill out all fields.");
      return;
    }

    try {
      const formattedDate = format(date, "dd.MM.yyyy");
      const token = UserService.getToken();
      await axios.post(
        `${UserService.BASE_URL}/item`,
        { ...item, date: formattedDate },
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
        },
      );
      clearForm();
      onItemAdded();
    } catch (error) {
      console.error("There was an error submitting the form!", error);
    }
  };

  return (
    <div className="container mt-4">
      <div className="row justify-content-center">
        <div className="col-12 border rounded p-4 shadow">
          <h2 className="text-center mb-4">Register Item</h2>
          {error && <div className="alert alert-danger">{error}</div>}
          <form
            onSubmit={onSubmit}
            className="d-flex flex-wrap gap-2 align-items-center"
          >
            <div className="flex-grow-1 me-2 position-relative">
              <label htmlFor="itemName" className="form-label">
                Name
              </label>
              <div className="input-group">
                <input
                  type="text"
                  className="form-control"
                  placeholder="Enter or search item"
                  name="itemName"
                  value={itemName}
                  onChange={onInputChange}
                  id="itemName"
                  style={{ borderRadius: "0.25rem" }}
                  required
                  autoComplete="off"
                />
                {showDropdown && csItems.length > 0 && (
                  <ul className="list-group position-absolute w-100" style={{ top: "100%", zIndex: 1000, maxHeight: "200px", overflowY: "auto" }}>
                    {csItems.map((csItem) => (
                      <li
                        key={csItem.id}
                        className="list-group-item list-group-item-action"
                        onClick={() => selectCsItem(csItem)}
                        style={{ cursor: "pointer" }}
                      >
                        {csItem.name}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
            <div className="flex-grow-1 me-2">
              <label htmlFor="itemStatus" className="form-label">
                Status
              </label>
              <select
                className="form-control"
                name="itemStatus"
                value={itemStatus}
                onChange={onInputChange}
                id="itemStatus"
                required
              >
                <option value="PICK">Pick</option>
                <option value="BOUGHT">Bought</option>
                <option value="SOLD">Sold</option>
              </select>
            </div>
            <div className="flex-grow-1 me-2">
              <label htmlFor="date" className="form-label">
                Date
              </label>
              <div className="input-group">
                <DatePicker
                  selected={date}
                  onChange={handleDateChange}
                  dateFormat={"dd.MM.yyyy"}
                  className="form-control"
                  required
                />
              </div>
            </div>
            <div className="flex-grow-1 me-2">
              <label htmlFor="quantity" className="form-label">
                Quantity
              </label>
              <input
                type="number"
                className="form-control"
                placeholder="Enter quantity"
                name="quantity"
                value={quantity}
                onChange={onInputChange}
                id="quantity"
                required
              />
            </div>
            <div className="flex-grow-1 me-2">
              <label htmlFor="price" className="form-label">
                Price
              </label>
              <input
                type="number"
                className="form-control"
                placeholder="Enter price"
                name="price"
                value={price}
                onChange={onInputChange}
                id="price"
                step="0.01"
                min="0"
                required
              />
            </div>
            <div className="d-flex align-items-end">
              <button type="submit" className="btn btn-primary me-2">
                Add
              </button>
              <button
                type="button"
                className="btn btn-outline-danger"
                onClick={clearForm}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
