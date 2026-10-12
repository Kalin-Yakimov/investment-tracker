import { fireEvent, render, screen } from "@testing-library/react";
import TablePagination from "./TablePagination";

test("both edges disable navigation and keep the existing page labels", () => {
  const onPageChange = jest.fn();
  const { rerender } = render(<TablePagination currentPage={0} totalPages={3} onPageChange={onPageChange} label="Portfolio pagination" />);
  expect(screen.getByRole("navigation", { name: "Portfolio pagination" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
  expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "2" }));
  expect(onPageChange).toHaveBeenLastCalledWith(1);
  rerender(<TablePagination currentPage={2} totalPages={3} onPageChange={onPageChange} label="History pagination" />);
  expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Previous" }));
  expect(onPageChange).toHaveBeenLastCalledWith(1);
});

test("empty results retain the one-page display and disabled arrows", () => {
  const onPageChange = jest.fn();
  render(<TablePagination currentPage={0} totalPages={0} onPageChange={onPageChange} />);
  expect(screen.getByText("Page 1 of 1")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  expect(onPageChange).not.toHaveBeenCalled();
});
