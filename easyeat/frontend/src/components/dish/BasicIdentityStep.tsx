import React, { useState, useEffect, useRef } from 'react';
import { DISH_CATEGORIES, type DishCategory, type CreateDishRequest } from '@/types/dish';
import { resolveApiMediaUrl } from '@/utils/mediaUtils';
import { BrutalistCard } from '@/components/chef/BrutalistCard';

export interface BasicIdentityStepProps {
  initialData?: Partial<CreateDishRequest & { photoUrl?: string | null }>;
  onSave?: (data: CreateDishRequest, photoFile?: File) => void | Promise<void>;
  onNext?: (data: CreateDishRequest, photoFile?: File | null) => void;
  onDraftChange?: (data: CreateDishRequest, photoFile?: File | null) => void;
  onPhotoDelete?: () => void | Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const BasicIdentityStep: React.FC<BasicIdentityStepProps> = ({
  initialData,
  onSave,
  onNext,
  onDraftChange,
  onPhotoDelete,
  isReadOnly = false,
  isSaving = false,
}) => {
  const [name, setName] = useState(initialData?.name || '');
  const [category, setCategory] = useState<DishCategory>(
    (initialData?.category as DishCategory) || 'Platos principales'
  );
  const [description, setDescription] = useState(initialData?.description || '');
  const [nameError, setNameError] = useState<string | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(initialData?.photoUrl || null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (initialData?.name !== undefined) setName(initialData.name);
    if (initialData?.category !== undefined) setCategory(initialData.category as DishCategory);
    if (initialData?.description !== undefined) setDescription(initialData.description || '');
    if (initialData?.photoUrl !== undefined) setPreviewUrl(initialData.photoUrl);
  }, [initialData]);

  const emitDraftChange = (
    nextName: string,
    nextCategory: DishCategory,
    nextDescription: string,
    nextFile: File | null
  ) => {
    if (onDraftChange) {
      onDraftChange(
        {
          name: nextName,
          category: nextCategory,
          description: nextDescription,
        },
        nextFile
      );
    }
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setName(val);
    if (nameError && val.trim()) {
      setNameError(null);
    }
    emitDraftChange(val, category, description, selectedFile);
  };

