import React, { useState, useEffect } from 'react';
import {
  StaffMemberResponse,
  StaffRegistrationRequest,
  StaffRole,
  StaffUpdateRequest,
} from '../../types/staff';

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

  if (!isOpen) return null;

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
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="staff-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 space-y-5 border border-gray-100">
        <div className="flex items-center justify-between border-b pb-3">
          <h3 id="staff-modal-title" className="text-lg font-bold text-gray-900">
            {isEdit ? 'Editar Miembro del Personal' : 'Registrar Nuevo Empleado'}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-lg font-bold"
          >
            ✕
          </button>
        </div>

        {generalError && (
          <div
            role="alert"
            className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700 animate-fade-in"
          >
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="staff-fullName" className="block text-sm font-medium text-gray-700 mb-1">
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
              placeholder="Ej. Juan Pérez"
              disabled={submitting || isLoading}
              className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                fieldErrors.fullName
                  ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                  : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'
              }`}
            />
            {fieldErrors.fullName && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.fullName}</p>
            )}
          </div>

          <div>
            <label htmlFor="staff-email" className="block text-sm font-medium text-gray-700 mb-1">
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
              placeholder="empleado@restaurante.com"
              disabled={submitting || isLoading}
              className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                fieldErrors.email
                  ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                  : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'
              }`}
            />
            {fieldErrors.email && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>
            )}
          </div>

          <div>
            <label htmlFor="staff-role" className="block text-sm font-medium text-gray-700 mb-1">
              Rol
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
              disabled={submitting || isLoading}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white"
            >
              <option value="ADMINISTRADOR">Administrador</option>
              <option value="CHEF">Chef</option>
              <option value="ENCARGADO">Encargado de Mostrador</option>
            </select>
            {fieldErrors.role && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.role}</p>
            )}
          </div>

          <div className="flex items-center justify-end space-x-3 pt-3 border-t">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting || isLoading}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting || isLoading}
              className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 transition-colors"
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
    </div>
  );
};

export default StaffFormModal;
