const configuredSearchDebounce = Number(
  process.env.REACT_APP_ITEM_SEARCH_DEBOUNCE_MS,
);

export const ITEM_SEARCH_DEBOUNCE_MS =
  Number.isFinite(configuredSearchDebounce) && configuredSearchDebounce >= 0
    ? configuredSearchDebounce
    : 1000;
