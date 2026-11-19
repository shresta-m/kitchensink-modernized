import { useState } from 'react';
import { Modal } from '../components/Modal';
import { Alert, Button } from '../components/ui';

/** Shows a server-generated temporary password once. It is not retrievable afterwards. */
export function TempPasswordDialog({
  open,
  title,
  name,
  email,
  password,
  onDone,
  onAddAnother,
}: {
  open: boolean;
  title: string;
  name: string;
  email: string;
  password: string;
  onDone: () => void;
  onAddAnother?: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(password);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard can be blocked; the password is still selectable on screen.
    }
  };

  return (
    <Modal open={open} onClose={onDone} title={title} description={`${name} · ${email}`}>
      <div className="flex flex-col gap-2">
        <span className="text-sm font-semibold">Temporary password</span>
        <div className="flex items-center gap-2">
          <code className="flex h-12 flex-1 items-center rounded-lg border border-gray-300 bg-gray-50 px-4 font-mono text-lg tracking-wide select-all">
            {password}
          </code>
          <Button variant="outline" onClick={copy} className="h-12" aria-live="polite">
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>
      </div>
      <Alert tone="warning">Shown once. Share it with the member securely. They must set a new password at first sign-in.</Alert>
      <div className="flex justify-end gap-2.5">
        {onAddAnother && (
          <Button variant="secondary" onClick={onAddAnother}>
            Add another
          </Button>
        )}
        <Button onClick={onDone}>Done</Button>
      </div>
    </Modal>
  );
}
