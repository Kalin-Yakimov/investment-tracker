import CatalogueApi from "./CatalogueApi";
import { useEffect, useState } from "react";
import { ITEM_SEARCH_DEBOUNCE_MS } from "../../config/uiConfig";
import { isRequestCanceled } from "../../shared/utils/RequestScope";
import useRequestScope from "../../shared/hooks/useRequestScope";
import { requestErrorMessage } from "../../shared/api/requestFeedback";

export default function useCatalogueSearch(query, enabled = true, minimumLength = 1) {
  const scope = useRequestScope();
  const [results, setResults] = useState([]);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const text = query.trim();
  useEffect(() => {
    if (!enabled || text.length < minimumLength) {
      setResults([]);
      setError("");
      return undefined;
    }
    // Keep the last successful suggestions during debounce/loading so the
    // dropdown stays mounted. Only the current request may replace them.
    const request = scope.begin("search");
    if (!request) return undefined;
    setError("");
    const timer = window.setTimeout(async () => {
      try {
        const response = await CatalogueApi.searchCsItems(text, { signal: request.signal });
        if (request.isCurrent()) setResults(Array.isArray(response) ? response : []);
      } catch (error) {
        if (request.isCurrent() && !isRequestCanceled(error)) {
          setResults([]);
          setError(requestErrorMessage(error, "Item search could not be loaded."));
        }
      } finally { request.finish(); }
    }, ITEM_SEARCH_DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      request.cancel();
      request.finish();
    };
  }, [text, enabled, minimumLength, revision, scope]);
  return { results, error, loading: scope.isPending("search"), retry: () => setRevision(value => value + 1) };
}
