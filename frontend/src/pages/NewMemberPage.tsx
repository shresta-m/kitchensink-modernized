import { useNavigate } from 'react-router-dom'
import { Link } from 'react-router-dom'
import { MemberForm } from '../components/MemberForm'
import { useToast } from '../components/Toast'
import { useCreateMember } from '../hooks/useMembers'

export function NewMemberPage() {
  const create = useCreateMember()
  const navigate = useNavigate()
  const toast = useToast()

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,560px)_1fr]">
      <section className="card p-6">
        <h1 className="m-0 text-[28px] font-bold tracking-tight">New member</h1>
        <p className="mb-6 mt-1.5 text-[15px] text-ink-muted">Register a member. Email must be unique.</p>
        <MemberForm
          id="new-member"
          disabled={create.isPending}
          onSubmit={async (input) => {
            const created = await create.mutateAsync(input)
            toast(`${created.name} was registered`)
            navigate('/members')
          }}
        >
          <div className="mt-2 flex gap-3">
            <button type="submit" className="btn-primary" disabled={create.isPending}>
              {create.isPending ? 'Registering…' : 'Register member'}
            </button>
            <Link to="/members" className="btn-neutral no-underline">Cancel</Link>
          </div>
        </MemberForm>
      </section>

      <aside className="card h-fit self-start border-brand-line bg-brand-soft p-6">
        <h2 className="m-0 text-base font-semibold text-brand-dark">Rules</h2>
        <ul className="mb-0 mt-3 list-disc space-y-2 pl-5 text-sm text-ink-soft">
          <li>Name: required, up to 25 characters, no digits</li>
          <li>Email: required, valid address, not already registered</li>
          <li>Phone: 10 to 12 digits</li>
        </ul>
      </aside>
    </div>
  )
}
