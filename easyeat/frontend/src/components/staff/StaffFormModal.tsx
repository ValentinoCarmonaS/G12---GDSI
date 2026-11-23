import React, { useState, useEffect } from 'react';
import {
  StaffMemberResponse,
  StaffRegistrationRequest,
  StaffRole,
  StaffUpdateRequest,
} from '../../types/staff';
import { AccessibleModal } from '../diner/AccessibleModal';

export interface StaffFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: StaffRegistrationRequest | StaffUpdateRequest) => Promise<void>;
  initialData?: StaffMemberResponse | null;
  isLoading?: boolean;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const StaffFormModal: React.FC<StaffFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isLoading = false,
}) => {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<StaffRole>('ENCARGADO');
  const [fieldErrors, setFieldErrors] = useState<{
    fullName?: string;
    email?: string;
    role?: string;
  }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const isEdit = Boolean(initialData);

  useEffect(() => {
    if (initialData) {
      setFullName(initialData.fullName || '');
      setEmail(initialData.email || '');
      setRole(initialData.role || 'ENCARGADO');
    } else {
      setFullName('');
      setEmail('');
      setRole('ENCARGADO');
    }
    setFieldErrors({});
    setGeneralError(null);
  }, [initialData, isOpen]);

  const validate = (): boolean => {
    const errors: { fullName?: string; email?: string; role?: string } = {};

    if (!fullName.trim()) {
      errors.fullName = 'El nombre completo es obligatorio';
    } else if (fullName.trim().length < 2) {
      errors.fullName = 'El nombre debe contener al menos 2 caracteres';
    }

    if (!email.trim()) {
      errors.email = 'El correo electrónico es obligatorio';
    } else if (!EMAIL_REGEX.test(email.trim())) {
      errors.email = 'Formato de email inválido';
    }

    if (!role) {
      errors.role = 'Debés seleccionar un rol';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting || isLoading) return;

    setGeneralError(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      await onSubmit({
        fullName: fullName.trim(),
        email: email.trim().toLowerCase(),
        role,
      });
      onClose();
    } catch (err: unknown) {
      const error = err as {
        response?: { status?: number; data?: { detail?: string; message?: string } };
      };
      const detail = error.response?.data?.detail || error.response?.data?.message;
      if (detail) {
        setGeneralError(detail);
      } else {
        setGeneralError('Error al procesar la solicitud. Intente nuevamente.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AccessibleModal
      isOpen={isOpen}
      onClose={onClose}
      title={isEdit ? 'Editar Miembro del Personal' : 'Registrar Nuevo Empleado'}
      size="md"
    >
      <div className="space-y-4">
        {generalError && (
          <div
            role="alert"
            className="p-3 text-sm rounded-ee-sm bg-rose-50 border border-rose-200 text-rose-800"
          >
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="staff-fullName" className="block text-sm font-medium text-ee-text mb-1">
              Nombre completo
            </label>
            <input
              id="staff-fullName"
              type="text"
              value={fullName}
              onChange={(e) => {
                setFullName(e.target.value);
                if (fieldErrors.fullName) {
                  setFieldErrors((prev) => ({ ...prev, fullName: undefined }));
                }
              }}
              placeholder="Ej: Laura Martínez"
              className={`w-full min-h-[44px] px-3 py-2 border rounded-ee-sm text-sm text-ee-text bg-white focus:outline-none focus:ring-2 focus:ring-ee-focus-ring ${
                fieldErrors.fullName ? 'border-rose-500' : 'border-ee-border-subtle'
              }`}
            />
            {fieldErrors.fullName && (
              <p className="mt-1 text-xs text-rose-700">{fieldErrors.fullName}</p>
            )}
          </div>

          <div>
            <label htmlFor="staff-email" className="block text-sm font-medium text-ee-text mb-1">
              Correo electrónico
            </label>
            <input
              id="staff-email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldErrors.email) {
                  setFieldErrors((prev) => ({ ...prev, email: undefined }));
                }
              }}
              placeholder="laura@restaurante.com"
              className={`w-full min-h-[44px] px-3 py-2 border rounded-ee-sm text-sm text-ee-text bg-white focus:outline-none focus:ring-2 focus:ring-ee-focus-ring ${
                fieldErrors.email ? 'border-rose-500' : 'border-ee-border-subtle'
              }`}
            />
            {fieldErrors.email && (
              <p className="mt-1 text-xs text-rose-700">{fieldErrors.email}</p>
            )}
          </div>

          <div>
            <label htmlFor="staff-role" className="block text-sm font-medium text-ee-text mb-1">
              Rol asignado
            </label>
            <select
              id="staff-role"
              value={role}
              onChange={(e) => {
                setRole(e.target.value as StaffRole);
                if (fieldErrors.role) {
                  setFieldErrors((prev) => ({ ...prev, role: undefined }));
                }
              }}
              className={`w-full min-h-[44px] px-3 py-2 border rounded-ee-sm text-sm text-ee-text bg-white focus:outline-none focus:ring-2 focus:ring-ee-focus-ring ${
                fieldErrors.role ? 'border-rose-500' : 'border-ee-border-subtle'
              }`}
            >
              <option value="ENCARGADO">Encargado de Mostrador</option>
              <option value="CHEF">Chef</option>
              <option value="ADMINISTRADOR">Administrador</option>
            </select>
            {fieldErrors.role && (
              <p className="mt-1 text-xs text-rose-700">{fieldErrors.role}</p>
            )}
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-ee-border-subtle">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting || isLoading}
              className="min-h-[44px] px-4 py-2 text-sm font-medium text-ee-text bg-white border border-ee-border-subtle rounded-ee-sm hover:bg-slate-50 transition-colors disabled:opacity-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting || isLoading}
              className="min-h-[44px] px-4 py-2 text-sm font-medium text-white bg-ee-primary hover:opacity-95 rounded-ee-sm shadow-sm transition-opacity disabled:opacity-50"
            >
              {submitting || isLoading
                ? 'Guardando...'
                : isEdit
                ? 'Guardar Cambios'
                : 'Registrar Empleado'}
            </button>
          </div>
        </form>
      </div>
    </AccessibleModal>
  );
};

export default StaffFormModal;
