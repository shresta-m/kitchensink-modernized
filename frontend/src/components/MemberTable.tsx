import { ArrowDown, ArrowUp, ArrowUpDown, Pencil, Trash2 } from 'lucide-react'
import type { Member, SortDir, SortField } from '../types/api'
import { formatDate, initials } from '../utils/format'

interface MemberTableProps {
  rows: Member[]
  canWrite: boolean
  selected: Set<string>
  onToggle: (id: string) => void
  onToggleAll: (checked: boolean) => void
  sortField: SortField
  sortDir: SortDir
  onSort: (field: SortField) => void
  onEdit: (m: Member) => void
  onDelete: (m: Member) => void
}

function SortHeader({ label, field, active, dir, onSort }: {
  label: string
  field: SortField
  active: boolean
  dir: SortDir
  onSort: (f: SortField) => void
}) {
  const Icon = !active ? ArrowUpDown : dir === 'asc' ? ArrowUp : ArrowDown
  return (
    <button type="button" onClick={() => onSort(field)}
      className={`flex items-center gap-1.5 border-0 bg-transparent p-0 font-semibold ${active ? 'text-brand' : 'text-ink-soft'}`}>
      {label}
      <Icon size={14} className={active ? 'text-brand' : 'text-ink-muted'} aria-hidden />
    </button>
  )
}

export function MemberTable(p: MemberTableProps) {
  const allSelected = p.rows.length > 0 && p.rows.every((r) => p.selected.has(r.id))
  const ariaSort = (f: SortField) => (p.sortField === f ? (p.sortDir === 'asc' ? 'ascending' : 'descending') : 'none')

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[880px] border-collapse text-sm">
        <thead>
          <tr className="bg-surface text-left text-ink-soft">
            {p.canWrite && (
              <th scope="col" className="w-12 py-3 pl-5">
                <input type="checkbox" aria-label="Select all rows on this page" className="h-[18px] w-[18px] accent-brand"
                  checked={allSelected} onChange={(e) => p.onToggleAll(e.target.checked)} />
              </th>
            )}
            <th scope="col" aria-sort={ariaSort('name')} className={`py-3 pr-4 ${p.canWrite ? 'pl-4' : 'pl-5'}`}>
              <SortHeader label="Name" field="name" active={p.sortField === 'name'} dir={p.sortDir} onSort={p.onSort} />
            </th>
            <th scope="col" aria-sort={ariaSort('email')} className="px-4 py-3">
              <SortHeader label="Email" field="email" active={p.sortField === 'email'} dir={p.sortDir} onSort={p.onSort} />
            </th>
            <th scope="col" className="px-4 py-3 font-semibold">Phone</th>
            <th scope="col" aria-sort={ariaSort('createdAt')} className="px-4 py-3">
              <SortHeader label="Created" field="createdAt" active={p.sortField === 'createdAt'} dir={p.sortDir} onSort={p.onSort} />
            </th>
            {p.canWrite && <th scope="col" className="py-3 pl-4 pr-5 text-right font-semibold">Actions</th>}
          </tr>
        </thead>
        <tbody>
          {p.rows.map((m) => {
            const isSelected = p.selected.has(m.id)
            return (
              <tr key={m.id} className={`border-t border-line ${isSelected ? 'bg-brand-soft' : 'bg-white'}`}>
                {p.canWrite && (
                  <td className="py-3 pl-5">
                    <input type="checkbox" aria-label={`Select ${m.name}`} className="h-[18px] w-[18px] accent-brand"
                      checked={isSelected} onChange={() => p.onToggle(m.id)} />
                  </td>
                )}
                <td className={`py-3 pr-4 ${p.canWrite ? 'pl-4' : 'pl-5'}`}>
                  <div className="flex items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-tint text-[13px] font-semibold text-brand-dark">
                      {initials(m.name)}
                    </span>
                    <span className="font-semibold">{m.name}</span>
                  </div>
                </td>
                <td className="px-4 py-3 text-ink-soft">{m.email}</td>
                <td className="px-4 py-3 tabular-nums text-ink-soft">{m.phoneNumber}</td>
                <td className="px-4 py-3 text-ink-muted">{formatDate(m.createdAt)}</td>
                {p.canWrite && (
                  <td className="py-3 pl-4 pr-5">
                    <div className="flex justify-end gap-2">
                      <button type="button" aria-label={`Edit ${m.name}`} onClick={() => p.onEdit(m)}
                        className="icon-btn border-line-strong text-ink-soft hover:bg-surface">
                        <Pencil size={18} />
                      </button>
                      <button type="button" aria-label={`Delete ${m.name}`} onClick={() => p.onDelete(m)}
                        className="icon-btn border-danger-line text-danger hover:bg-danger-tint">
                        <Trash2 size={18} />
                      </button>
                    </div>
                  </td>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
