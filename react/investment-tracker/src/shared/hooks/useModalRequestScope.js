import { useEffect } from "react";
import useRequestScope from "./useRequestScope";

export default function useModalRequestScope(show, options) {
  const scope = useRequestScope(options);
  useEffect(() => {
    if (!show) scope.cancelAll();
    return () => scope.cancelAll();
  }, [show, scope]);
  return scope;
}
