import { expect, test } from "vitest";
import axios from "axios";
import { apiData, apiError } from "./ApiClient";


test("shared response handling unwraps data and preserves the server error object", async () => {
  const payload = { error: "Invalid request" };
  await expect(apiData(Promise.resolve({ data: { ok: true } }))).resolves.toEqual({ ok: true });
  await expect(apiData(Promise.reject({ response: { data: payload } }))).rejects.toBe(payload);
  expect(apiError({ response: { data: payload } })).toBe(payload);
});

test("cancellation and transport errors retain their identity", async () => {
  for (const error of [new axios.CanceledError("Session changed"), new Error("Connection unavailable")]) {
    expect(apiError(error)).toBe(error);
    await expect(apiData(Promise.reject(error))).rejects.toBe(error);
  }
});
