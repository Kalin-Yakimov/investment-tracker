import { useEffect, useMemo, useState } from "react";
import UserService from "../service/UserService";
import { createRequestScope } from "../service/RequestScope";

export default function useRequestScope({ guardSession = true } = {}) {
  const [, rerender] = useState(0);
  const scope = useMemo(
    () => createRequestScope(
      () => guardSession
        ? (UserService.getSessionVersion?.() ?? UserService.getCurrentUser()?.id ?? null)
        : null,
      () => rerender((value) => value + 1),
    ),
    [guardSession],
  );
  useEffect(() => {
    scope.activate();
    const unsubscribe = guardSession ? UserService.subscribe?.(() => scope.checkOwner()) : null;
    return () => {
      unsubscribe?.();
      scope.dispose();
    };
  }, [scope, guardSession]);
  return scope;
}
