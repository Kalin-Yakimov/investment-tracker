import { useEffect, useMemo, useState } from "react";
import AuthService from "../../features/auth/AuthService";
import { createRequestScope } from "../utils/RequestScope";

export default function useRequestScope({ guardSession = true } = {}) {
  const [, rerender] = useState(0);
  const scope = useMemo(
    () => createRequestScope(
      () => guardSession
        ? (AuthService.getSessionVersion?.() ?? AuthService.getCurrentUser()?.id ?? null)
        : null,
      () => rerender((value) => value + 1),
    ),
    [guardSession],
  );
  useEffect(() => {
    scope.activate();
    const unsubscribe = guardSession ? AuthService.subscribe?.(() => scope.checkOwner()) : null;
    return () => {
      unsubscribe?.();
      scope.dispose();
    };
  }, [scope, guardSession]);
  return scope;
}
