import CatalogueApi from "./CatalogueApi";
import { useEffect, useState } from "react";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../../config/uiConfig";
import { isRequestCanceled } from "../../shared/utils/RequestScope";
import useRequestScope from "../../shared/hooks/useRequestScope";

export default function useCatalogueSearch(query, enabled = true, minimumLength = 1) {
  const scope = useRequestScope();
  const [results, setResults] = useState([]);
  const text = query.trim();
  useEffect(() => {
    if (!enabled || text.length < minimumLength) {
      setResults([]);
      return undefined;
    }
    // Keep the last successful suggestions during debounce/loading so the
    // dropdown stays mounted. Only the current request may replace them.
    const request = scope.begin("search");
    if (!request) return undefined;
    const timer = window.setTimeout(async () => {
      try {
        const response = await CatalogueApi.searchCsItems(text, { signal: request.signal });
        if (request.isCurrent()) setResults(Array.isArray(response) ? response : []);
      } catch (error) {
        if (request.isCurrent() && !isRequestCanceled(error)) {
          setResults([]);
          console.error("Catalogue search failed:", error);
        }
      } finally { request.finish(); }
    }, ITEM_SEARCH_DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      request.cancel();
      request.finish();
    };
  }, [text, enabled, minimumLength, scope]);
  return results;
}
