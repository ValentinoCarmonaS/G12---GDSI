import React from 'react';
import { StaffAccountStatus, StaffMemberResponse } from '../../types/staff';

export interface StaffStatusModalProps {
  isOpen: boolean;
  staffMember: StaffMemberResponse | null;
  onClose: () => void;
  onConfirm: (id: string, status: StaffAccountStatus) => Promise<void> | void;
  isSubmitting?: boolean;
  error?: string | null;
}

export const StaffStatusModal: React.FC<StaffStatusModalProps> = ({
  isOpen,
  staffMember,
  onClose,
  onConfirm,
  isSubmitting = false,
  error = null,
}) => {
  if (!isOpen || !staffMember) {
    return null;
  }

  const isSuspending = staffMember.status !== 'SUSPENDED';
  const targetStatus: StaffAccountStatus = isSuspending ? 'SUSPENDED' : 'ACTIVE';

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="staff-status-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 space-y-4">
        <h3 id="staff-status-modal-title" className="text-lg font-semibold text-gray-900">
          {isSuspending ? 'Suspender acceso' : 'Activar acceso'}
        </h3>

        <p className="text-sm text-gray-600">
          {isSuspending
            ? `¿Está seguro de que desea suspender el acceso de ${staffMember.fullName}? El empleado no podrá iniciar sesión hasta que sea reactivado.`
            : `¿Está seguro de que desea activar el acceso de ${staffMember.fullName}? El empleado podrá volver a iniciar sesión en el sistema.`}
        </p>

        {error && (
          <div
            role="alert"
            className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700"
          >
            {error}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => onConfirm(staffMember.id, targetStatus)}
            disabled={isSubmitting}
            className={`px-4 py-2 text-sm font-medium text-white rounded-md disabled:opacity-50 ${
              isSuspending
                ? 'bg-red-600 hover:bg-red-700'
                : 'bg-green-600 hover:bg-green-700'
            }`}
          >
            {isSuspending ? 'Confirmar suspensión' : 'Confirmar activación'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default StaffStatusModal;
