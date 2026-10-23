import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import usePortfolioReturnScroll from "./usePortfolioReturnScroll";

let frames;
beforeEach(() => {
  frames = new Map();
  let sequence = 0;
  vi.stubGlobal("requestAnimationFrame", callback => { frames.set(++sequence, callback); return sequence; });
  vi.stubGlobal("cancelAnimationFrame", id => frames.delete(id));
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
  vi.spyOn(document.documentElement, "scrollHeight", "get").mockReturnValue(3000);
  window.scrollY = 400;
  document.documentElement.style.overflowAnchor = "auto";
});
afterEach(() => { vi.unstubAllGlobals(); document.documentElement.style.overflowAnchor = ""; });

function paint() {
  const callbacks = [...frames.values()]; frames.clear();
  act(() => callbacks.forEach(callback => callback()));
}

function setup(anchor = { itemId: "42", offsetTop: 100 }) {
  const view = renderHook(({ ready }) => usePortfolioReturnScroll(anchor, ready), { initialProps: { ready: false } });
  const section = { getBoundingClientRect: vi.fn(() => ({ top: 200, height: 700 })) };
  const row = { getBoundingClientRect: vi.fn(() => ({ top: 300, height: 48 })) };
  view.result.current.sectionRef.current = section;
  view.result.current.registerRow(42, row);
  return { ...view, row, section };
}

test("captures record identity and row-relative position instead of the page's absolute scroll", () => {
  const view = setup(null);
  expect(view.result.current.captureAnchor(42)).toEqual({ itemId: "42", offsetTop: 300 });
  expect(frames.size).toBe(0);
});

test("waits for readiness and layout, restores once, then releases temporary anchoring control", () => {
  const view = setup();
  expect(document.documentElement.style.overflowAnchor).toBe("none");
  expect(frames.size).toBe(0);
  view.rerender({ ready: true }); paint();
  expect(window.scrollTo).not.toHaveBeenCalled();
  paint();
  expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 600, left: 0, behavior: "instant" });
  paint();
  expect(document.documentElement.style.overflowAnchor).toBe("auto");
  view.rerender({ ready: false }); view.rerender({ ready: true }); paint(); paint();
  expect(window.scrollTo).toHaveBeenCalledTimes(1);
});

test("uses current row geometry if content above it changes while loading", () => {
  const view = setup(); view.rerender({ ready: true }); paint();
  view.row.getBoundingClientRect.mockReturnValue({ top: 500, height: 48 });
  paint();
  expect(window.scrollTo).toHaveBeenCalledWith({ top: 800, left: 0, behavior: "instant" });
});

test.each(["deleted", "moved off the retained page"])("falls back to the table when the original row is %s", () => {
  const view = setup(); view.result.current.registerRow(42, null);
  view.result.current.registerRow(99, { getBoundingClientRect: () => ({ top: 350, height: 48 }) });
  view.rerender({ ready: true }); paint(); paint();
  expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 592, left: 0, behavior: "instant" });
});

test("clamps to the document's new end when fewer records leave less scrollable space", () => {
  const view = setup();
  vi.spyOn(document.documentElement, "scrollHeight", "get").mockReturnValue(window.innerHeight + 250);
  view.rerender({ ready: true }); paint(); paint();
  expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 250, left: 0, behavior: "instant" });
});

test("an empty short page has a valid zero scroll position", () => {
  const view = setup(); view.result.current.registerRow(42, null);
  vi.spyOn(document.documentElement, "scrollHeight", "get").mockReturnValue(200);
  view.rerender({ ready: true }); paint(); paint();
  expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0, left: 0, behavior: "instant" });
});

test.each(["wheel", "touchstart", "pointerdown", "keydown"])("does not fight the user's %s interaction while waiting", event => {
  const view = setup(); act(() => window.dispatchEvent(new Event(event)));
  view.rerender({ ready: true }); paint(); paint();
  expect(window.scrollTo).not.toHaveBeenCalled();
  expect(document.documentElement.style.overflowAnchor).toBe("auto");
});

test("leaving the page cancels pending frames and restores browser styles", () => {
  const view = setup(); view.rerender({ ready: true }); paint(); view.unmount(); paint();
  expect(window.scrollTo).not.toHaveBeenCalled();
  expect(frames.size).toBe(0);
  expect(document.documentElement.style.overflowAnchor).toBe("auto");
});
