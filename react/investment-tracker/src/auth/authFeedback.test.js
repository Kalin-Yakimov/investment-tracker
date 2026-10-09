import { authErrorMessage, pendingActivationEmail, rememberActivationEmail } from "./authFeedback";

afterEach(() => { jest.restoreAllMocks(); sessionStorage.clear(); });

test("displays API validation and error responses safely", () => {
  expect(authErrorMessage({ error: "Wait before retrying" }, "Fallback")).toBe("Wait before retrying");
  expect(authErrorMessage({ validationErrors: ["Enter email", "Enter code"] }, "Fallback"))
    .toBe("Enter email Enter code");
  expect(authErrorMessage(null, "Fallback")).toBe("Fallback");
});

test("stores only the pending email and removes it when finished", () => {
  rememberActivationEmail("person@example.com");
  expect(pendingActivationEmail()).toBe("person@example.com");
  expect(sessionStorage.length).toBe(1);
  rememberActivationEmail("");
  expect(pendingActivationEmail()).toBe("");
});

test("unavailable session storage does not break authentication forms", () => {
  jest.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("unavailable"); });
  jest.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("unavailable"); });
  expect(pendingActivationEmail()).toBe("");
  expect(() => rememberActivationEmail("person@example.com")).not.toThrow();
});
