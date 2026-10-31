import React, { useState } from 'react';

export interface RejectionDialogProps {
  isOpen: boolean;
  /** Called only once the comment satisfies FR-021 (10-1000 characters after trimming, never
   * blank). Receives the comment exactly as typed, never pre-trimmed: FR-021 stores it verbatim. */
  onConfirm: (comment: string) => void;
  /** Called on abandonment. The dish's status, price and recipe stay as they were, and no audit
   * entry is implied (FR-031). */
  onCancel: () => void;
}

const MIN_LENGTH = 10;
const MAX_LENGTH = 1000;

function isValid(comment: string): boolean {
  const trimmed = comment.trim();
  return trimmed.length >= MIN_LENGTH && comment.length <= MAX_LENGTH;
}

/**
 * The mandatory rejection comment of FR-021, with the live character counter measured against
 * the raw length the Administrator typed — the trim only matters for the blank/too-short check,
 * not for what the counter displays, so a trailing space does not make the counter lie about how
 * much room is left.
 */
export const RejectionDialog: React.FC<RejectionDialogProps> = ({
  isOpen,
  onConfirm,
  onCancel,
}) => {
  const [comment, setComment] = useState('');

  if (!isOpen) {
    return null;
  }

  const valid = isValid(comment);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="rejection-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50"
    >
      <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-lg space-y-3">
        <h2 id="rejection-dialog-title" className="text-base font-semibold text-gray-900">
          Rechazar y devolver a cocina
        </h2>

        <label className="block text-sm">
          <span className="text-gray-700">Comentario para el Chef</span>
          <textarea
            aria-label="Comentario para el Chef"
            className="mt-1 block w-full rounded border border-gray-300 px-2 py-1.5 text-sm"
            rows={4}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        </label>

        <p className={`text-xs ${comment.length > MAX_LENGTH ? 'text-red-600' : 'text-gray-500'}`}>
          {comment.length} / {MAX_LENGTH}
        </p>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700"
            onClick={onCancel}
          >
            Cancelar
          </button>
          <button
            type="button"
            className="rounded bg-red-700 px-3 py-1.5 text-sm text-white disabled:bg-gray-300"
            disabled={!valid}
            onClick={() => onConfirm(comment)}
          >
            Rechazar
          </button>
        </div>
      </div>
    </div>
  );
};

export default RejectionDialog;
