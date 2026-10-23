import { useCallback, useLayoutEffect, useRef } from "react";

// Temporary, mount-scoped DOM state. No records, URLs or browser storage are cached.
export default function usePortfolioReturnScroll(initialAnchor, ready) {
  const pending = useRef(initialAnchor);
  const sectionRef = useRef(null);
  const rows = useRef(new Map());
  const registerRow = useCallback((id, element) => {
    if (element) rows.current.set(String(id), element);
    else rows.current.delete(String(id));
  }, []);
  const captureAnchor = useCallback(id => ({
    itemId: String(id), offsetTop: rows.current.get(String(id))?.getBoundingClientRect().top ?? 0,
  }), []);

  useLayoutEffect(() => {
    if (!pending.current) return;
    const root = document.documentElement;
    const previousAnchoring = root.style.overflowAnchor;
    root.style.overflowAnchor = "none";
    let frame;
    let disposed = false;
    const intents = ["wheel", "touchstart", "pointerdown", "keydown"];
    const dispose = () => {
      if (disposed) return;
      disposed = true;
      cancelAnimationFrame(frame);
      root.style.overflowAnchor = previousAnchoring;
      intents.forEach(event => window.removeEventListener(event, cancel, true));
    };
    const cancel = () => { pending.current = null; dispose(); };
    intents.forEach(event => window.addEventListener(event, cancel, { capture: true, passive: true }));

    if (ready) {
      // Read geometry after loaded rows and their layout have been committed.
      frame = requestAnimationFrame(() => {
        frame = requestAnimationFrame(() => {
          const anchor = pending.current;
          if (!anchor) return;
          const row = rows.current.get(String(anchor.itemId));
          const target = row ?? sectionRef.current;
          if (!target) { cancel(); return; }
          const rect = target.getBoundingClientRect();
          const headerBottom = document.querySelector("header.custom-navbar-wrapper")?.getBoundingClientRect().bottom ?? 0;
          const inset = Math.max(0, headerBottom) + 8;
          const desiredOffset = row && Number.isFinite(anchor.offsetTop) ? anchor.offsetTop : inset;
          const offset = Math.min(Math.max(inset, desiredOffset), Math.max(inset, window.innerHeight - rect.height - 8));
          const maximum = Math.max(0, root.scrollHeight - window.innerHeight);
          const top = Math.min(maximum, Math.max(0, window.scrollY + rect.top - offset));
          window.scrollTo({ top, left: 0, behavior: "instant" });
          pending.current = null;
          // Keep native scroll anchoring out of this one restoration paint only.
          frame = requestAnimationFrame(dispose);
        });
      });
    }
    return dispose;
  }, [ready]);

  return { sectionRef, registerRow, captureAnchor };
}
