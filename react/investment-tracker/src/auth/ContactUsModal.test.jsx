import ContactService from "../service/ContactService";
import { act, fireEvent, render, screen } from "@testing-library/react";
import ContactUsModal from "./ContactUsModal";
import UserService from "../service/UserService";
import deferred from "../testUtils/deferred";

jest.mock("../service/UserService", () => ({
  __esModule: true, default: { getSessionVersion: jest.fn(),  },
}));
beforeEach(() => {
  jest.clearAllMocks();
  UserService.getSessionVersion.mockReturnValue(1);
});
const submit = () => {
  fireEvent.change(screen.getByLabelText("Message"), { target: { value: "Please help with my portfolio record." } });
  fireEvent.submit(screen.getByLabelText("Message").closest("form"));
};

test("rapid repeated submissions send one contact request", async () => {
  const pending = deferred();
  ContactService.createContactTicket.mockReturnValue(pending.promise);
  render(<ContactUsModal show onClose={() => {}} defaultEmail="user@example.test" />);
  submit();
  fireEvent.submit(screen.getByLabelText("Message").closest("form"));
  expect(ContactService.createContactTicket).toHaveBeenCalledTimes(1);
  await act(async () => pending.resolve({}));
  expect(screen.getByText(/Your message was sent successfully/)).toBeInTheDocument();
});

test("closing and reopening a modal ignores the previous submission's late success", async () => {
  const pending = deferred();
  ContactService.createContactTicket.mockReturnValue(pending.promise);
  const onClose = jest.fn();
  const { rerender } = render(<ContactUsModal show onClose={onClose} defaultEmail="user@example.test" />);
  submit();
  rerender(<ContactUsModal show={false} onClose={onClose} defaultEmail="user@example.test" />);
  rerender(<ContactUsModal show onClose={onClose} defaultEmail="user@example.test" />);
  await act(async () => pending.resolve({}));
  expect(screen.queryByText(/Your message was sent successfully/)).not.toBeInTheDocument();
  expect(screen.getByLabelText("Message")).toHaveValue("");
  expect(onClose).not.toHaveBeenCalled();
});

jest.mock("../service/ContactService", () => ({
  __esModule: true, default: { createContactTicket: jest.fn() },
}));
