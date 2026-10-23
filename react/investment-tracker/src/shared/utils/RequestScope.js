export const isRequestCanceled = (error) =>
  error?.__CANCEL__ === true || error?.name === "AbortError" || error?.code === "ERR_CANCELED";

// One latest read per key. Mutations use replace:false/abortable:false and
// remain pending until they settle, even when their UI result is invalidated.
export function createRequestScope(getOwner = () => null, onChange = () => {}) {
  const entries = new Map();
  let active = false;
  const notify = () => { if (active) onChange(); };
  const cancel = (key) => {
    const entry = entries.get(key);
    if (!entry) return;
    entry.invalid = true;
    if (entry.controller) {
      entries.delete(key);
      entry.controller.abort();
    }
    notify();
  };
  const scope = {
    activate() { active = true; },
    isActive() { return active; },
    isPending(key) { return key === undefined ? entries.size > 0 : entries.has(key); },
    pendingCount() { return entries.size; },
    cancel,
    cancelAll() { [...entries.keys()].forEach(cancel); },
    checkOwner() {
      const owner = getOwner();
      entries.forEach((entry, key) => { if (entry.owner !== owner) cancel(key); });
    },
    dispose() {
      active = false;
      entries.forEach((entry) => {
        entry.invalid = true;
        entry.controller?.abort();
      });
      entries.clear();
    },
    begin(key, { replace = true, abortable = true } = {}) {
      if (!active) return null;
      const previous = entries.get(key);
      if (previous && (!replace || !previous.controller)) return null;
      if (previous) cancel(key);
      const entry = {
        owner: getOwner(),
        controller: abortable ? new AbortController() : null,
        invalid: false,
      };
      entries.set(key, entry);
      notify();
      return {
        signal: entry.controller?.signal,
        isCurrent: () => active && !entry.invalid && entries.get(key) === entry && entry.owner === getOwner(),
        cancel: () => { if (entries.get(key) === entry) cancel(key); },
        finish: () => {
          if (entries.get(key) === entry) {
            entries.delete(key);
            notify();
          }
        },
      };
    },
  };
  return scope;
}
