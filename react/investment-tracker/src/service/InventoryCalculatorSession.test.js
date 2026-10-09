import {
  clearInventoryCalculatorSession,
  loadInventoryCalculatorSession,
  saveInventoryCalculatorSession,
} from "./InventoryCalculatorSession";

const result = { steamId64: "123", profileName: "Example", items: [] };

beforeEach(() => clearInventoryCalculatorSession());

test("restores the last calculation for the same user", () => {
  saveInventoryCalculatorSession(42, "https://steamcommunity.com/id/example", result);

  expect(loadInventoryCalculatorSession(42)).toEqual({
    ownerId: "42",
    steamUrl: "https://steamcommunity.com/id/example",
    result,
  });
});

test("restores the calculation after a page reload", () => {
  saveInventoryCalculatorSession(42, "profile-a", result);

  jest.isolateModules(() => {
    const { loadInventoryCalculatorSession: loadAfterReload } = require("./InventoryCalculatorSession");
    expect(loadAfterReload(42)?.result).toEqual(result);
  });
});

test("does not reveal a previous user's calculation", () => {
  saveInventoryCalculatorSession(42, "profile-a", result);

  expect(loadInventoryCalculatorSession(43)).toBeNull();
  expect(window.sessionStorage.length).toBe(0);
});

test("clears the calculation on sign-out", () => {
  saveInventoryCalculatorSession(42, "profile-a", result);
  clearInventoryCalculatorSession();

  expect(loadInventoryCalculatorSession(42)).toBeNull();
});
