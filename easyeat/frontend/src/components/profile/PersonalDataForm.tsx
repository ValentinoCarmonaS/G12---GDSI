import React, { useState, useEffect, useRef } from 'react';
import { User as UserIcon, Camera, Trash2 } from 'lucide-react';
import { PhotoDeleteConfirmModal } from './PhotoDeleteConfirmModal';
import { getAuthenticatedAvatarUrl } from '../../utils/avatarUtils';

export interface PersonalDataFormProps {
  initialData?: {
    firstName?: string | null;
    lastName?: string | null;
    phone?: string | null;
    email?: string | null;
    age?: number | null;
    gender?: string | null;
    address?: string | null;
    avatarUrl?: string | null;
  };
  onSubmit: (data: {
    firstName: string;
    lastName: string;
    phone?: string;
    age?: number;
    gender?: string;
    address?: string;
  }) => Promise<void>;
  onCancel?: () => void;
  onRequestEmailChange?: (newEmail: string) => Promise<void>;
  onUploadAvatar?: (file: File) => Promise<unknown>;
  onRemoveAvatar?: () => Promise<unknown>;
  isSubmitting?: boolean;
}

const NAME_REGEX = /^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$/;
const PHONE_REGEX = /^\+?[0-9 -]{8,20}$/;
const MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5 MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];

