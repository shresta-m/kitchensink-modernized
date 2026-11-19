import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { membersApi } from '../api/members';
import type { Member, MemberQuery, MemberStatus, PageResponse, Role } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AppLayout } from '../components/AppLayout';
import { Icon } from '../components/Icon';
import { Alert, Badge, Button, SelectInput, TextInput, cn } from '../components/ui';
import { ConfirmDialog } from '../dialogs/ConfirmDialog';
import { EditMemberDialog } from '../dialogs/EditMemberDialog';
import { TempPasswordDialog } from '../dialogs/TempPasswordDialog';
import { useLookups } from '../lib/useLookups';
import { describeError, EMPTY_RESPONSE_MESSAGE } from '../lib/errors';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'empty-response' }
  | { kind: 'ok'; data: PageResponse<Member> };

const COLUMNS: Array<{ field: string; label: string }> = [
  { field: 'name', label: 'Name' },
  { field: 'email', label: 'Email' },
  { field: 'role', label: 'Role' },
  { field: 'createdAt', label: 'Added' },
];

const dateFmt = new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short', year: 'numeric' });

function pageList(current: number, total: number): Array<number | 'gap'> {
  const pages = new Set([0, total - 1, current - 1, current, current + 1]);
  const sorted = [...pages].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
  const out: Array<number | 'gap'> = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push('gap');
    out.push(p);
  });
  return out;
}

