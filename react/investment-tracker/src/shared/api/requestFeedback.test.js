import { expect, test } from "vitest";
import { requestErrorMessage } from "./requestFeedback";
test("write timeout explicitly warns against blindly repeating a possibly saved operation", () => {
  expect(requestErrorMessage({ code: "ECONNABORTED" }, "Fallback", { mutation: true })).toContain("may already have been saved");
  expect(requestErrorMessage({ code: "ETIMEDOUT" })).toBe("The request timed out. Please try again.");
  expect(requestErrorMessage({ code: "ERR_NETWORK" }, "Fallback", { mutation: true })).toContain("may already have been saved");
  expect(requestErrorMessage({ code: "ERR_NETWORK" })).toContain("Check your connection");
});
test("expected validation messages are readable and arbitrary exception details are not echoed", () => {
  expect(requestErrorMessage({ response: { data: { validationErrors: ["Quantity is invalid", "Price is invalid"] } } })).toBe("Quantity is invalid Price is invalid");
  expect(requestErrorMessage(new Error("private configuration details"), "Safe message")).toBe("Safe message");
});