export const PersonalDataForm: React.FC<PersonalDataFormProps> = ({
  initialData,
  onSubmit,
  onCancel,
  onRequestEmailChange,
  onUploadAvatar,
  onRemoveAvatar,
  isSubmitting = false,
}) => {
  const [firstName, setFirstName] = useState(initialData?.firstName || '');
  const [lastName, setLastName] = useState(initialData?.lastName || '');
  const [phone, setPhone] = useState(initialData?.phone || '');
  const [age, setAge] = useState<string>(initialData?.age != null ? String(initialData.age) : '');
  const [gender, setGender] = useState(initialData?.gender || '');
  const [address, setAddress] = useState(initialData?.address || '');

  // Avatar state
  const [avatarPreview, setAvatarPreview] = useState<string | null>(initialData?.avatarUrl || null);
  const [stagedPhotoFile, setStagedPhotoFile] = useState<File | null>(null);
  const [isPhotoRemovalPending, setIsPhotoRemovalPending] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Email state
  const [isEditingEmail, setIsEditingEmail] = useState(false);
  const [newEmail, setNewEmail] = useState('');
  const [emailSubmitting, setEmailSubmitting] = useState(false);

  const [errors, setErrors] = useState<{
    firstName?: string;
    lastName?: string;
    phone?: string;
    email?: string;
    age?: string;
    gender?: string;
    address?: string;
    photo?: string;
  }>({});

  useEffect(() => {
    setFirstName(initialData?.firstName || '');
    setLastName(initialData?.lastName || '');
    setPhone(initialData?.phone || '');
    setAge(initialData?.age != null ? String(initialData.age) : '');
    setGender(initialData?.gender || '');
    setAddress(initialData?.address || '');
    setAvatarPreview(initialData?.avatarUrl || null);
    setStagedPhotoFile(null);
    setIsPhotoRemovalPending(false);
    setErrors({});
  }, [initialData]);

  const handlePhotoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > MAX_PHOTO_SIZE) {
      setErrors((prev) => ({
        ...prev,
        photo: 'El archivo supera el tamaño máximo permitido de 5 MB.',
      }));
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    if (!ALLOWED_MIME_TYPES.includes(file.type.toLowerCase())) {
      setErrors((prev) => ({
        ...prev,
        photo: 'Formato no soportado. Debe ser JPEG, PNG o WebP.',
      }));
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setErrors((prev) => {
      const next = { ...prev };
      delete next.photo;
      return next;
    });

    setStagedPhotoFile(file);
    setIsPhotoRemovalPending(false);
    const previewUrl = URL.createObjectURL(file);
    setAvatarPreview(previewUrl);
  };

  const handleConfirmPhotoRemoval = () => {
    setIsPhotoRemovalPending(true);
    setStagedPhotoFile(null);
    setAvatarPreview(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const validate = () => {
    const errs: {
      firstName?: string;
      lastName?: string;
      phone?: string;
      age?: string;
      gender?: string;
      address?: string;
    } = {};

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

    const trimmedAge = age.trim();
    if (trimmedAge) {
      const parsedAge = parseInt(trimmedAge, 10);
      if (isNaN(parsedAge) || parsedAge < 14 || parsedAge > 120) {
        errs.age = 'La edad debe ser un número entero entre 14 y 120 años.';
      }
    }

    const trimmedAddress = address.trim();
    if (trimmedAddress && trimmedAddress.length > 255) {
      errs.address = 'El domicilio no puede exceder los 255 caracteres.';
    }

    setErrors((prev) => ({ ...prev, ...errs }));
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    // Consolidate deferred photo removal if confirmed
    if (isPhotoRemovalPending && onRemoveAvatar) {
      await onRemoveAvatar();
      window.dispatchEvent(new CustomEvent('easyeat:avatar-changed'));
    }

    // Consolidate staged photo upload if selected
    if (stagedPhotoFile && onUploadAvatar) {
      await onUploadAvatar(stagedPhotoFile);
      window.dispatchEvent(new CustomEvent('easyeat:avatar-changed'));
    }

    const parsedAge = age.trim() ? parseInt(age.trim(), 10) : undefined;
    const cleanGender = gender.trim() ? gender.trim() : undefined;
    const cleanAddress = address.trim() ? address.trim() : undefined;

    await onSubmit({
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      phone: phone.trim() ? phone.trim() : undefined,
      age: parsedAge,
      gender: cleanGender,
      address: cleanAddress,
    });
  };

  const handleCancel = () => {
    setFirstName(initialData?.firstName || '');
    setLastName(initialData?.lastName || '');
    setPhone(initialData?.phone || '');
    setAge(initialData?.age != null ? String(initialData.age) : '');
    setGender(initialData?.gender || '');
    setAddress(initialData?.address || '');
    setAvatarPreview(initialData?.avatarUrl || null);
    setStagedPhotoFile(null);
    setIsPhotoRemovalPending(false);
    setErrors({});
    if (fileInputRef.current) fileInputRef.current.value = '';
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

      {/* Avatar Management Section */}
      <div className="flex flex-col sm:flex-row items-center gap-6 pb-6 mb-6 border-b border-neutral-100">
        <div className="relative">
          <div className="w-24 h-24 rounded-full overflow-hidden bg-neutral-100 border-2 border-neutral-200 flex items-center justify-center text-neutral-400">
            {avatarPreview ? (
              <img
                src={getAuthenticatedAvatarUrl(avatarPreview)}
                alt="Avatar comensal"
                className="w-full h-full object-cover"
              />
            ) : (
              <UserIcon className="w-12 h-12 text-neutral-400" />
            )}
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-3">
            <label
              htmlFor="avatar-file-input"
              className="px-4 py-2 text-sm font-medium text-amber-700 bg-amber-50 hover:bg-amber-100 rounded-lg cursor-pointer transition-colors inline-flex items-center gap-2"
            >
              <Camera className="w-4 h-4" />
              {avatarPreview ? 'Cambiar foto' : 'Subir foto'}
            </label>
            <input
              id="avatar-file-input"
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handlePhotoSelect}
              className="sr-only"
            />

            {avatarPreview && (
              <button
                type="button"
                onClick={() => setIsDeleteModalOpen(true)}
                className="px-4 py-2 text-sm font-medium text-red-600 hover:text-red-700 bg-red-50 hover:bg-red-100 rounded-lg transition-colors inline-flex items-center gap-2"
              >
                <Trash2 className="w-4 h-4" />
                Eliminar foto
              </button>
            )}
          </div>
          <span className="text-xs text-neutral-500">
            Formatos admitidos: JPEG, PNG, WebP. Tamaño máximo: 5 MB.
          </span>
          {errors.photo && <p className="text-xs text-red-600">{errors.photo}</p>}
        </div>
      </div>

      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
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
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
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

          <div>
            <label htmlFor="age" className="block text-sm font-medium text-neutral-700 mb-1">
              Edad <span className="text-neutral-400 font-normal">(opcional)</span>
            </label>
            <input
              id="age"
              type="number"
              min="14"
              max="120"
              value={age}
              onChange={(e) => setAge(e.target.value)}
              disabled={isSubmitting}
              placeholder="Ej: 28"
              className={`w-full px-3.5 py-2.5 rounded-lg border ${
                errors.age ? 'border-red-500 focus:ring-red-500' : 'border-neutral-300 focus:ring-amber-500'
              } text-neutral-900 focus:outline-none focus:ring-2`}
            />
            {errors.age && <p className="mt-1 text-sm text-red-600">{errors.age}</p>}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div>
            <label htmlFor="gender" className="block text-sm font-medium text-neutral-700 mb-1">
              Género <span className="text-neutral-400 font-normal">(opcional)</span>
            </label>
            <select
              id="gender"
              value={gender}
              onChange={(e) => setGender(e.target.value)}
              disabled={isSubmitting}
              className={`w-full px-3.5 py-2.5 rounded-lg border ${
                errors.gender ? 'border-red-500 focus:ring-red-500' : 'border-neutral-300 focus:ring-amber-500'
              } text-neutral-900 focus:outline-none focus:ring-2 bg-white`}
            >
              <option value="">Seleccione género...</option>
              <option value="Femenino">Femenino</option>
              <option value="Masculino">Masculino</option>
              <option value="No binario">No binario</option>
              <option value="Prefiero no responder">Prefiero no responder</option>
            </select>
            {errors.gender && <p className="mt-1 text-sm text-red-600">{errors.gender}</p>}
          </div>

          <div>
            <label htmlFor="address" className="block text-sm font-medium text-neutral-700 mb-1">
              Domicilio de contacto <span className="text-neutral-400 font-normal">(opcional)</span>
            </label>
            <input
              id="address"
              type="text"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              disabled={isSubmitting}
              placeholder="Ej: Av. Santa Fe 1234, CABA"
              className={`w-full px-3.5 py-2.5 rounded-lg border ${
                errors.address ? 'border-red-500 focus:ring-red-500' : 'border-neutral-300 focus:ring-amber-500'
              } text-neutral-900 focus:outline-none focus:ring-2`}
            />
            {errors.address && <p className="mt-1 text-sm text-red-600">{errors.address}</p>}
          </div>
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

      {/* Confirmation Modal for Deferred Removal */}
      <PhotoDeleteConfirmModal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={handleConfirmPhotoRemoval}
      />
    </div>
  );
};

export default PersonalDataForm;
