import { Download, Plus, Search, Trash2, Upload } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/http'
import { membersApi } from '../api/members'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/Alert'
import { ConfirmDeleteDialog } from '../components/ConfirmDeleteDialog'
import { EditMemberDialog } from '../components/EditMemberDialog'
import { ImportDialog } from '../components/ImportDialog'
import { MemberTable } from '../components/MemberTable'
import { Pagination } from '../components/Pagination'
import { useToast } from '../components/Toast'
import { useDebounce } from '../hooks/useDebounce'
import { useMembersList } from '../hooks/useMembers'
import type { Member, SortDir, SortField } from '../types/api'

const PAGE_SIZES = [8, 25, 50]

type DeleteTarget = { kind: 'one'; member: Member } | { kind: 'many'; ids: string[] }

export function MembersPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()

  const [search, setSearch] = useState('')
  const q = useDebounce(search.trim(), 300)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(PAGE_SIZES[0])
  const [sortField, setSortField] = useState<SortField>('name')
  const [sortDir, setSortDir] = useState<SortDir>('asc')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const [editing, setEditing] = useState<Member | null>(null)
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null)
  const [importing, setImporting] = useState(false)

  const query = useMemo(() => ({ q, page, size, sortField, sortDir }), [q, page, size, sortField, sortDir])
  const { data, error, isLoading, isFetching, refetch } = useMembersList(query)

  // A new search, sort or page size changes what "page 1" means, and old selections no longer apply.
  useEffect(() => setPage(0), [q, size, sortField, sortDir])
  useEffect(() => setSelected(new Set()), [q, page, size, sortField, sortDir])

  // If the last row of the last page was deleted, step back instead of showing an empty page.
  useEffect(() => {
    if (data && data.content.length === 0 && data.page > 0) setPage(Math.max(0, data.totalPages - 1))
  }, [data])

  function toggleSort(field: SortField) {
    if (field === sortField) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    else {
      setSortField(field)
      setSortDir('asc')
    }
  }

  async function exportCsv() {
    try {
      await membersApi.exportCsv({ q, sortField, sortDir })
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Export failed', 'error')
    }
  }

  const rows = data?.content ?? []
  const total = data?.totalElements ?? 0

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 text-[28px] font-bold tracking-tight">Members</h1>
          <p className="mb-0 mt-1.5 text-[15px] text-ink-muted">
            {data ? `${total} ${q ? 'matching' : 'registered'} member${total === 1 ? '' : 's'}` : ' '}
          </p>
        </div>
        <div className="flex flex-wrap gap-2.5">
          <button type="button" className="btn-neutral" onClick={exportCsv}>
            <Download size={18} /> Export CSV
          </button>
          {isAdmin && (
            <>
              <button type="button" className="btn-outline" onClick={() => setImporting(true)}>
                <Upload size={18} /> Import Excel
              </button>
              <Link to="/members/new" className="btn-primary no-underline">
                <Plus size={18} /> New member
              </Link>
            </>
          )}
        </div>
      </div>

      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
          <div className="relative min-w-[260px] max-w-[440px] flex-1">
            <Search size={18} className="pointer-events-none absolute left-3.5 top-3.5 text-ink-muted" aria-hidden />
            <label htmlFor="q" className="sr-only">Search members</label>
            <input id="q" type="search" value={search} onChange={(e) => setSearch(e.target.value)} maxLength={100}
              placeholder="Search by name, email or phone" className="input pl-[42px]" />
          </div>
          <div className="flex items-center gap-2.5 text-sm text-ink-soft">
            <label htmlFor="ps">Rows per page</label>
            <select id="ps" value={size} onChange={(e) => setSize(Number(e.target.value))} className="input w-auto">
              {PAGE_SIZES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
        </div>

        {isAdmin && selected.size > 0 && (
          <div className="flex flex-wrap items-center gap-4 border-b border-brand-line bg-brand-tint px-5 py-2.5 text-sm text-brand-dark">
            <span className="font-semibold">{selected.size} selected</span>
            <button type="button" className="btn-danger btn-sm" onClick={() => setDeleting({ kind: 'many', ids: [...selected] })}>
              <Trash2 size={16} /> Delete selected
            </button>
            <button type="button" className="h-9 border-0 bg-transparent px-1.5 text-[13px] font-semibold text-brand-dark underline"
              onClick={() => setSelected(new Set())}>
              Clear selection
            </button>
          </div>
        )}

        {error && !data ? (
          <div className="flex flex-col items-start gap-3 p-6">
            <ErrorAlert>{error instanceof ApiError ? error.message : 'Could not load members.'}</ErrorAlert>
            <button type="button" className="btn-neutral" onClick={() => refetch()}>Try again</button>
          </div>
        ) : isLoading ? (
          <div className="p-6" role="status" aria-label="Loading members">
            {Array.from({ length: 6 }, (_, i) => <div key={i} className="mb-3 h-11 animate-pulse rounded-lg bg-surface" />)}
          </div>
        ) : rows.length === 0 ? (
          <div className="px-6 py-16 text-center">
            <p className="m-0 text-base font-semibold">{q ? 'No members match your search' : 'No members yet'}</p>
            <p className="mb-0 mt-1.5 text-sm text-ink-muted">
              {q ? 'Try a different name, email or phone prefix.' : isAdmin ? 'Add one or import a spreadsheet.' : ''}
            </p>
          </div>
        ) : (
          <div className={isFetching ? 'opacity-70 transition-opacity' : 'transition-opacity'}>
            <MemberTable
              rows={rows}
              canWrite={isAdmin}
              selected={selected}
              onToggle={(id) => setSelected((prev) => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })}
              onToggleAll={(checked) => setSelected(checked ? new Set(rows.map((r) => r.id)) : new Set())}
              sortField={sortField}
              sortDir={sortDir}
              onSort={toggleSort}
              onEdit={setEditing}
              onDelete={(m) => setDeleting({ kind: 'one', member: m })}
            />
          </div>
        )}

        {data && data.totalElements > 0 && (
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onPage={setPage} />
        )}
      </section>

      {editing && <EditMemberDialog member={editing} onClose={() => setEditing(null)} />}
      {deleting && (
        <ConfirmDeleteDialog target={deleting} onClose={() => setDeleting(null)}
          onDone={() => { setDeleting(null); setSelected(new Set()) }} />
      )}
      {importing && <ImportDialog onClose={() => setImporting(false)} />}
    </>
  )
}
