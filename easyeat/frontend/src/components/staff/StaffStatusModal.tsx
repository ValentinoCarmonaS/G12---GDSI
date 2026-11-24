import React from 'react';
import { StaffAccountStatus, StaffMemberResponse } from '../../types/staff';
import { AccessibleModal } from '../diner/AccessibleModal';

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
  if (!staffMember) {
    return null;
  }

  const isSuspending = staffMember.status !== 'SUSPENDED';
  const targetStatus: StaffAccountStatus = isSuspending ? 'SUSPENDED' : 'ACTIVE';

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title={isSuspending ? 'Suspender acceso' : 'Activar acceso'}
      size="sm"
    >
      <div className="space-y-4">
        <p className="text-sm text-ee-text-muted">
          {isSuspending
            ? `¿Está seguro de que desea suspender el acceso de ${staffMember.fullName}? El empleado no podrá iniciar sesión hasta que sea reactivado.`
            : `¿Está seguro de que desea activar el acceso de ${staffMember.fullName}? El empleado podrá volver a iniciar sesión en el sistema.`}
        </p>

        {error && (
          <div
            role="alert"
            className="p-3 text-sm rounded-ee-sm bg-rose-50 border border-rose-200 text-rose-800"
          >
            {error}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="min-h-[44px] px-4 py-2 text-sm font-medium text-ee-text bg-white border border-ee-border-subtle rounded-ee-sm hover:bg-slate-50 disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => onConfirm(staffMember.id, targetStatus)}
            disabled={isSubmitting}
            className={`min-h-[44px] px-4 py-2 text-sm font-medium text-white rounded-ee-sm disabled:opacity-50 ${
              isSuspending
                ? 'bg-rose-700 hover:bg-rose-800'
                : 'bg-emerald-700 hover:bg-emerald-800'
            }`}
          >
            {isSuspending ? 'Confirmar suspensión' : 'Confirmar activación'}
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};

export default StaffStatusModal;
