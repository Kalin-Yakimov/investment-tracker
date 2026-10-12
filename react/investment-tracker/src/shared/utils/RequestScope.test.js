import { expect, test, vi } from "vitest";
import { createRequestScope, isRequestCanceled } from "./RequestScope";

test("only the newest read can update the screen; older completion cannot finish it", () => {
  const scope = createRequestScope();
  scope.activate();
  const old = scope.begin("list");
  const latest = scope.begin("list");
  expect(old.signal.aborted).toBe(true);
  expect(old.isCurrent()).toBe(false);
  old.finish();
  expect(scope.isPending("list")).toBe(true);
  expect(latest.isCurrent()).toBe(true);
  latest.finish();
  expect(scope.isPending()).toBe(false);
});

test("invalidating a mutation ignores its UI result but keeps the duplicate lock until settlement", () => {
  const scope = createRequestScope();
  scope.activate();
  const mutation = scope.begin("save", { replace: false, abortable: false });
  expect(mutation.signal).toBeUndefined();
  expect(scope.begin("save", { replace: false, abortable: false })).toBeNull();
  scope.cancel("save");
  expect(mutation.isCurrent()).toBe(false);
  expect(scope.begin("save", { replace: false, abortable: false })).toBeNull();
  mutation.finish();
  expect(scope.begin("save", { replace: false, abortable: false })).not.toBeNull();
});

test("session changes cancel reads and prevent old-account results", () => {
  let owner = 1;
  const scope = createRequestScope(() => owner);
  scope.activate();
  const read = scope.begin("private");
  const write = scope.begin("write", { abortable: false });
  owner = 2;
  expect(read.isCurrent()).toBe(false);
  scope.checkOwner();
  expect(read.signal.aborted).toBe(true);
  expect(write.isCurrent()).toBe(false);
  expect(scope.isPending("write")).toBe(true);
});

test("unmount cancels reads and does not notify; StrictMode reactivation does not revive old work", () => {
  const notify = vi.fn();
  const scope = createRequestScope(() => null, notify);
  expect(scope.begin("before-mount")).toBeNull();
  scope.activate();
  const old = scope.begin("list");
  scope.dispose();
  notify.mockClear();
  old.finish();
  expect(notify).not.toHaveBeenCalled();
  expect(old.signal.aborted).toBe(true);
  scope.activate();
  const current = scope.begin("list");
  expect(old.isCurrent()).toBe(false);
  expect(current.isCurrent()).toBe(true);
});

test("cancellation is distinguished from real failures", () => {
  expect(isRequestCanceled({ code: "ERR_CANCELED" })).toBe(true);
  expect(isRequestCanceled({ name: "AbortError" })).toBe(true);
  expect(isRequestCanceled({ __CANCEL__: true })).toBe(true);
  expect(isRequestCanceled(new Error("Network failed"))).toBe(false);
});
