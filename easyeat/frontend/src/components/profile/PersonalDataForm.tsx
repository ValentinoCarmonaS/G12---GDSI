import React, { useState, useEffect } from 'react';

export interface PersonalDataFormProps {
  initialData?: {
    firstName?: string | null;
    lastName?: string | null;
    phone?: string | null;
    email?: string | null;
  };
  onSubmit: (data: { firstName: string; lastName: string; phone?: string }) => Promise<void>;
  onCancel?: () => void;
  onRequestEmailChange?: (newEmail: string) => Promise<void>;
  isSubmitting?: boolean;
}

const NAME_REGEX = /^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$/;
const PHONE_REGEX = /^\+?[0-9 -]{8,20}$/;

export const PersonalDataForm: React.FC<PersonalDataFormProps> = ({
  initialData,
  onSubmit,
  onCancel,
  onRequestEmailChange,
  isSubmitting = false,
}) => {
  const [firstName, setFirstName] = useState(initialData?.firstName || '');
  const [lastName, setLastName] = useState(initialData?.lastName || '');
  const [phone, setPhone] = useState(initialData?.phone || '');

  const [isEditingEmail, setIsEditingEmail] = useState(false);
  const [newEmail, setNewEmail] = useState('');
  const [emailSubmitting, setEmailSubmitting] = useState(false);

  const [errors, setErrors] = useState<{
    firstName?: string;
    lastName?: string;
    phone?: string;
    email?: string;
  }>({});

  useEffect(() => {
    setFirstName(initialData?.firstName || '');
    setLastName(initialData?.lastName || '');
    setPhone(initialData?.phone || '');
    setErrors({});
  }, [initialData]);

  const validate = () => {
    const errs: { firstName?: string; lastName?: string; phone?: string } = {};

    const trimmedFirst = firstName.trim();
    if (!trimmedFirst) {
      errs.firstName = 'El nombre es obligatorio';
    } else if (!NAME_REGEX.test(trimmedFirst)) {
      errs.firstName = 'El nombre solo puede contener letras y espacios';
    }

    const trimmedLast = lastName.trim();
    if (!trimmedLast) {
      errs.lastName = 'El apellido es obligatorio';
    } else if (!NAME_REGEX.test(trimmedLast)) {
      errs.lastName = 'El apellido solo puede contener letras y espacios';
    }

    const trimmedPhone = phone.trim();
    if (trimmedPhone && !PHONE_REGEX.test(trimmedPhone)) {
      errs.phone = 'Formato de teléfono inválido (debe contener entre 8 y 20 caracteres numéricos)';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    await onSubmit({
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      phone: phone.trim() ? phone.trim() : undefined,
    });
  };

  const handleCancel = () => {
    setFirstName(initialData?.firstName || '');
    setLastName(initialData?.lastName || '');
    setPhone(initialData?.phone || '');
    setErrors({});
    if (onCancel) {
      onCancel();
    }
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newEmail.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newEmail.trim())) {
      setErrors((prev) => ({ ...prev, email: 'Ingrese un correo electrónico válido' }));
      return;
    }

    if (onRequestEmailChange) {
      setEmailSubmitting(true);
      try {
        await onRequestEmailChange(newEmail.trim().toLowerCase());
        setIsEditingEmail(false);
        setNewEmail('');
      } finally {
        setEmailSubmitting(false);
      }
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6">
      <h2 className="text-xl font-semibold text-neutral-900 mb-6">Datos personales</h2>

      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <div>
          <label htmlFor="firstName" className="block text-sm font-medium text-neutral-700 mb-1">
            Nombre
          </label>
          <input
            id="firstName"
            type="text"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            disabled={isSubmitting}
            className={`w-full px-3.5 py-2.5 rounded-lg border ${
              errors.firstName ? 'border-red-500 focus:ring-red-500' : 'border-neutral-300 focus:ring-amber-500'
            } text-neutral-900 focus:outline-none focus:ring-2`}
          />
          {errors.firstName && <p className="mt-1 text-sm text-red-600">{errors.firstName}</p>}
        </div>

        <div>
          <label htmlFor="lastName" className="block text-sm font-medium text-neutral-700 mb-1">
            Apellido
          </label>
          <input
            id="lastName"
            type="text"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            disabled={isSubmitting}
            className={`w-full px-3.5 py-2.5 rounded-lg border ${
              errors.lastName ? 'border-red-500 focus:ring-red-500' : 'border-neutral-300 focus:ring-amber-500'
            } text-neutral-900 focus:outline-none focus:ring-2`}
          />
          {errors.lastName && <p className="mt-1 text-sm text-red-600">{errors.lastName}</p>}
        </div>

        <div>
          <label htmlFor="phone" className="block text-sm font-medium text-neutral-700 mb-1">
            Teléfono <span className="text-neutral-400 font-normal">(opcional)</span>
          </label>
          <input
            id="phone"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            disabled={isSubmitting}
            placeholder="+54 9 11 2345-6789"
            className={`w-full px-3.5 py-2.5 rounded-lg border ${
              errors.phone ? 'border-red-500 focus:ring-red-500' : 'border-neutral-300 focus:ring-amber-500'
            } text-neutral-900 focus:outline-none focus:ring-2`}
          />
          {errors.phone && <p className="mt-1 text-sm text-red-600">{errors.phone}</p>}
        </div>

        <div className="pt-2">
          <label className="block text-sm font-medium text-neutral-700 mb-1">
            Correo electrónico principal
          </label>
          <div className="flex items-center gap-3">
            <input
              type="email"
              readOnly
              value={initialData?.email || ''}
              className="flex-1 px-3.5 py-2.5 rounded-lg border border-neutral-200 bg-neutral-50 text-neutral-600 cursor-not-allowed"
            />
            {onRequestEmailChange && (
              <button
                type="button"
                onClick={() => setIsEditingEmail(!isEditingEmail)}
                className="px-4 py-2.5 text-sm font-medium text-amber-700 hover:text-amber-800 bg-amber-50 hover:bg-amber-100 rounded-lg transition-colors"
              >
                {isEditingEmail ? 'Cancelar' : 'Modificar correo'}
              </button>
            )}
          </div>
        </div>

        {isEditingEmail && (
          <div className="p-4 bg-amber-50/50 rounded-lg border border-amber-200/60 mt-3 space-y-3">
            <h4 className="text-sm font-medium text-amber-950">Solicitar nuevo correo electrónico</h4>
            <p className="text-xs text-amber-800">
              Se enviará un enlace de verificación válido por 24 horas a la nueva dirección. El correo actual seguirá activo hasta confirmar el cambio.
            </p>
            <div className="flex gap-2">
              <input
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder="nuevo.correo@ejemplo.com"
                disabled={emailSubmitting}
                className="flex-1 px-3 py-2 text-sm rounded-lg border border-amber-300 focus:outline-none focus:ring-2 focus:ring-amber-500"
              />
              <button
                type="button"
                onClick={handleEmailSubmit}
                disabled={emailSubmitting}
                className="px-4 py-2 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 rounded-lg disabled:opacity-50 transition-colors"
              >
                {emailSubmitting ? 'Enviando...' : 'Enviar verificación'}
              </button>
            </div>
            {errors.email && <p className="text-xs text-red-600">{errors.email}</p>}
          </div>
        )}

        <div className="flex items-center justify-end gap-3 pt-4 border-t border-neutral-100">
          <button
            type="button"
            onClick={handleCancel}
            disabled={isSubmitting}
            className="px-5 py-2.5 rounded-lg text-sm font-medium text-neutral-700 hover:bg-neutral-100 transition-colors disabled:opacity-50"
          >
            Descartar cambios
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="px-5 py-2.5 rounded-lg text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 transition-colors disabled:opacity-50 shadow-sm"
          >
            {isSubmitting ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default PersonalDataForm;
