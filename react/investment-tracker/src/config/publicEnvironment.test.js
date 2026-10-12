import { expect, test } from "vitest";
import { readPublicEnvironment } from "./publicEnvironment";

test("keeps the existing local URL and one-second debounce defaults", () => {
  expect(readPublicEnvironment({})).toEqual({
    VITE_API_URL: "http://localhost:8080", VITE_ITEM_SEARCH_DEBOUNCE_MS: "1000",
  });
});

test("existing CRA public settings remain compatible", () => {
  expect(readPublicEnvironment({
    REACT_APP_API_URL: "https://api.example.test", REACT_APP_ITEM_SEARCH_DEBOUNCE_MS: "1500",
  })).toEqual({ VITE_API_URL: "https://api.example.test", VITE_ITEM_SEARCH_DEBOUNCE_MS: "1500" });
});

test("explicit Vite settings take precedence over legacy names", () => {
  expect(readPublicEnvironment({
    VITE_API_URL: "https://new.example.test", REACT_APP_API_URL: "https://old.example.test",
    VITE_ITEM_SEARCH_DEBOUNCE_MS: "750", REACT_APP_ITEM_SEARCH_DEBOUNCE_MS: "1500",
  })).toEqual({ VITE_API_URL: "https://new.example.test", VITE_ITEM_SEARCH_DEBOUNCE_MS: "750" });
});

test("zero and empty debounce settings retain their numeric-zero meaning", () => {
  expect(readPublicEnvironment({ VITE_ITEM_SEARCH_DEBOUNCE_MS: "0" }).VITE_ITEM_SEARCH_DEBOUNCE_MS).toBe("0");
  expect(readPublicEnvironment({ REACT_APP_ITEM_SEARCH_DEBOUNCE_MS: "" }).VITE_ITEM_SEARCH_DEBOUNCE_MS).toBe("");
});

test("does not include unrelated or private backend settings", () => {
  const env = { DB_PASSWORD: "synthetic-test-value", MAIL_PASSWORD: "synthetic-test-value",
    JWT_SECRET: "synthetic-test-value", REACT_APP_UNRELATED: "unused" };
  expect(Object.keys(readPublicEnvironment(env)).sort())
    .toEqual(["VITE_API_URL", "VITE_ITEM_SEARCH_DEBOUNCE_MS"]);
});