export function MembersPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const lookups = useLookups();

  const [searchInput, setSearchInput] = useState('');
  const [query, setQuery] = useState<MemberQuery>({ sort: 'name,asc', page: 0, size: 8 });
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [toast, setToast] = useState<string | null>(null);

  const [editing, setEditing] = useState<Member | null>(null);
  const [deleting, setDeleting] = useState<Member | null>(null);
  const [resetting, setResetting] = useState<Member | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [issued, setIssued] = useState<{ member: Member; password: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const notify = useCallback((message: string) => {
    setToast(message);
    setTimeout(() => setToast(null), 3500);
  }, []);
  const reload = () => setReloadKey((k) => k + 1);

  // Debounce the search box into the query.
  useEffect(() => {
    const t = setTimeout(() => {
      setQuery((q) => (q.search === (searchInput.trim() || undefined) ? q : { ...q, search: searchInput.trim() || undefined, page: 0 }));
    }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  useEffect(() => {
    let cancelled = false;
    setState({ kind: 'loading' });
    membersApi
      .list(query)
      .then((data) => {
        if (cancelled) return;
        setState(data ? { kind: 'ok', data } : { kind: 'empty-response' });
      })
      .catch((e) => !cancelled && setState({ kind: 'error', message: describeError(e) }));
    return () => {
      cancelled = true;
    };
  }, [query, reloadKey]);

  const patch = (p: Partial<MemberQuery>) => {
    setSelected(new Set());
    setQuery((q) => ({ ...q, ...p, page: p.page ?? 0 }));
  };

  const [sortField, sortDir] = query.sort.split(',');
  const toggleSort = (field: string) => patch({ sort: field === sortField ? `${field},${sortDir === 'asc' ? 'desc' : 'asc'}` : `${field},asc` });

  const rows = state.kind === 'ok' ? state.data.content : [];
  const allSelected = rows.length > 0 && rows.every((m) => selected.has(m.id));
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(rows.map((m) => m.id)));
  const toggleOne = (id: string) =>
    setSelected((s) => {
      const n = new Set(s);
      if (!n.delete(id)) n.add(id);
      return n;
    });

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      notify(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = () =>
    run(async () => {
      if (!deleting) return;
      await membersApi.remove(deleting.id);
      setDeleting(null);
      notify(`${deleting.name} deleted.`);
      reload();
    });

  const confirmBulk = () =>
    run(async () => {
      const res = await membersApi.bulkDelete([...selected]);
      setBulkOpen(false);
      setSelected(new Set());
      notify(res ? `${res.deletedCount} members deleted.` : 'Delete request sent.');
      reload();
    });

  const approve = (m: Member) =>
    run(async () => {
      await membersApi.approve(m.id);
      notify(`${m.name} approved.`);
      reload();
    });

  const confirmReset = () =>
    run(async () => {
      if (!resetting) return;
      const res = await membersApi.resetPassword(resetting.id);
      const member = resetting;
      setResetting(null);
      if (!res) notify(EMPTY_RESPONSE_MESSAGE);
      else {
        setIssued({ member, password: res.temporaryPassword });
        reload();
      }
    });

  const exportCsv = () =>
    run(async () => {
      const file = await membersApi.exportCsv({ search: query.search, role: query.role, status: query.status });
      if (!file) return notify('The server returned an empty file.');
      const url = URL.createObjectURL(file.blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file.filename;
      a.click();
      URL.revokeObjectURL(url);
    });

  const data = state.kind === 'ok' ? state.data : null;
  const from = data && data.totalElements > 0 ? data.page * data.size + 1 : 0;
  const to = data ? Math.min((data.page + 1) * data.size, data.totalElements) : 0;
  const filtered = Boolean(query.search || query.role || query.status);
  const colCount = COLUMNS.length + 1 + (isAdmin ? 3 : 0);

  const sortHeader = (c: { field: string; label: string }) => (
    <th key={c.field} className="whitespace-nowrap px-4 py-3 font-semibold" aria-sort={sortField === c.field ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button className="inline-flex items-center gap-1.5 font-semibold hover:text-teal-800" onClick={() => toggleSort(c.field)}>
        {c.label}
        <Icon name={sortField === c.field ? (sortDir === 'asc' ? 'up' : 'down') : 'sort'} size={14} />
      </button>
    </th>
  );

  return (
    <AppLayout>
      <div>
        <h1 className="text-[26px] font-bold tracking-tight">{isAdmin ? 'Members' : 'Member directory'}</h1>
        <p className="mt-1 text-[15px] text-gray-600">
          {isAdmin ? 'Search, edit and manage everyone who can sign in.' : 'Browse and search members. You have read-only access.'}
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2.5">
        <div className="relative min-w-[220px] flex-1">
          <span className="pointer-events-none absolute left-3.5 top-3 text-gray-600">
            <Icon name="search" />
          </span>
          <TextInput aria-label="Search members" placeholder="Search name, email or phone" className="pl-10" value={searchInput} onChange={(e) => setSearchInput(e.target.value)} />
        </div>
        <SelectInput aria-label="Filter by role" style={{ width: 130, flex: '0 0 130px' }} value={query.role ?? ''} onChange={(e) => patch({ role: (e.target.value || undefined) as Role | undefined })}>
          <option value="">All roles</option>
          {lookups.roles.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </SelectInput>
        {isAdmin && (
          <SelectInput aria-label="Filter by status" style={{ width: 200, flex: '0 0 200px' }} value={query.status ?? ''} onChange={(e) => patch({ status: (e.target.value || undefined) as MemberStatus | undefined })}>
            <option value="">All statuses</option>
            {lookups.statuses.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </SelectInput>
        )}
        {isAdmin && (
          <Button variant="secondary" onClick={exportCsv} disabled={busy}>
            <Icon name="download" /> Export
          </Button>
        )}
        {isAdmin && (
          <Link to="/members/new" className="no-underline">
            <span className="inline-flex h-11 items-center gap-2 rounded-lg bg-teal-700 px-4 text-sm font-semibold text-white hover:bg-teal-800">
              <Icon name="plus" /> Add member
            </span>
          </Link>
        )}
      </div>

      <div className="-mb-3 flex min-h-10 flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5 text-sm text-gray-700">
          {isAdmin && selected.size > 0 ? (
            <>
              <span className="font-semibold text-gray-900">{selected.size} selected</span>
              <Button size="sm" variant="secondary" onClick={() => setSelected(new Set())}>Clear selection</Button>
              <Button size="sm" variant="danger" onClick={() => setBulkOpen(true)}>
                <Icon name="trash" size={16} /> Delete selected
              </Button>
            </>
          ) : (
            isAdmin && <span className="text-gray-600">Select rows to delete in bulk</span>
          )}
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          Rows per page
          <SelectInput aria-label="Rows per page" className="h-9 text-sm" style={{ width: 76, flex: '0 0 76px' }} value={query.size} onChange={(e) => patch({ size: Number(e.target.value) })}>
            {[8, 25, 50].map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </SelectInput>
        </label>
      </div>

      {state.kind === 'error' && (
        <Alert tone="error">
          {state.message}{' '}
          <button className="font-semibold underline" onClick={reload}>Retry</button>
        </Alert>
      )}
      {state.kind === 'empty-response' && <Alert tone="warning">{EMPTY_RESPONSE_MESSAGE}</Alert>}

      <div className="overflow-x-auto rounded-xl border border-gray-200">
        <table className="w-full min-w-[960px] border-collapse text-left text-sm">
          <thead className="bg-gray-50 text-[13px] text-gray-700">
            <tr>
              {isAdmin && (
                <th className="w-12 px-4 py-3">
                  <input type="checkbox" aria-label="Select all rows on this page" className="h-4 w-4 accent-teal-700" checked={allSelected} onChange={toggleAll} />
                </th>
              )}
              {COLUMNS.slice(0, 2).map(sortHeader)}
              <th className="px-4 py-3 font-semibold">Phone</th>
              {COLUMNS.slice(2).map(sortHeader)}
              {isAdmin && <th className="px-4 py-3 font-semibold">Status</th>}
              {isAdmin && <th className="sticky right-0 w-[136px] bg-gray-50 px-4 py-3 text-right font-semibold">Actions</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {state.kind === 'loading' &&
              Array.from({ length: 5 }, (_, i) => (
                <tr key={i}>
                  <td colSpan={colCount} className="px-4 py-3.5">
                    <div className="h-5 animate-pulse rounded bg-gray-100" />
                  </td>
                </tr>
              ))}
            {state.kind === 'ok' && rows.length === 0 && (
              <tr>
                <td colSpan={colCount} className="px-4 py-12 text-center text-gray-600">
                  {filtered ? 'No members match your search or filters.' : 'No members yet.'}
                </td>
              </tr>
            )}
            {rows.map((m) => (
              <tr key={m.id} className={cn(selected.has(m.id) && 'bg-teal-50/50')}>
                {isAdmin && (
                  <td className="px-4 py-3">
                    <input type="checkbox" aria-label={`Select ${m.name}`} className="h-4 w-4 accent-teal-700" checked={selected.has(m.id)} onChange={() => toggleOne(m.id)} />
                  </td>
                )}
                <td className="whitespace-nowrap px-4 py-3 font-semibold">{m.name}</td>
                <td className="whitespace-nowrap px-4 py-3 text-gray-700">{m.email}</td>
                <td className="whitespace-nowrap px-4 py-3 text-gray-700">{m.countryCode} {m.phone}</td>
                <td className="px-4 py-3">
                  <Badge tone={m.role === 'ADMIN' ? 'teal' : 'gray'}>{m.role === 'ADMIN' ? 'Admin' : 'Viewer'}</Badge>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-gray-700">{dateFmt.format(new Date(m.createdAt))}</td>
                {isAdmin && (
                  <td className="whitespace-nowrap px-4 py-3">
                    <Badge tone={m.status === 'ACTIVE' ? 'teal' : 'amber'} dot>
                      {STATUS_LABEL[m.status]}
                    </Badge>
                  </td>
                )}
                {isAdmin && (
                  <td className={cn('sticky right-0 px-4 py-2', selected.has(m.id) ? 'bg-teal-50' : 'bg-white')}>
                    <div className="flex justify-end gap-1">
                      {m.status === 'PENDING_APPROVAL' && (
                        <IconAction label={`Approve ${m.name}`} icon="check" disabled={busy} onClick={() => approve(m)} />
                      )}
                      <IconAction label={`Edit ${m.name}`} icon="edit" onClick={() => setEditing(m)} />
                      <IconAction label={`Delete ${m.name}`} icon="trash" danger onClick={() => setDeleting(m)} />
                    </div>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data && (
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-gray-700">
          <div className="flex items-center gap-3">
            <span>
              {from}–{to} of {data.totalElements}
            </span>
          </div>
          <nav aria-label="Pagination" className="flex items-center gap-1">
            <Button size="sm" variant="secondary" disabled={data.page === 0} onClick={() => patch({ page: data.page - 1 })}>Previous</Button>
            {pageList(data.page, data.totalPages).map((p, i) =>
              p === 'gap' ? (
                <span key={`g${i}`} className="px-1.5">…</span>
              ) : (
                <button
                  key={p}
                  aria-current={p === data.page ? 'page' : undefined}
                  onClick={() => patch({ page: p })}
                  className={cn('h-9 min-w-9 rounded-lg px-2 text-[13px] font-semibold', p === data.page ? 'bg-teal-700 text-white' : 'hover:bg-gray-100')}
                >
                  {p + 1}
                </button>
              ),
            )}
            <Button size="sm" variant="secondary" disabled={data.page + 1 >= data.totalPages} onClick={() => patch({ page: data.page + 1 })}>Next</Button>
          </nav>
        </div>
      )}

      <EditMemberDialog
        member={editing}
        onClose={() => setEditing(null)}
        onResetPassword={(m) => {
          setEditing(null);
          setResetting(m);
        }}
        onSaved={(m) => {
          setEditing(null);
          notify(`${m.name} updated.`);
          reload();
        }}
      />
      <ConfirmDialog open={deleting !== null} title="Delete member?" confirmLabel="Delete" danger busy={busy} onConfirm={confirmDelete} onClose={() => setDeleting(null)}>
        <strong>{deleting?.name}</strong> will be removed and can no longer sign in. This cannot be undone.
      </ConfirmDialog>
      <ConfirmDialog open={bulkOpen} title={`Delete ${selected.size} members?`} confirmLabel="Delete all" danger busy={busy} onConfirm={confirmBulk} onClose={() => setBulkOpen(false)}>
        The selected members will be removed and can no longer sign in. This cannot be undone.
      </ConfirmDialog>
      <ConfirmDialog open={resetting !== null} title="Reset password?" confirmLabel="Generate temporary password" busy={busy} onConfirm={confirmReset} onClose={() => setResetting(null)}>
        A new temporary password will be generated for <strong>{resetting?.name}</strong>. Their current password stops working and they must set a new one at next sign-in.
      </ConfirmDialog>
      <TempPasswordDialog
        open={issued !== null}
        title="Temporary password"
        name={issued?.member.name ?? ''}
        email={issued?.member.email ?? ''}
        password={issued?.password ?? ''}
        onDone={() => setIssued(null)}
      />

      {toast && (
        <div role="status" className="fixed bottom-20 left-1/2 z-50 -translate-x-1/2 rounded-lg bg-gray-900 px-4 py-3 text-sm font-medium text-white shadow-lg">
          {toast}
        </div>
      )}
    </AppLayout>
  );
}

const STATUS_LABEL: Record<MemberStatus, string> = {
  ACTIVE: 'Active',
  TEMP_PASSWORD: 'Temp password',
  PENDING_APPROVAL: 'Pending approval',
};

function IconAction({ label, icon, danger, disabled, onClick }: { label: string; icon: 'edit' | 'trash' | 'check'; danger?: boolean; disabled?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      disabled={disabled}
      className={cn('flex h-9 w-9 disabled:opacity-50 items-center justify-center rounded-lg hover:bg-gray-100', danger ? 'text-red-700' : 'text-gray-700')}
    >
      <Icon name={icon} size={17} />
    </button>
  );
}
