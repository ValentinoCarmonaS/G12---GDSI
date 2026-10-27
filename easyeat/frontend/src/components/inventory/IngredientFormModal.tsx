import React, { useState, useEffect } from 'react';
import {
  AdminIngredientDetailResponse,
  CreateIngredientRequest,
  IngredientCategory,
  UnitOfMeasure,
  UpdateIngredientRequest,
} from '../../types/inventory';

export interface IngredientFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: CreateIngredientRequest | UpdateIngredientRequest) => Promise<void>;
  initialData?: AdminIngredientDetailResponse | null;
  isLoading?: boolean;
}

const CATEGORIES: { value: IngredientCategory; label: string }[] = [
  { value: 'Produce', label: 'Vegetales y Frutas (Produce)' },
  { value: 'Meat', label: 'Carnes (Meat)' },
  { value: 'Dairy', label: 'Lácteos (Dairy)' },
  { value: 'Dry Goods', label: 'Secos y Almacén (Dry Goods)' },
  { value: 'Spices', label: 'Especias y Condimentos (Spices)' },
];

const UNITS: { value: UnitOfMeasure; label: string }[] = [
  { value: 'kg', label: 'Kilogramos (kg)' },
  { value: 'g', label: 'Gramos (g)' },
  { value: 'l', label: 'Litros (l)' },
  { value: 'ml', label: 'Mililitros (ml)' },
  { value: 'u', label: 'Unidades (u)' },
];

export const IngredientFormModal: React.FC<IngredientFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isLoading = false,
}) => {
  const [name, setName] = useState('');
  const [category, setCategory] = useState<IngredientCategory>('Produce');
  const [unitOfMeasure, setUnitOfMeasure] = useState<UnitOfMeasure>('kg');
  const [fieldErrors, setFieldErrors] = useState<{
    name?: string;
    category?: string;
    unitOfMeasure?: string;
  }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const isEdit = Boolean(initialData);
  // Domain rule (FR-013): UOM cannot be changed if stock > 0
  const isUomLocked = Boolean(isEdit && initialData && initialData.currentStock > 0);

  useEffect(() => {
    if (initialData) {
      setName(initialData.name || '');
      setCategory(initialData.category || 'Produce');
      setUnitOfMeasure(initialData.unitOfMeasure || 'kg');
    } else {
      setName('');
      setCategory('Produce');
      setUnitOfMeasure('kg');
    }
    setFieldErrors({});
    setGeneralError(null);
  }, [initialData, isOpen]);

  if (!isOpen) return null;

  const validate = (): boolean => {
    const errors: {
      name?: string;
      category?: string;
      unitOfMeasure?: string;
    } = {};

    if (!name.trim()) {
      errors.name = 'El nombre es obligatorio';
    } else if (name.trim().length < 2) {
      errors.name = 'El nombre debe tener al menos 2 caracteres';
    }

    if (!category) {
      errors.category = 'La categoría es obligatoria';
    }

    if (!unitOfMeasure) {
      errors.unitOfMeasure = 'La unidad de medida es obligatoria';
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
        name: name.trim(),
        category,
        unitOfMeasure,
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
        setGeneralError('Error al guardar el ingrediente. Intente nuevamente.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="ingredient-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 space-y-5 border border-gray-100">
        <div className="flex items-center justify-between border-b pb-3">
          <h3 id="ingredient-modal-title" className="text-lg font-bold text-gray-900">
            {isEdit ? 'Editar Ingrediente' : 'Nuevo Ingrediente'}
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
            <label htmlFor="ingredient-name" className="block text-sm font-medium text-gray-700 mb-1">
              Nombre del ingrediente
            </label>
            <input
              id="ingredient-name"
              type="text"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                if (fieldErrors.name) setFieldErrors((prev) => ({ ...prev, name: undefined }));
              }}
              placeholder="Ej. Harina 000"
              disabled={submitting || isLoading}
              className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                fieldErrors.name
                  ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                  : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'
              }`}
            />
            {fieldErrors.name && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.name}</p>
            )}
          </div>

          <div>
            <label htmlFor="ingredient-category" className="block text-sm font-medium text-gray-700 mb-1">
              Categoría
            </label>
            <select
              id="ingredient-category"
              value={category}
              onChange={(e) => {
                setCategory(e.target.value as IngredientCategory);
                if (fieldErrors.category) setFieldErrors((prev) => ({ ...prev, category: undefined }));
              }}
              disabled={submitting || isLoading}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="ingredient-uom" className="block text-sm font-medium text-gray-700 mb-1">
              Unidad de medida
            </label>
            <select
              id="ingredient-uom"
              value={unitOfMeasure}
              onChange={(e) => {
                setUnitOfMeasure(e.target.value as UnitOfMeasure);
                if (fieldErrors.unitOfMeasure) setFieldErrors((prev) => ({ ...prev, unitOfMeasure: undefined }));
              }}
              disabled={submitting || isLoading || isUomLocked}
              className={`w-full px-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 transition-colors ${
                isUomLocked
                  ? 'bg-gray-100 border-gray-300 text-gray-500 cursor-not-allowed'
                  : 'bg-white border-gray-300 focus:ring-blue-500'
              }`}
            >
              {UNITS.map((u) => (
                <option key={u.value} value={u.value}>
                  {u.label}
                </option>
              ))}
            </select>
            {isUomLocked && (
              <p className="mt-1 text-xs text-amber-700 bg-amber-50 p-2 rounded border border-amber-200">
                🔒 No se puede cambiar la unidad de medida mientras haya stock positivo registrado.
              </p>
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
                : 'Guardar Ingrediente'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default IngredientFormModal;

