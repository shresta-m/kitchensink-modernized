import { ApiError } from '../api/http'
import { useBulkDelete, useDeleteMember } from '../hooks/useMembers'
import type { Member } from '../types/api'
import { Modal } from './Modal'
import { useToast } from './Toast'

type Target = { kind: 'one'; member: Member } | { kind: 'many'; ids: string[] }

export function ConfirmDeleteDialog({ target, onClose, onDone }: { target: Target; onClose: () => void; onDone: () => void }) {
  const single = useDeleteMember()
  const bulk = useBulkDelete()
  const toast = useToast()
  const pending = single.isPending || bulk.isPending

  async function confirm() {
    try {
      if (target.kind === 'one') {
        await single.mutateAsync(target.member.id)
        toast(`${target.member.name} was deleted`)
      } else {
        const res = await bulk.mutateAsync(target.ids)
        toast(`${res.deleted} member${res.deleted === 1 ? '' : 's'} deleted`)
      }
      onDone()
    } catch (err) {
      // already gone (404) counts as done from the user's point of view
      if (err instanceof ApiError && err.status === 404) {
        onDone()
        return
      }
      toast(err instanceof ApiError ? err.message : 'Delete failed. Please try again.', 'error')
    }
  }

  const title = target.kind === 'one' ? 'Delete member?' : `Delete ${target.ids.length} members?`
  return (
    <Modal
      title={title}
      onClose={onClose}
      busy={pending}
      footer={
        <>
          <button type="button" className="btn-neutral" onClick={onClose} disabled={pending}>
            Cancel
          </button>
          <button type="button" className="btn-danger" onClick={confirm} disabled={pending}>
            {pending ? 'Deleting…' : 'Delete'}
          </button>
        </>
      }
    >
      <p className="m-0 text-[15px] text-ink-soft">
        {target.kind === 'one' ? (
          <>
            <strong className="text-ink">{target.member.name}</strong> ({target.member.email}) will be permanently removed.
          </>
        ) : (
          <>The selected members will be permanently removed.</>
        )}{' '}
        This cannot be undone.
      </p>
    </Modal>
  )
}
