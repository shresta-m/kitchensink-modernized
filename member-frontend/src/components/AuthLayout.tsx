import type { ReactNode } from 'react';
import { Icon } from './Icon';

/** Split screen used by Register, Sign in and Change password. */
export function AuthLayout({
  heading,
  intro,
  bullets,
  children,
}: {
  heading: string;
  intro: string;
  bullets: string[];
  children: ReactNode;
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[5fr_7fr]">
      <aside className="flex flex-col justify-between gap-8 bg-teal-700 px-8 py-8 text-white lg:px-12 lg:py-10">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-[10px] bg-white text-xl font-bold text-teal-700">K</div>
          <span className="text-xl font-semibold tracking-tight">Kitchensink</span>
        </div>
        <div className="flex max-w-md flex-col gap-5">
          <h1 className="text-3xl font-bold leading-tight tracking-tight">{heading}</h1>
          <p className="text-base leading-relaxed">{intro}</p>
        </div>
        <ul className="flex flex-col gap-3 text-[15px]">
          {bullets.map((b) => (
            <li key={b} className="flex items-center gap-3">
              <Icon name="check" size={20} />
              {b}
            </li>
          ))}
        </ul>
      </aside>
      <main className="flex items-center justify-center px-6 py-8 sm:px-10">{children}</main>
    </div>
  );
}
