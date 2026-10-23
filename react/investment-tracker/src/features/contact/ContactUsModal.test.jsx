import { beforeEach, expect, test, vi } from "vitest";
import ContactApi from "./ContactApi";
import { act, fireEvent, render, screen } from "@testing-library/react";
import ContactUsModal from "./ContactUsModal";
import AuthService from "../auth/AuthService";
import deferred from "../../testUtils/deferred";

vi.mock("../auth/AuthService", () => ({
  default: { getSessionVersion: vi.fn(),  },
}));
beforeEach(() => {
  vi.clearAllMocks();
  AuthService.getSessionVersion.mockReturnValue(1);
});
const submit = () => {
  fireEvent.change(screen.getByLabelText("Message"), { target: { value: "Please help with my portfolio record." } });
  fireEvent.submit(screen.getByLabelText("Message").closest("form"));
};

test("rapid repeated submissions send one contact request", async () => {
  const pending = deferred();
  ContactApi.createContactTicket.mockReturnValue(pending.promise);
  render(<ContactUsModal show onClose={() => {}} defaultEmail="user@example.test" />);
  submit();
  fireEvent.submit(screen.getByLabelText("Message").closest("form"));
  expect(ContactApi.createContactTicket).toHaveBeenCalledTimes(1);
  await act(async () => pending.resolve({}));
  expect(screen.getByText(/Your message was sent successfully/)).toBeInTheDocument();
});

test("closing and reopening a modal ignores the previous submission's late success", async () => {
  const pending = deferred();
  ContactApi.createContactTicket.mockReturnValue(pending.promise);
  const onClose = vi.fn();
  const { rerender } = render(<ContactUsModal show onClose={onClose} defaultEmail="user@example.test" />);
  submit();
  rerender(<ContactUsModal show={false} onClose={onClose} defaultEmail="user@example.test" />);
  rerender(<ContactUsModal show onClose={onClose} defaultEmail="user@example.test" />);
  await act(async () => pending.resolve({}));
  expect(screen.queryByText(/Your message was sent successfully/)).not.toBeInTheDocument();
  expect(screen.getByLabelText("Message")).toHaveValue("");
  expect(onClose).not.toHaveBeenCalled();
});

vi.mock("./ContactApi", () => ({
  default: { createContactTicket: vi.fn() },
}));
