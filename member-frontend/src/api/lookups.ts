import { request } from './client';
import type { LookupOption, MemberStatus, Role } from './types';

export const lookupsApi = {
  roles: () => request<Array<LookupOption<Role>>>('/lookups/roles'),
  statuses: () => request<Array<LookupOption<MemberStatus>>>('/lookups/statuses'),
};
