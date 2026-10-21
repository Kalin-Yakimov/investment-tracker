import { expect, test } from "vitest";
import { portfolioFormError, portfolioFormPayload } from "./portfolioForm";
const item = { itemName: "Case", csItemId: "case", itemStatus: "BOUGHT", date: new Date(2026, 9, 11), quantity: 2, price: 0 };
test.each([0, "0", "0.00", "999999999999999999999999999999999999.99"])("price %s is valid and sent without floating-point loss", price => {
  expect(portfolioFormError({ ...item, price }, true)).toBe("");
  expect(portfolioFormPayload({ ...item, price, image: "not-a-request-field" })).toEqual({
    itemName: "Case", csItemId: "case", itemStatus: "BOUGHT", date: "11.10.2026", quantity: 2, price: String(price),
  });
});
test.each([{ price: "" }, { price: -1 }, { price: "1.234" }, { price: "9999999999999999999999999999999999999" }, { quantity: 0 }, { quantity: 1.5 }, { quantity: 2147483648 }, { date: new Date("invalid") }])("invalid form value %j is rejected", change => {
  expect(portfolioFormError({ ...item, ...change })).not.toBe("");
});
