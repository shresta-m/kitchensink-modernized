import { Download, FileSpreadsheet } from 'lucide-react'
import { useRef, useState } from 'react'
import { ApiError } from '../api/http'
import { membersApi } from '../api/members'
import { useImportMembers } from '../hooks/useMembers'
import type { ImportReport } from '../types/api'
import { ErrorAlert } from './Alert'
import { Modal } from './Modal'
import { useToast } from './Toast'

const MAX_BYTES = 5 * 1024 * 1024

const CODE_LABEL: Record<string, string> = {
  VALIDATION_FAILED: 'Invalid',
  DUPLICATE_IN_FILE: 'Duplicate in file',
  DUPLICATE_EMAIL: 'Already registered',
  PROCESSING_ERROR: 'Not saved, retry',
}

export function ImportDialog({ onClose }: { onClose: () => void }) {
  const importMembers = useImportMembers()
  const toast = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)

  function pick(f: File | undefined) {
    setError(null)
    if (!f) return setFile(null)
    if (!f.name.toLowerCase().endsWith('.xlsx')) return setError('Only .xlsx files are accepted.')
    if (f.size > MAX_BYTES) return setError('The file is larger than 5 MB.')
    setFile(f)
  }

  async function upload() {
    if (!file) return
    setError(null)
    try {
      const result = await importMembers.mutateAsync(file)
      setReport(result)
      toast(`${result.inserted} member${result.inserted === 1 ? '' : 's'} imported`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Upload failed. Please try again.')
    }
  }

  async function template() {
    try {
      await membersApi.downloadTemplate()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not download the template', 'error')
    }
  }

  return (
    <Modal
      title="Import members from Excel"
      width="max-w-2xl"
      onClose={onClose}
      busy={importMembers.isPending}
      footer={
        report ? (
          <button type="button" className="btn-primary" onClick={onClose}>
            Done
          </button>
        ) : (
          <>
            <button type="button" className="btn-neutral" onClick={onClose} disabled={importMembers.isPending}>
              Cancel
            </button>
            <button type="button" className="btn-primary" onClick={upload} disabled={!file || importMembers.isPending}>
              {importMembers.isPending ? 'Importing…' : 'Import'}
            </button>
          </>
        )
      }
    >
      {!report ? (
        <div className="flex flex-col gap-4">
          <p className="m-0 text-sm text-ink-soft">
            Upload an .xlsx file with the columns <strong>Name</strong>, <strong>Email</strong> and <strong>Phone</strong>{' '}
            (max 5,000 rows, 5 MB). Rows are checked with the same rules as the form; duplicate emails are skipped.
          </p>
          <button type="button" onClick={template} className="btn-neutral btn-sm self-start">
            <Download size={16} /> Download template
          </button>
          <label
            htmlFor="xlsx-file"
            className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-brand-line bg-brand-soft px-6 py-8 text-center hover:bg-brand-tint"
          >
            <FileSpreadsheet size={28} className="text-brand" />
            <span className="text-sm font-semibold text-brand-dark">{file ? file.name : 'Choose an .xlsx file'}</span>
            {file && <span className="text-[13px] text-ink-muted">{(file.size / 1024).toFixed(1)} KB</span>}
            <input id="xlsx-file" ref={inputRef} type="file" accept=".xlsx" className="sr-only"
              onChange={(e) => pick(e.target.files?.[0])} />
          </label>
          {error && <ErrorAlert>{error}</ErrorAlert>}
        </div>
      ) : (
        <ImportResult report={report} />
      )}
    </Modal>
  )
}

function ImportResult({ report }: { report: ImportReport }) {
  const stats = [
    { label: 'Imported', value: report.inserted, tone: 'text-brand-dark bg-brand-tint' },
    { label: 'Duplicates skipped', value: report.duplicatesSkipped, tone: 'text-ink-soft bg-surface' },
    { label: 'Invalid rows', value: report.invalid, tone: 'text-danger-dark bg-danger-tint' },
    { label: 'Not saved', value: report.failed, tone: 'text-danger-dark bg-danger-tint' },
  ]
  return (
    <div className="flex flex-col gap-4">
      <p className="m-0 text-sm text-ink-soft">{report.totalRows} rows read.</p>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {stats.map((s) => (
          <div key={s.label} className={`rounded-lg px-3 py-3 ${s.tone}`}>
            <div className="text-2xl font-bold tabular-nums">{s.value}</div>
            <div className="text-[13px] font-medium">{s.label}</div>
          </div>
        ))}
      </div>
      {report.errors.length > 0 && (
        <div className="max-h-64 overflow-auto rounded-lg border border-line">
          <table className="w-full border-collapse text-[13px]">
            <thead className="sticky top-0 bg-surface text-left text-ink-soft">
              <tr>
                <th className="px-3 py-2 font-semibold">Row</th>
                <th className="px-3 py-2 font-semibold">Field</th>
                <th className="px-3 py-2 font-semibold">Problem</th>
              </tr>
            </thead>
            <tbody>
              {report.errors.map((e, i) => (
                <tr key={`${e.row}-${e.field}-${i}`} className="border-t border-line align-top">
                  <td className="px-3 py-2 tabular-nums">{e.row}</td>
                  <td className="px-3 py-2">{e.field}</td>
                  <td className="px-3 py-2">
                    <span className="font-medium">{CODE_LABEL[e.code] ?? e.code}</span>
                    <span className="text-ink-muted"> · {e.message}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {report.errorsTruncated && (
        <p className="m-0 text-[13px] text-ink-muted">Only the first problems are listed. Fix these and upload again to see the rest.</p>
      )}
    </div>
  )
}
