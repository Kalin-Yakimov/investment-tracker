import CatalogueService from "../service/CatalogueService";
import { useEffect, useState } from "react";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../config/uiConfig";
import { isRequestCanceled } from "../service/RequestScope";
import useRequestScope from "./useRequestScope";

export default function useCatalogueSearch(query, enabled = true, minimumLength = 1) {
  const scope = useRequestScope();
  const [results, setResults] = useState([]);
  const text = query.trim();
  useEffect(() => {
    setResults([]);
    if (!enabled || text.length < minimumLength) return undefined;
    const request = scope.begin("search");
    if (!request) return undefined;
    const timer = window.setTimeout(async () => {
      try {
        const response = await CatalogueService.searchCsItems(text, { signal: request.signal });
        if (request.isCurrent()) setResults(Array.isArray(response) ? response : []);
      } catch (error) {
        if (request.isCurrent() && !isRequestCanceled(error)) console.error("Catalogue search failed:", error);
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
