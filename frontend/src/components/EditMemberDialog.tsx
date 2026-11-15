import { useState } from 'react'
import { ApiError } from '../api/http'
import { useUpdateMember } from '../hooks/useMembers'
import type { Member } from '../types/api'
import { ErrorAlert } from './Alert'
import { MemberForm } from './MemberForm'
import { Modal } from './Modal'
import { useToast } from './Toast'

export function EditMemberDialog({ member, onClose }: { member: Member; onClose: () => void }) {
  const update = useUpdateMember()
  const toast = useToast()
  const [conflict, setConflict] = useState(false)

  return (
    <Modal
      title="Edit member"
      onClose={onClose}
      busy={update.isPending}
      footer={
        <>
          <button type="button" className="btn-neutral" onClick={onClose} disabled={update.isPending}>
            Cancel
          </button>
          <button type="submit" form="edit-member" className="btn-primary" disabled={update.isPending || conflict}>
            {update.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </>
      }
    >
      {conflict && (
        <div className="mb-4">
          <ErrorAlert>This member was changed by someone else. Close this dialog to see the latest data, then edit again.</ErrorAlert>
        </div>
      )}
      <MemberForm
        id="edit-member"
        initial={{ name: member.name, email: member.email, phoneNumber: member.phoneNumber }}
        disabled={conflict}
        onSubmit={async (input) => {
          try {
            await update.mutateAsync({ id: member.id, input, version: member.version })
            toast('Member updated successfully')
            onClose()
          } catch (err) {
            if (err instanceof ApiError && err.code === 'VERSION_CONFLICT') {
              setConflict(true) // the list refetches on close; never overwrite blindly
              return
            }
            throw err
          }
        }}
      />
    </Modal>
  )
}
