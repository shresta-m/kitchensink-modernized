interface PaginationProps {
  page: number // zero-based
  totalPages: number
  totalElements: number
  size: number
  onPage: (page: number) => void
}

/** 1 … 4 5 6 … 20 style window, as zero-based page numbers; null marks an ellipsis. */
export function pageWindow(current: number, total: number): (number | null)[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  const pages = new Set([0, total - 1, current - 1, current, current + 1])
  const sorted = [...pages].filter((p) => p >= 0 && p < total).sort((a, b) => a - b)
  const out: (number | null)[] = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push(null)
    out.push(p)
  })
  return out
}

export function Pagination({ page, totalPages, totalElements, size, onPage }: PaginationProps) {
  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, totalElements)
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3.5 text-sm text-ink-soft">
      <span>
        Showing {from}–{to} of {totalElements}
      </span>
      <nav aria-label="Pagination" className="flex items-center gap-1.5">
        <button type="button" className="btn-neutral h-11 px-3.5 font-medium disabled:bg-surface disabled:text-line-input"
          disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        {pageWindow(page, totalPages).map((p, i) =>
          p === null ? (
            <span key={`gap-${i}`} className="px-1.5 text-ink-muted">…</span>
          ) : (
            <button
              key={p}
              type="button"
              aria-current={p === page ? 'page' : undefined}
              onClick={() => onPage(p)}
              className={`h-11 min-w-11 rounded-lg px-2 text-sm ${
                p === page ? 'bg-brand font-semibold text-white' : 'border border-line-strong bg-white hover:bg-surface'
              }`}
            >
              {p + 1}
            </button>
          ),
        )}
        <button type="button" className="btn-neutral h-11 px-3.5 font-medium disabled:bg-surface disabled:text-line-input"
          disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </nav>
    </div>
  )
}
