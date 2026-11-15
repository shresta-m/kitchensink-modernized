/**
 * Path of the separate admin sign-up page. It is not linked from anywhere; share it with the people who
 * hold the admin sign-up code. Override with VITE_ADMIN_SIGNUP_PATH in .env to move it.
 */
const configured = import.meta.env.VITE_ADMIN_SIGNUP_PATH as string | undefined
export const ADMIN_SIGNUP_PATH = configured && configured.startsWith('/') ? configured : '/admin/register'
