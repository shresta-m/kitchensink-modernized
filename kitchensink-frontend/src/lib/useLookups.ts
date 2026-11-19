import { useEffect, useState } from 'react';
import { lookupsApi } from '../api/lookups';
import type { LookupOption, MemberStatus, Role } from '../api/types';

interface Lookups {
  roles: Array<LookupOption<Role>>;
  statuses: Array<LookupOption<MemberStatus>>;
}

// Fetched once per page load and shared. A failed fetch is not cached, so the next screen retries.
let cache: Promise<Lookups> | null = null;

function load(): Promise<Lookups> {
  cache ??= Promise.all([lookupsApi.roles(), lookupsApi.statuses()])
    .then(([roles, statuses]) => ({ roles: roles ?? [], statuses: statuses ?? [] }))
    .catch((e) => {
      cache = null;
      throw e;
    });
  return cache;
}

/** Role and status options from the backend. Empty lists until loaded (or if the call fails). */
export function useLookups(): Lookups {
  const [lookups, setLookups] = useState<Lookups>({ roles: [], statuses: [] });
  useEffect(() => {
    let cancelled = false;
    load()
      .then((l) => !cancelled && setLookups(l))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);
  return lookups;
}
