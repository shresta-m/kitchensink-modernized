import type { ReactNode } from 'react';
import { Modal } from '../components/Modal';
import { Button } from '../components/ui';

export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  danger,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="text-[15px] leading-relaxed text-gray-700">{children}</div>
      <div className="flex justify-end gap-2.5">
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy}>
          {busy ? 'Working…' : confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
