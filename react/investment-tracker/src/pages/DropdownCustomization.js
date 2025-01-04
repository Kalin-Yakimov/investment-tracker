import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import axios from "axios";
import UserService from "../service/UserService";
import Card from "../utils/Card";

export default function DropdownCustomization() {
  const [dropdownItemName, setDropdownItemName] = useState("");
  const [dropdownItems, setDropdownItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [itemsPerPage] = useState(10); // Items per page
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const loadDropdownItems = useCallback(
    async (page = 0) => {
      try {
        const token = UserService.getToken();
        const response = await axios.get(
          `http://localhost:8080/dropdownItems`,
          {
            params: { page, size: itemsPerPage }, // Pagination parameters
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        );
        setDropdownItems(response.data.content); // Set items for the current page
        setTotalPages(response.data.totalPages); // Set the total number of pages
        setCurrentPage(page);
      } catch (error) {
        console.error("There was an error fetching the dropdown items!", error);
        setDropdownItems([]);
      } finally {
        setLoading(false);
      }
    },
    [itemsPerPage]
  );

  useEffect(() => {
    loadDropdownItems();
  }, [loadDropdownItems]);

  const onInputChange = (e) => {
    setDropdownItemName(e.target.value);
    setError("");
    setSuccessMessage("");
  };

  const onSubmit = async (e) => {
    e.preventDefault();

    // Validation
    if (!dropdownItemName) {
      setError("Item name cannot be empty.");
      return;
    }
    if (dropdownItemName.length > 60) {
      setError("Item name cannot be more than 60 characters.");
      return;
    }

    try {
      const token = UserService.getToken();
      await axios.post(
        "http://localhost:8080/dropdownItem",
        { itemName: dropdownItemName },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
        }
      );
      setSuccessMessage(
        `Item with name: ${dropdownItemName} has been successfully added.`
      );
      setDropdownItemName("");
      loadDropdownItems();
    } catch (error) {
      if (error.response && error.response.status === 409) {
        setError("Item with this name already exists.");
      } else {
        console.error("There was an error submitting the form!", error);
        setError("There was an error submitting the form. Please try again.");
      }
    }
  };

  const deleteItem = async (id) => {
    try {
      const token = UserService.getToken();
      await axios.delete(`http://localhost:8080/dropdownItems/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      setSuccessMessage(`Item has been successfully deleted.`);
      loadDropdownItems();
    } catch (error) {
      console.error("There was an error deleting the item!", error);
      setError("There was an error deleting the item. Please try again.");
    }
  };

  const clearInput = () => {
    setDropdownItemName("");
    setError("");
    setSuccessMessage("");
  };

  const handlePageChange = (page) => {
    if (page === currentPage) return;
    setLoading(true);
    loadDropdownItems(page);
  };

  const renderPagination = () => (
    <nav
      aria-label="Page navigation example"
      className="d-flex justify-content-end"
    >
      <ul className="pagination">
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
            currentPage === totalPages - 1 ? "disabled" : ""
          }`}
        >
          <button
            className="page-link"
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage === totalPages - 1}
          >
            Next
          </button>
        </li>
      </ul>
    </nav>
  );

  return (
    <div className="container mt-4">
      <div className="row justify-content-center">
        <div className="col-md-8 border rounded p-4 shadow text-center">
          <h2 className="text-center mb-4">Dropdown Customization</h2>
          <p>Here you can add new item options to your form's dropdown.</p>
          {error && <div className="alert alert-danger">{error}</div>}
          {successMessage && (
            <div className="alert alert-success">{successMessage}</div>
          )}
          <form className="row g-3" onSubmit={onSubmit}>
            <div className="col-md-8 offset-md-2">
              <label htmlFor="itemName" className="form-label">
                Name
              </label>
              <input
                type="text"
                className="form-control"
                placeholder="Enter item name"
                name="itemName"
                value={dropdownItemName}
                onChange={onInputChange}
                id="itemName"
                required
              />
            </div>
            <div className="col-md-12 d-flex justify-content-center align-items-end">
              <button type="submit" className="btn btn-primary me-2">
                Add To Dropdown
              </button>
              <button
                type="button"
                className="btn btn-secondary mx-2"
                onClick={clearInput}
              >
                Clear Input
              </button>
              <Link className="btn btn-outline-danger mx-2" to="/">
                Go Back
              </Link>
            </div>
          </form>

          <div className="mt-4">
            <h4>Items in Dropdown</h4>
            {loading ? (
              <div className="alert alert-info" role="alert">
                Loading items...
              </div>
            ) : (
              <Card>
                <div
                  className="items-container"
                  style={{ marginBottom: "20px" }}
                >
                  <ul className="list-group">
                    {dropdownItems.map((item) => (
                      <li
                        key={item.id}
                        className="list-group-item d-flex justify-content-between align-items-center"
                      >
                        {item.itemName}
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => deleteItem(item.id)}
                        >
                          Delete
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
                {renderPagination()}
              </Card>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
