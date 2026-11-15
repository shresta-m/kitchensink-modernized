# Kitchensink frontend (React + TypeScript + Vite)

White and teal UI for the Kitchensink members API: sign in, list with search / sort / pagination, register,
edit and delete from row actions, bulk delete, Excel import with a result report, CSV export.

## Run

Prerequisites: Node 20+, and the backend running on http://localhost:8080.

```bash
npm install
npm run dev          # http://localhost:5173
```

In dev, Vite proxies `/api` to the backend (`BACKEND_URL` overrides the target), so there is no CORS setup.
Sign in with the user you created through the backend's `BOOTSTRAP_*` variables.

```bash
npm run typecheck    # tsc
npm test             # vitest (client-side validation rules)
npm run build        # production bundle in dist/
```

To serve `dist/` from another origin, build with `VITE_API_BASE_URL=https://api.example.com` and add the web
origin to the backend's `CORS_ALLOWED_ORIGINS`.

## Structure

```
src/
  api/         http.ts (fetch wrapper, problem+json -> ApiError, downloads), auth.ts, members.ts
  auth/        AuthContext (login, logout, session restore, 401 handling)
  components/  Layout, MemberTable, Pagination, MemberForm, Edit/ConfirmDelete/Import dialogs, Modal, Toast, ...
  hooks/       useMembers (TanStack Query queries and mutations), useDebounce
  pages/       LoginPage, RegisterPage, AdminRegisterPage, MembersPage, NewMemberPage, UsersPage (admin)
  types/       API types matching the backend DTOs
  utils/       validation (mirrors backend rules), format
```

## Behaviour worth knowing

- **Accounts:** anyone can sign up at `/register` and gets a read-only `VIEWER` account, then signs in on the login page. Admins create
  `ADMIN` or `VIEWER` users at `/admin/users`.
- **Admin sign-up:** a separate page at `/admin/register` (not linked anywhere; change it with
  `VITE_ADMIN_SIGNUP_PATH`). It needs the server's admin sign-up code and sends you to the normal login page
  afterwards. If the server has no code configured it shows that admin sign-up is disabled.
- **Roles:** `VIEWER` sees the table, search, sort and CSV export only. `ADMIN` also gets New member, Import,
  edit, delete and bulk delete. The backend enforces this; the UI just hides what would be refused.
- **Errors:** every backend failure arrives as RFC 9457 problem JSON. Field errors (`errors[]`) appear under the
  matching input; `DUPLICATE_EMAIL` shows under Email; `VERSION_CONFLICT` (someone else edited) blocks the save and
  asks the user to reload instead of overwriting.
- **Session:** the JWT is kept in `sessionStorage` (cleared when the tab closes) and sent as a bearer header. A 401
  signs the user out, clears cached member data and shows "session expired". Trade-off: any XSS could read it;
  an httpOnly-cookie design would need backend changes.
- **Validation:** the same rules as the backend run in the browser for instant feedback. The server remains the
  authority and re-validates everything.
- **Search** is a prefix match on name, email or phone, debounced 300 ms. Selection is cleared when the page,
  sort or search changes, so "delete selected" always refers to what is visible.
