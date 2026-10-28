import React, { useState, useEffect, useRef } from 'react';
import { DISH_CATEGORIES, type DishCategory, type CreateDishRequest } from '@/types/dish';

export interface BasicIdentityStepProps {
  initialData?: Partial<CreateDishRequest & { photoUrl?: string | null }>;
  onSave?: (data: CreateDishRequest, photoFile?: File) => void | Promise<void>;
  onPhotoDelete?: () => void | Promise<void>;
  isReadOnly?: boolean;
  isSaving?: boolean;
}

const MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5 MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const BasicIdentityStep: React.FC<BasicIdentityStepProps> = ({
  initialData,
  onSave,
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

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setName(e.target.value);
    if (nameError && e.target.value.trim()) {
      setNameError(null);
    }
  };

  const handleDescriptionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setDescription(e.target.value);
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
      const objectUrl = URL.createObjectURL(file);
      setPreviewUrl(objectUrl);
    } else {
      setPreviewUrl('preview-' + file.name);
    }
  };

  const handleRemovePhoto = async () => {
    setSelectedFile(null);
    setPreviewUrl(null);
    setPhotoError(null);
    if (fileInputRef.current) fileInputRef.current.value = '';

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

    if (onSave) {
      await onSave(
        {
          name: trimmedName,
          category,
          description: description ? description.trim() : undefined,
        },
        selectedFile || undefined
      );
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-2xl bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm">
      {/* Dish Name */}
      <div>
        <div className="flex justify-between items-center mb-1.5">
          <label htmlFor="dish-name" className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Nombre del plato <span className="text-red-500">*</span>
          </label>
          <span className="text-xs text-zinc-500 dark:text-zinc-400">
            {name.length}/100
          </span>
        </div>
        <input
          id="dish-name"
          type="text"
          maxLength={100}
          value={name}
          onChange={handleNameChange}
          disabled={isReadOnly || isSaving}
          placeholder="Ej: Risotto de Hongos Portobello"
          className={`w-full px-3.5 py-2 text-sm rounded-lg border bg-zinc-50 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 transition focus:outline-none focus:ring-2 ${
            nameError
              ? 'border-red-500 focus:ring-red-400/20'
              : 'border-zinc-300 dark:border-zinc-700 focus:border-amber-500 focus:ring-amber-400/20'
          }`}
        />
        {nameError && (
          <p className="mt-1.5 text-xs text-red-600 dark:text-red-400 font-medium">
            {nameError}
          </p>
        )}
      </div>

      {/* Dish Category */}
      <div>
        <label htmlFor="dish-category" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100 mb-1.5">
          Categoría <span className="text-red-500">*</span>
        </label>
        <select
          id="dish-category"
          value={category}
          onChange={(e) => setCategory(e.target.value as DishCategory)}
          disabled={isReadOnly || isSaving}
          className="w-full px-3.5 py-2 text-sm rounded-lg border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:border-amber-500 focus:ring-amber-400/20"
        >
          {DISH_CATEGORIES.map((cat) => (
            <option key={cat} value={cat}>
              {cat}
            </option>
          ))}
        </select>
      </div>

      {/* Dish Description */}
      <div>
        <div className="flex justify-between items-center mb-1.5">
          <label htmlFor="dish-description" className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Descripción
          </label>
          <span className="text-xs text-zinc-500 dark:text-zinc-400">
            {description.length}/500
          </span>
        </div>
        <textarea
          id="dish-description"
          rows={3}
          maxLength={500}
          value={description}
          onChange={handleDescriptionChange}
          disabled={isReadOnly || isSaving}
          placeholder="Describe la preparación, texturas o notas culinarias para el personal de servicio..."
          className="w-full px-3.5 py-2 text-sm rounded-lg border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:border-amber-500 focus:ring-amber-400/20"
        />
      </div>

      {/* Dish Photo */}
      <div>
        <label htmlFor="dish-photo" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100 mb-1.5">
          Fotografía
        </label>
        <input
          id="dish-photo"
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={handleFileChange}
          disabled={isReadOnly || isSaving}
          className="block w-full text-xs text-zinc-600 dark:text-zinc-400 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-amber-50 file:text-amber-700 hover:file:bg-amber-100 dark:file:bg-amber-950/60 dark:file:text-amber-300 cursor-pointer"
        />
        <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
          Tamaño máximo 5 MB. Solo imágenes.
        </p>

        {photoError && (
          <p className="mt-1.5 text-xs text-red-600 dark:text-red-400 font-medium">
            {photoError}
          </p>
        )}

        {previewUrl && (
          <div className="mt-4 flex items-center space-x-4 p-3 bg-zinc-50 dark:bg-zinc-800/60 rounded-lg border border-zinc-200 dark:border-zinc-700">
            <img
              src={previewUrl}
              alt="Vista previa del plato"
              className="w-20 h-20 object-cover rounded-md border border-zinc-200 dark:border-zinc-700"
            />
            <div className="flex-1">
              <p className="text-xs text-zinc-600 dark:text-zinc-300">
                {selectedFile ? selectedFile.name : 'Foto actual del plato'}
              </p>
              {!isReadOnly && (
                <button
                  type="button"
                  onClick={handleRemovePhoto}
                  disabled={isSaving}
                  className="mt-2 text-xs font-semibold text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 focus:outline-none"
                >
                  Eliminar foto
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Actions */}
      {!isReadOnly && (
        <div className="pt-4 flex justify-end">
          <button
            type="submit"
            disabled={isSaving}
            className="px-5 py-2.5 bg-amber-500 hover:bg-amber-600 text-zinc-950 font-semibold text-sm rounded-lg shadow-sm transition focus:outline-none focus:ring-2 focus:ring-amber-400 focus:ring-offset-2 disabled:opacity-50"
          >
            {isSaving ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      )}
    </form>
  );
};

export default BasicIdentityStep;
