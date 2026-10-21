import PriceTrackerApi from "../../features/price-tracker/PriceTrackerApi";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isRequestCanceled } from "../utils/RequestScope";
import useRequestScope from "./useRequestScope";
import { PRICE_HISTORY_CONCURRENCY } from "../../config/uiConfig";
import { requestErrorMessage } from "../api/requestFeedback";

export default function usePriceHistories(items, revision = 0) {
  const scope = useRequestScope();
  const [histories, setHistories] = useState({});
  const [states, setStates] = useState({});
  const work = useRef({ wanted: new Set(), attempted: new Set(), queue: [], revision });
  const key = items.map((item) => item.id).filter((id) => id != null).join(",");
  const ids = useMemo(() => [...new Set(key ? key.split(",") : [])], [key]);

  const pump = useCallback(function pump() {
    const state = work.current;
    while (scope.isActive() && scope.pendingCount() < PRICE_HISTORY_CONCURRENCY && state.queue.length) {
      const id = state.queue.shift();
      if (!state.wanted.has(id) || state.attempted.has(id)) continue;
      const request = scope.begin("history:" + id);
      if (!request) continue;
      state.attempted.add(id);
      setStates(previous => ({ ...previous, [id]: { status: "loading", error: "" } }));
      Promise.resolve()
        .then(() => {
          if (!request.isCurrent()) return null;
          return PriceTrackerApi.getPriceHistory(id, { signal: request.signal });
        })
        .then((response) => {
          if (request.isCurrent() && work.current.wanted.has(id)) {
            setHistories((previous) => ({ ...previous, [id]: Array.isArray(response) ? response : [] }));
            setStates(previous => ({ ...previous, [id]: { status: "success", error: "" } }));
          }
        })
        .catch((error) => {
          if (request.isCurrent() && !isRequestCanceled(error)) {
            setStates(previous => ({ ...previous, [id]: { status: "error", error: requestErrorMessage(error, "Price history could not be loaded.") } }));
          }
        })
        .finally(() => { request.finish(); pump(); });
    }
  }, [scope]);

  useEffect(() => {
    const state = work.current;
    if (state.revision !== revision) {
      scope.cancelAll();
      state.attempted.clear();
      state.queue = [];
      state.revision = revision;
    }
    state.wanted = new Set(ids);
    state.queue = state.queue.filter((id) => state.wanted.has(id));
    [...state.attempted].forEach((id) => {
      if (!state.wanted.has(id)) {
        scope.cancel("history:" + id);
        state.attempted.delete(id);
      }
    });
    setHistories((previous) => Object.fromEntries(Object.entries(previous).filter(([id]) => state.wanted.has(id))));
    setStates((previous) => Object.fromEntries(Object.entries(previous).filter(([id]) => state.wanted.has(id))));
    ids.forEach((id) => {
      if (!state.attempted.has(id) && !state.queue.includes(id)) state.queue.push(id);
    });
    pump();
  }, [ids, revision, pump, scope]);

  useEffect(() => () => {
    work.current.wanted.clear();
    work.current.attempted.clear();
    work.current.queue = [];
  }, []);
  const retry = useCallback((itemId) => {
    const id = String(itemId), state = work.current;
    if (!state.wanted.has(id) || scope.isPending("history:" + id)) return;
    state.attempted.delete(id);
    if (!state.queue.includes(id)) state.queue.unshift(id);
    pump();
  }, [scope, pump]);
  return { histories, states, retry };
}
