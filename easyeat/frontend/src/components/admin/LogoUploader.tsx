import React, { useRef, useState, useEffect } from 'react';
import { Upload, Image as ImageIcon, AlertCircle } from 'lucide-react';
import { resolveApiMediaUrl } from '../../utils/mediaUtils';

interface LogoUploaderProps {
  currentLogoUrl?: string | null;
  onUpload: (file: File) => Promise<void> | void;
  isUploading?: boolean;
  disabled?: boolean;
}

const MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp', 'image/svg+xml'];

export const LogoUploader: React.FC<LogoUploaderProps> = ({
  currentLogoUrl,
  onUpload,
  isUploading = false,
  disabled = false,
}) => {
  const [error, setError] = useState<string | null>(null);
  const [optimisticPreview, setOptimisticPreview] = useState<string | null>(null);
  const [imageError, setImageError] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      if (optimisticPreview && typeof URL.revokeObjectURL === 'function') {
        URL.revokeObjectURL(optimisticPreview);
      }
    };
  }, [optimisticPreview]);

  useEffect(() => {
    setImageError(false);
  }, [currentLogoUrl, optimisticPreview]);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    setError(null);
    const file = e.target.files?.[0];
    if (!file) return;

    if (!ALLOWED_TYPES.includes(file.type.toLowerCase()) && !file.name.match(/\.(png|jpe?g|webp|svg)$/i)) {
      setError('Formato no soportado. Debe ser PNG, JPG, WEBP o SVG.');
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      setError('El archivo supera el tamaño máximo permitido de 2MB.');
      return;
    }

    if (typeof URL.createObjectURL === 'function') {
      const localPreviewUrl = URL.createObjectURL(file);
      setOptimisticPreview(localPreviewUrl);
    }

    try {
      await onUpload(file);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al subir el logotipo.';
      setError(msg);
      setOptimisticPreview(null);
    }
  };

  const displayLogoUrl = optimisticPreview || currentLogoUrl;

  return (
    <div className="space-y-3">
      <label className="block text-sm font-medium text-stone-700 dark:text-stone-300">
        Logotipo del Establecimiento
      </label>

      <div className="flex items-center gap-4">
        <div className="w-20 h-20 rounded-xl border-2 border-dashed border-stone-300 dark:border-stone-700 bg-stone-50 dark:bg-stone-900 flex items-center justify-center overflow-hidden flex-shrink-0 shadow-inner">
          {displayLogoUrl && !imageError ? (
            <img
              src={resolveApiMediaUrl(displayLogoUrl)}
              alt="Logotipo de la marca"
              onError={() => setImageError(true)}
              className="w-full h-full object-contain p-1"
            />
          ) : (
            <ImageIcon className="w-8 h-8 text-stone-400" />
          )}
        </div>

        <div className="flex-1 space-y-1">
          <input
            ref={fileInputRef}
            type="file"
            accept=".png,.jpg,.jpeg,.webp,.svg,image/*"
            data-testid="logo-file-input"
            onChange={handleFileChange}
            disabled={disabled || isUploading}
            className="hidden"
          />

          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={disabled || isUploading}
            className="inline-flex items-center gap-2 px-3 py-2 border border-stone-300 dark:border-stone-700 rounded-lg text-sm font-medium text-stone-700 dark:text-stone-200 bg-white dark:bg-stone-800 hover:bg-stone-50 dark:hover:bg-stone-700 shadow-sm transition-colors disabled:opacity-50"
          >
            <Upload className="w-4 h-4 text-stone-500" />
            {isUploading ? 'Subiendo...' : 'Seleccionar imagen'}
          </button>
          <p className="text-xs text-stone-500 dark:text-stone-400">
            Formatos soportados: PNG, JPG, WEBP, SVG (máx. 2MB).
          </p>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-1.5 text-xs text-red-600 dark:text-red-400">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
};
