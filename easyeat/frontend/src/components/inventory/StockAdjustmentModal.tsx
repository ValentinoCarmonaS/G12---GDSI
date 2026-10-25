import React, { useState, useEffect, useMemo } from 'react';
import {
  AdminIngredientDetailResponse,
  StockAdjustmentMode,
  StockAdjustmentRequest,
} from '../../types/inventory';

export interface StockAdjustmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  ingredient: AdminIngredientDetailResponse | null;
  onAdjust: (ingredientId: string, request: StockAdjustmentRequest) => Promise<void>;
  isLoading?: boolean;
}

export const StockAdjustmentModal: React.FC<StockAdjustmentModalProps> = ({
  isOpen,
  onClose,
  ingredient,
  onAdjust,
  isLoading = false,
}) => {
  const [mode, setMode] = useState<StockAdjustmentMode>('ABSOLUTE');
  const [value, setValue] = useState<string>('');
  const [justification, setJustification] = useState<string>('');
  const [fieldErrors, setFieldErrors] = useState<{
    value?: string;
    justification?: string;
  }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setMode('ABSOLUTE');
    setValue('');
    setJustification('');
    setFieldErrors({});
    setGeneralError(null);
  }, [isOpen, ingredient]);

  const currentStock = ingredient ? ingredient.currentStock : 0;
  const unit = ingredient ? ingredient.unitOfMeasure : '';

  // Real-time calculations
  const { computedDelta, resultingStock, isNumeric } = useMemo(() => {
    const num = parseFloat(value);
    if (isNaN(num)) {
      return { computedDelta: 0, resultingStock: currentStock, isNumeric: false };
    }

    if (mode === 'ABSOLUTE') {
      return {
        computedDelta: num - currentStock,
        resultingStock: num,
        isNumeric: true,
      };
    } else {
      return {
        computedDelta: num,
        resultingStock: currentStock + num,
        isNumeric: true,
      };
    }
  }, [mode, value, currentStock]);

  if (!isOpen || !ingredient) return null;

  const validate = (): boolean => {
    const errors: { value?: string; justification?: string } = {};

    const num = parseFloat(value);
    if (!value || isNaN(num)) {
      errors.value = 'Debés ingresar un valor numérico';
    } else {
      // Discrete unit check (u)
      if (ingredient.unitOfMeasure === 'u' && !Number.isInteger(num)) {
        errors.value = "Las unidades discretas ('u') deben ser números enteros.";
      }

      // Non-negative resulting stock invariant (FR-016)
      if (resultingStock < 0) {
        errors.value = 'El stock resultante no puede ser negativo.';
      }

      // Zero delta rejection (FR-017)
      if (computedDelta === 0) {
        errors.value = 'El ajuste no produce variación en el stock (delta igual a cero).';
      }
    }

    if (!justification.trim()) {
      errors.justification = 'La justificación es obligatoria';
    } else if (justification.trim().length < 5) {
      errors.justification = 'La justificación debe contener al menos 5 caracteres';
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
      await onAdjust(ingredient.id, {
        mode,
        value: parseFloat(value),
        justification: justification.trim(),
      });
      onClose();
    } catch (err: unknown) {
      const error = err as {
        response?: { data?: { detail?: string; message?: string } };
      };
      const detail = error.response?.data?.detail || error.response?.data?.message;
      if (detail) {
        setGeneralError(detail);
      } else {
        setGeneralError('Error al registrar el ajuste de stock. Intente nuevamente.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="stock-adjustment-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 space-y-5 border border-gray-100">
        <div className="flex items-center justify-between border-b pb-3">
          <div>
            <h3 id="stock-adjustment-title" className="text-lg font-bold text-gray-900">
              Ajuste Manual de Stock
            </h3>
            <p className="text-xs text-gray-500">
              <span className="font-semibold text-gray-700">{ingredient.name}</span>
              {' — Categoría: '}
              {ingredient.category}
            </p>
          </div>
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

        {/* Live Calculation Preview Box */}
        <div className="bg-gray-50 rounded-lg p-3.5 border border-gray-200 grid grid-cols-3 gap-2 text-center">
          <div>
            <p className="text-xs text-gray-500">Stock Actual</p>
            <p className="text-sm font-bold text-gray-800 font-mono">
              {currentStock} {unit}
            </p>
          </div>
          <div>
            <p className="text-xs text-gray-500">Variación Neta</p>
            <p
              className={`text-sm font-bold font-mono ${
                !isNumeric || computedDelta === 0
                  ? 'text-gray-400'
                  : computedDelta > 0
                  ? 'text-green-600'
                  : 'text-red-600'
              }`}
            >
              {isNumeric && computedDelta > 0 ? `+${computedDelta}` : computedDelta} {unit}
            </p>
          </div>
          <div>
            <p className="text-xs text-gray-500">Nuevo Stock</p>
            <p
              className={`text-sm font-bold font-mono ${
                resultingStock < 0 ? 'text-red-600 font-extrabold' : 'text-blue-600'
              }`}
            >
              {resultingStock} {unit}
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="adjustment-mode" className="block text-sm font-medium text-gray-700 mb-1">
              Modo de ajuste
            </label>
            <select
              id="adjustment-mode"
              value={mode}
              onChange={(e) => {
                setMode(e.target.value as StockAdjustmentMode);
                if (fieldErrors.value) setFieldErrors((prev) => ({ ...prev, value: undefined }));
              }}
              disabled={submitting || isLoading}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white text-sm"
            >
              <option value="ABSOLUTE">Ajuste Absoluto (Fijar saldo final exacto)</option>
              <option value="RELATIVE">Ajuste Relativo (Sumar o restar delta +/-)</option>
            </select>
          </div>

          <div>
            <label htmlFor="adjustment-value" className="block text-sm font-medium text-gray-700 mb-1">
              {mode === 'ABSOLUTE' ? 'Valor final de stock' : 'Valor del delta (+ o -)'}
            </label>
            <input
              id="adjustment-value"
              type="number"
              step={ingredient.unitOfMeasure === 'u' ? '1' : '0.0001'}
              value={value}
              onChange={(e) => {
                setValue(e.target.value);
                if (fieldErrors.value) setFieldErrors((prev) => ({ ...prev, value: undefined }));
              }}
              placeholder={mode === 'ABSOLUTE' ? 'Ej. 50' : 'Ej. 10 ó -5'}
              disabled={submitting || isLoading}
              className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors ${
                fieldErrors.value
                  ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                  : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'
              }`}
            />
            {fieldErrors.value && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.value}</p>
            )}
          </div>

          <div>
            <label htmlFor="adjustment-justification" className="block text-sm font-medium text-gray-700 mb-1">
              Justificación obligatoria
            </label>
            <textarea
              id="adjustment-justification"
              rows={2}
              value={justification}
              onChange={(e) => {
                setJustification(e.target.value);
                if (fieldErrors.justification) {
                  setFieldErrors((prev) => ({ ...prev, justification: undefined }));
                }
              }}
              placeholder="Ej. Recuento físico mensual, recepción de mercadería, merma por rotura..."
              disabled={submitting || isLoading}
              className={`w-full px-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 transition-colors text-sm ${
                fieldErrors.justification
                  ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                  : 'border-gray-300 focus:ring-blue-500 focus:border-blue-500'
              }`}
            />
            {fieldErrors.justification && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.justification}</p>
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
              {submitting || isLoading ? 'Registrando...' : 'Confirmar Ajuste'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default StockAdjustmentModal;
