import { useEffect, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';

/** Native <dialog>: focus trap, Escape to close and the backdrop come from the browser. */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  width = 'max-w-md',
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  width?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      aria-label={title}
      className={`m-auto w-[calc(100%-2rem)] ${width} rounded-2xl border border-gray-200 p-0 shadow-xl backdrop:bg-gray-900/40`}
    >
      {open && (
        <div className="flex flex-col gap-5 p-7">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold">{title}</h2>
              {description && <p className="mt-1 text-sm text-gray-600">{description}</p>}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close"
              className="-mr-2 -mt-2 flex h-11 w-11 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100"
            >
              <Icon name="close" size={20} />
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  );
}