  const handleCategoryChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value as DishCategory;
    setCategory(val);
    emitDraftChange(name, val, description, selectedFile);
  };

  const handleDescriptionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const val = e.target.value;
    setDescription(val);
    emitDraftChange(name, category, val, selectedFile);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setPhotoError(null);
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > MAX_PHOTO_BYTES) {
      setPhotoError('El archivo no debe superar los 5 MB.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      setPhotoError('Formatos soportados: JPEG, PNG, WebP.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setSelectedFile(file);
    if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      setPreviewUrl(URL.createObjectURL(file));
    } else {
      setPreviewUrl('preview-' + file.name);
    }
    emitDraftChange(name, category, description, file);
  };

  const handleRemovePhoto = async () => {
    setSelectedFile(null);
    setPreviewUrl(null);
    setPhotoError(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
    emitDraftChange(name, category, description, null);

    if (onPhotoDelete) {
      await onPhotoDelete();
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedName = name.trim();

    if (!trimmedName) {
      setNameError('El nombre del plato es obligatorio');
      return;
    }

    const payload: CreateDishRequest = {
      name: trimmedName,
      category,
      description: description.trim() || undefined,
    };

    if (onNext) {
      onNext(payload, selectedFile);
      return;
    }

    if (onSave) {
      await onSave(payload, selectedFile || undefined);
    }
  };

  const resolvedPreview = previewUrl ? resolveApiMediaUrl(previewUrl) || previewUrl : null;

  return (
    <BrutalistCard className="p-6">
      <form onSubmit={handleSubmit} noValidate className="space-y-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="space-y-4">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="dish-name"
                  className="block text-sm font-bold uppercase tracking-wide text-[#0A0A0A]"
                >
                  Nombre del plato *
                </label>
                <span className="font-mono text-xs text-[#52525B]">{name.length}/120</span>
              </div>
              <input
                id="dish-name"
                type="text"
                maxLength={120}
                value={name}
                onChange={handleNameChange}
                disabled={isReadOnly || isSaving}
                placeholder="Ej: Milanesa a la napolitana"
                className="w-full px-3 py-2.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] font-medium focus:outline-none focus:ring-2 focus:ring-[#0A0A0A] disabled:bg-[#F5F5F4] disabled:text-[#52525B]"
              />
              {nameError && (
                <p role="alert" className="mt-1.5 text-xs font-bold text-[#DC2626]">
                  {nameError}
                </p>
              )}
            </div>

            <div>
              <label
                htmlFor="dish-category"
                className="block text-sm font-bold uppercase tracking-wide text-[#0A0A0A] mb-1.5"
              >
                Categoría *
              </label>
              <select
                id="dish-category"
                value={category}
                onChange={handleCategoryChange}
                disabled={isReadOnly || isSaving}
                className="w-full px-3 py-2.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] font-medium focus:outline-none focus:ring-2 focus:ring-[#0A0A0A] disabled:bg-[#F5F5F4] disabled:text-[#52525B]"
              >
                {DISH_CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="dish-description"
                  className="block text-sm font-bold uppercase tracking-wide text-[#0A0A0A]"
                >
                  Descripción
                </label>
                <span className="font-mono text-xs text-[#52525B]">
                  {description.length}/500
                </span>
              </div>
              <textarea
                id="dish-description"
                rows={4}
                maxLength={500}
                value={description}
                onChange={handleDescriptionChange}
                disabled={isReadOnly || isSaving}
                placeholder="Breve descripción culinaria del plato..."
                className="w-full px-3 py-2.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] font-medium focus:outline-none focus:ring-2 focus:ring-[#0A0A0A] disabled:bg-[#F5F5F4] disabled:text-[#52525B]"
              />
            </div>
          </div>

          <div className="space-y-3">
            <label
              htmlFor="dish-photo"
              className="block text-sm font-bold uppercase tracking-wide text-[#0A0A0A]"
            >
              Fotografía del plato
            </label>
            <div className="border-2 border-dashed border-[#0A0A0A] bg-[#F5F5F4] p-4 min-h-[220px] flex flex-col items-center justify-center gap-3">
              {resolvedPreview ? (
                <div className="w-full space-y-3">
                  <img
                    src={resolvedPreview}
                    alt="Vista previa del plato"
                    className="w-full h-48 object-cover border-2 border-[#0A0A0A]"
                  />
                  {!isReadOnly && (
                    <button
                      type="button"
                      onClick={handleRemovePhoto}
                      disabled={isSaving}
                      className="px-3 py-1.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#DC2626] text-xs font-bold uppercase hover:bg-[#FEE2E2]"
                    >
                      Quitar fotografía
                    </button>
                  )}
                </div>
              ) : (
                <p className="text-xs font-mono text-[#52525B] text-center">
                  JPEG, PNG o WebP — Máximo 5 MB
                </p>
              )}

              {!isReadOnly && (
                <input
                  ref={fileInputRef}
                  id="dish-photo"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  onChange={handleFileChange}
                  disabled={isSaving}
                  className="text-xs font-mono text-[#0A0A0A] file:mr-3 file:py-2 file:px-3 file:border-2 file:border-[#0A0A0A] file:bg-[#FFFFFF] file:text-[#0A0A0A] file:font-bold file:uppercase hover:file:bg-[#0A0A0A] hover:file:text-[#FFFFFF]"
                />
              )}
            </div>
            {photoError && (
              <p role="alert" className="text-xs font-bold text-[#DC2626]">
                {photoError}
              </p>
            )}
          </div>
        </div>

        <div className="flex justify-end pt-4 border-t-2 border-[#0A0A0A]">
          <button
            type="submit"
            disabled={isSaving}
            className="min-h-[48px] px-6 py-2.5 border-2 border-[#0A0A0A] bg-[#0A0A0A] text-[#FFFFFF] text-sm font-bold uppercase tracking-wide hover:bg-[#27272A] disabled:opacity-50"
          >
            {onNext ? 'Siguiente' : isSaving ? 'Guardando...' : 'Guardar y continuar'}
          </button>
        </div>
      </form>
    </BrutalistCard>
  );
};

export default BasicIdentityStep;
