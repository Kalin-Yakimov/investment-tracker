import React, { useEffect, useState, useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import axios from "axios";
import UserService from "../service/UserService";
import { Table } from "react-bootstrap";
import Card from "../utils/Card";

export default function ViewItem() {
  const [item, setItem] = useState({
    itemName: "",
    itemStatus: "",
    date: "",
    quantity: "",
    price: "",
  });
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const { id } = useParams();
  const itemsPerPage = 10;

  const loadItem = useCallback(async () => {
    try {
      const token = UserService.getToken();
      const result = await axios.get(`http://localhost:8080/item/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      setItem(result.data);
    } catch (error) {
      console.error("Error fetching item:", error);
      setError("Item not found");
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadItemHistory = useCallback(
    async (page = 0) => {
      setHistoryLoading(true);
      try {
        const result = await UserService.getItemByName(
          item.itemName,
          page,
          itemsPerPage
        );
        setHistory(result.content);
        setHistoryTotalPages(result.totalPages);
        setCurrentPage(page);
      } catch (error) {
        console.error("Error fetching item history:", error);
      } finally {
        setHistoryLoading(false);
      }
    },
    [item.itemName, itemsPerPage]
  );

  useEffect(() => {
    loadItem();
  }, [loadItem]);

  useEffect(() => {
    if (item.itemName) {
      loadItemHistory();
    }
  }, [item.itemName, loadItemHistory]);

  const deleteItem = async () => {
    try {
      const token = UserService.getToken();
      await axios.delete(`http://localhost:8080/item/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      window.location.href = "/";
    } catch (error) {
      console.error("Error deleting item:", error);
    }
  };

  const handleHistoryPageChange = (page) => {
    if (page === currentPage) return;
    loadItemHistory(page);
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
            onClick={() => handleHistoryPageChange(currentPage - 1)}
            disabled={currentPage === 0}
          >
            Previous
          </button>
        </li>
        {Array.from({ length: historyTotalPages }, (_, index) => (
          <li
            key={index}
            className={`page-item ${index === currentPage ? "active" : ""}`}
          >
            <button
              className="page-link"
              onClick={() => handleHistoryPageChange(index)}
            >
              {index + 1}
            </button>
          </li>
        ))}
        <li
          className={`page-item ${
            currentPage === historyTotalPages - 1 ? "disabled" : ""
          }`}
        >
          <button
            className="page-link"
            onClick={() => handleHistoryPageChange(currentPage + 1)}
            disabled={currentPage === historyTotalPages - 1}
          >
            Next
          </button>
        </li>
      </ul>
    </nav>
  );

  if (loading) return <div>Loading...</div>;

  if (error) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
        <Link className="btn btn-primary my-2" to={"/"}>
          Back to Home
        </Link>
      </div>
    );
  }

  return (
    <div className="container mt-4">
      <div className="row justify-content-center">
        <div className="col-md-12 border rounded p-4 shadow">
          <h2 className="text-center mb-4">Item Details</h2>
          <div className="card">
            <div className="card-header">
              <ul className="list-group list-group-flush">
                <li className="list-group-item">
                  <b>Name: </b>
                  {item.itemName}
                </li>
                <li className="list-group-item">
                  <b>Status: </b>
                  {item.itemStatus}
                </li>
                <li className="list-group-item">
                  <b>Date: </b>
                  {item.date}
                </li>
                <li className="list-group-item">
                  <b>Quantity: </b>
                  {item.quantity}
                </li>
                <li className="list-group-item">
                  <b>Price: </b>
                  {item.price}
                </li>
              </ul>
            </div>
          </div>
          <div className="mt-4">
            <Link className="btn btn-primary mx-2" to={`/editItem/${id}`}>
              Edit
            </Link>
            <Link className="btn btn-secondary mx-2" to={"/"}>
              Back to Home
            </Link>
            <button className="btn btn-danger mx-2" onClick={deleteItem}>
              Delete
            </button>
          </div>

          <h3 className="text-center mt-4">Item History</h3>
          {historyLoading ? (
            <div>Loading history...</div>
          ) : history.length > 0 ? (
            <>
              <Card>
                <Table bordered hover responsive size="md">
                  <thead>
                    <tr>
                      <th scope="col">Id</th>
                      <th scope="col">Name</th>
                      <th scope="col">Status</th>
                      <th scope="col">Date</th>
                      <th scope="col">Quantity</th>
                      <th scope="col">Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((item, index) => (
                      <tr key={index}>
                        <td>{currentPage * itemsPerPage + index + 1}</td>
                        <td>{item.itemName}</td>
                        <td>
                          {item.itemStatus === "BOUGHT" ? "Bought" : "Sold"}
                        </td>
                        <td>{item.date}</td>
                        <td>{item.quantity}</td>
                        <td>{Number(item.price).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
                {renderPagination()}
              </Card>
            </>
          ) : (
            <div className="alert alert-warning" role="alert">
              There is no history to show.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
