import React, { useState, useEffect, useMemo } from 'react';
import {
  AdminIngredientDetailResponse,
  IngredientBatch,
  StockAdjustmentAction,
  StockAdjustmentRequest,
} from '../../types/inventory';
import { inventoryApi } from '../../api/inventoryApi';

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
  const isDiscontinued = ingredient?.status === 'DISCONTINUED';

  const [activeTab, setActiveTab] = useState<StockAdjustmentAction>(
    isDiscontinued ? 'SUBTRACT' : 'ADD'
  );

  // ADD form state
  const [addQuantity, setAddQuantity] = useState<string>('');
  const [addUnitCost, setAddUnitCost] = useState<string>('');
  const [addJustification, setAddJustification] = useState<string>('');

  // SUBTRACT form state
  const [batches, setBatches] = useState<IngredientBatch[]>([]);
  const [isLoadingBatches, setIsLoadingBatches] = useState<boolean>(false);
  const [selectedBatchId, setSelectedBatchId] = useState<string>('');
  const [subtractQuantity, setSubtractQuantity] = useState<string>('');
  const [subtractJustification, setSubtractJustification] = useState<string>('');

  // Errors & submission
  const [fieldErrors, setFieldErrors] = useState<{
    quantity?: string;
    unitCost?: string;
    batchId?: string;
    justification?: string;
  }>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Initialize or reset form when modal opens or ingredient changes
  useEffect(() => {
    if (isOpen && ingredient) {
      const defaultTab = ingredient.status === 'DISCONTINUED' ? 'SUBTRACT' : 'ADD';
      setActiveTab(defaultTab);
      setAddQuantity('');
      setAddUnitCost(ingredient.unitPurchaseCost?.toString() ?? '');
      setAddJustification('');
      setSelectedBatchId('');
      setSubtractQuantity('');
      setSubtractJustification('');
      setFieldErrors({});
      setGeneralError(null);

      // Load active batches for subtraction
      loadBatches(ingredient.id);
    }
  }, [isOpen, ingredient]);

  const loadBatches = async (ingredientId: string) => {
    setIsLoadingBatches(true);
    try {
      const data = await inventoryApi.getBatches(ingredientId);
      setBatches(data);
    } catch {
      setGeneralError('Error al cargar los lotes disponibles para este ingrediente.');
    } finally {
      setIsLoadingBatches(false);
    }
  };

  const selectedBatch = useMemo(() => {
    return batches.find((b) => b.id === selectedBatchId) || null;
  }, [batches, selectedBatchId]);

  const currentStock = ingredient ? ingredient.currentStock : 0;
  const unit = ingredient ? ingredient.unitOfMeasure : '';

  // ADD live calculations
  const addCalculations = useMemo(() => {
    const qty = parseFloat(addQuantity);
    const cost = parseFloat(addUnitCost);
    const isValidQty = !isNaN(qty) && qty > 0;
    const isValidCost = !isNaN(cost) && cost >= 0;

    return {
      resultingStock: isValidQty ? currentStock + qty : currentStock,
      totalCost: isValidQty && isValidCost ? qty * cost : null,
    };
  }, [addQuantity, addUnitCost, currentStock]);

  // SUBTRACT live calculations
  const subtractCalculations = useMemo(() => {
    const qty = parseFloat(subtractQuantity);
    const isValidQty = !isNaN(qty) && qty > 0;
    const unitCost = selectedBatch ? selectedBatch.unitCost : 0;
    const batchRemaining = selectedBatch ? selectedBatch.remainingQuantity : 0;

    return {
      financialLoss: isValidQty && selectedBatch ? qty * unitCost : null,
      batchRemainingAfter: isValidQty && selectedBatch ? batchRemaining - qty : batchRemaining,
      globalResultingStock: isValidQty ? currentStock - qty : currentStock,
    };
  }, [subtractQuantity, selectedBatch, currentStock]);

  if (!isOpen || !ingredient) return null;

  const validateAddForm = (): boolean => {
    const errors: { quantity?: string; unitCost?: string; justification?: string } = {};

    const qty = parseFloat(addQuantity);
    if (!addQuantity || isNaN(qty) || qty <= 0) {
      errors.quantity = 'Debés ingresar una cantidad mayor a cero';
    } else if (ingredient.unitOfMeasure === 'u' && !Number.isInteger(qty)) {
      errors.quantity = "Las unidades discretas ('u') deben ser números enteros.";
    }

    if (!addUnitCost.trim()) {
      errors.unitCost = 'El costo unitario de compra es obligatorio.';
    } else {
      const cost = parseFloat(addUnitCost);
      if (isNaN(cost) || cost < 0) {
        errors.unitCost = 'El costo unitario de compra es obligatorio y debe ser mayor o igual a cero.';
      }
    }

    if (!addJustification.trim()) {
      errors.justification = 'La justificación es obligatoria';
    } else if (addJustification.trim().length < 5) {
      errors.justification = 'La justificación debe contener al menos 5 caracteres';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const validateSubtractForm = (): boolean => {
    const errors: { quantity?: string; batchId?: string; justification?: string } = {};

    if (!selectedBatchId) {
      errors.batchId = 'Debés seleccionar un lote de compra para imputar la merma.';
    }

    const qty = parseFloat(subtractQuantity);
    if (!subtractQuantity || isNaN(qty) || qty <= 0) {
      errors.quantity = 'Debés ingresar una cantidad mayor a cero';
    } else if (ingredient.unitOfMeasure === 'u' && !Number.isInteger(qty)) {
      errors.quantity = "Las unidades discretas ('u') deben ser números enteros.";
    } else if (selectedBatch && qty > selectedBatch.remainingQuantity) {
      errors.quantity = 'La cantidad a descontar supera el saldo disponible del lote.';
    }

    if (!subtractJustification.trim()) {
      errors.justification = 'La justificación es obligatoria';
    } else if (subtractJustification.trim().length < 5) {
      errors.justification = 'La justificación debe contener al menos 5 caracteres';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setGeneralError(null);

    if (activeTab === 'ADD') {
      if (!validateAddForm()) return;

      setSubmitting(true);
      try {
        await onAdjust(ingredient.id, {
          action: 'ADD',
          quantity: parseFloat(addQuantity),
          unitCost: parseFloat(addUnitCost),
          justification: addJustification.trim(),
        });
        onClose();
      } catch (err: unknown) {
        const error = err as { response?: { data?: { detail?: string } } };
        setGeneralError(error.response?.data?.detail || 'Error al procesar el ingreso de stock.');
      } finally {
        setSubmitting(false);
      }
    } else {
      if (!validateSubtractForm()) return;

      setSubmitting(true);
      try {
        await onAdjust(ingredient.id, {
          action: 'SUBTRACT',
          quantity: parseFloat(subtractQuantity),
          batchId: selectedBatchId,
          justification: subtractJustification.trim(),
        });
        onClose();
      } catch (err: unknown) {
        const error = err as { response?: { data?: { detail?: string } } };
        setGeneralError(error.response?.data?.detail || 'Error al procesar la merma de stock.');
      } finally {
        setSubmitting(false);
      }
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      className="fixed inset-0 z-50 overflow-y-auto bg-black bg-opacity-50 flex items-center justify-center p-4"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full overflow-hidden border border-gray-200 animate-fade-in">
        {/* Header */}
        <div className="px-6 py-4 bg-gray-50 border-b border-gray-200 flex justify-between items-center">
          <div>
            <h3 id="modal-title" className="text-lg font-bold text-gray-900">
              Ajuste de Stock por Lotes
            </h3>
            <p className="text-sm text-gray-500">
              <span className="font-semibold text-gray-800">{ingredient.name}</span> — Stock global actual:{' '}
              <span className="font-mono font-bold text-blue-700">
                {currentStock} {unit}
              </span>
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 focus:outline-none text-xl font-bold"
            aria-label="Cerrar modal"
          >
            &times;
          </button>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200 bg-gray-50 px-6 flex space-x-4">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'ADD'}
            disabled={isDiscontinued}
            onClick={() => {
              setActiveTab('ADD');
              setFieldErrors({});
            }}
            title={
              isDiscontinued
                ? 'Los ingredientes desactivados no admiten nuevos ingresos de stock'
                : 'Registrar nuevo lote de compra'
            }
            className={`py-3 px-2 border-b-2 font-medium text-sm transition-colors ${
              activeTab === 'ADD'
                ? 'border-blue-600 text-blue-600'
                : isDiscontinued
                ? 'border-transparent text-gray-300 cursor-not-allowed'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Agregar stock
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'SUBTRACT'}
            onClick={() => {
              setActiveTab('SUBTRACT');
              setFieldErrors({});
            }}
            className={`py-3 px-2 border-b-2 font-medium text-sm transition-colors ${
              activeTab === 'SUBTRACT'
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            Restar stock (merma)
          </button>
        </div>

        {/* Content & Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {isDiscontinued && (
            <div className="p-3 text-xs rounded-md bg-orange-50 border border-orange-200 text-orange-800">
              Materia prima desactivada: solo se admiten mermas o bajas sobre lotes existentes hasta liquidar el stock disponible.
            </div>
          )}

          {generalError && (
            <div
              role="alert"
              className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700"
            >
              {generalError}
            </div>
          )}

          {/* TAB 1: AGREGAR STOCK */}
          {activeTab === 'ADD' && (
            <div className="space-y-4">
              <div>
                <label htmlFor="addQuantityInput" className="block text-sm font-medium text-gray-700">
                  Cantidad a ingresar ({unit}) *
                </label>
                <input
                  id="addQuantityInput"
                  type="number"
                  step="any"
                  min="0"
                  value={addQuantity}
                  onChange={(e) => setAddQuantity(e.target.value)}
                  placeholder={`Ej: 10 ${unit}`}
                  className={`mt-1 block w-full px-3 py-2 border rounded-md shadow-sm text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white ${
                    fieldErrors.quantity ? 'border-red-500 ring-1 ring-red-500' : 'border-gray-300'
                  }`}
                />
                {fieldErrors.quantity && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.quantity}</p>
                )}
              </div>

              <div>
                <label htmlFor="unitCostInput" className="block text-sm font-medium text-gray-700">
                  Precio unitario de compra ($ / {unit}) *
                </label>
                <input
                  id="unitCostInput"
                  type="number"
                  step="any"
                  min="0"
                  value={addUnitCost}
                  onChange={(e) => setAddUnitCost(e.target.value)}
                  placeholder="Ej: 150.00"
                  className={`mt-1 block w-full px-3 py-2 border rounded-md shadow-sm text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white ${
                    fieldErrors.unitCost ? 'border-red-500 ring-1 ring-red-500' : 'border-gray-300'
                  }`}
                />
                {fieldErrors.unitCost && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.unitCost}</p>
                )}
                <p className="mt-1 text-xs text-gray-500">
                  Cada ingreso genera un lote independiente conservando este precio para costeo y mermas.
                </p>
              </div>

              {/* Live ADD calculations card */}
              <div className="bg-gray-50 p-3 rounded-md border border-gray-200 text-xs space-y-1">
                <div className="flex justify-between text-gray-600">
                  <span>Stock global resultante:</span>
                  <span className="font-semibold text-gray-900">
                    {addCalculations.resultingStock} {unit}
                  </span>
                </div>
                {addCalculations.totalCost !== null && (
                  <div className="flex justify-between text-gray-600">
                    <span>Inversión total en lote:</span>
                    <span className="font-semibold text-blue-700 font-mono">
                      ${addCalculations.totalCost.toFixed(2)}
                    </span>
                  </div>
                )}
              </div>

              <div>
                <label htmlFor="addJustificationInput" className="block text-sm font-medium text-gray-700">
                  Justificación *
                </label>
                <textarea
                  id="addJustificationInput"
                  rows={3}
                  value={addJustification}
                  onChange={(e) => setAddJustification(e.target.value)}
                  placeholder="Detallar remito, factura o motivo del ingreso..."
                  className={`mt-1 block w-full px-3 py-2 border rounded-md shadow-sm text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white ${
                    fieldErrors.justification ? 'border-red-500 ring-1 ring-red-500' : 'border-gray-300'
                  }`}
                />
                {fieldErrors.justification && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.justification}</p>
                )}
              </div>
            </div>
          )}

          {/* TAB 2: RESTAR STOCK (MERMA) */}
          {activeTab === 'SUBTRACT' && (
            <div className="space-y-4">
              <div>
                <label htmlFor="batchSelect" className="block text-sm font-medium text-gray-700">
                  Lote a descontar *
                </label>
                {isLoadingBatches ? (
                  <p className="text-xs text-gray-500 mt-1">Cargando lotes disponibles...</p>
                ) : batches.length === 0 ? (
                  <p className="text-xs text-red-600 mt-1 font-medium">
                    No hay lotes con saldo disponible para registrar mermas o pérdidas.
                  </p>
                ) : (
                  <select
                    id="batchSelect"
                    value={selectedBatchId}
                    onChange={(e) => setSelectedBatchId(e.target.value)}
                    className={`mt-1 block w-full px-3 py-2 border rounded-md shadow-sm text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                      fieldErrors.batchId ? 'border-red-500 ring-1 ring-red-500' : 'border-gray-300'
                    }`}
                  >
                    <option value="">-- Seleccionar lote de compra --</option>
                    {batches.map((batch) => {
                      const dateStr = new Date(batch.createdAt).toLocaleDateString('es-AR');
                      return (
                        <option key={batch.id} value={batch.id}>
                          Lote {batch.id.slice(0, 8)}... — {batch.remainingQuantity} {unit} disp. (${batch.unitCost.toFixed(2)}/{unit}) — {dateStr}
                        </option>
                      );
                    })}
                  </select>
                )}
                {fieldErrors.batchId && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.batchId}</p>
                )}
              </div>

              <div>
                <label htmlFor="subtractQuantityInput" className="block text-sm font-medium text-gray-700">
                  Cantidad a descontar ({unit}) *
                </label>
                <input
                  id="subtractQuantityInput"
                  type="number"
                  step="any"
                  min="0"
                  value={subtractQuantity}
                  onChange={(e) => setSubtractQuantity(e.target.value)}
                  placeholder={`Ej: 2 ${unit}`}
                  className={`mt-1 block w-full px-3 py-2 border rounded-md shadow-sm text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white ${
                    fieldErrors.quantity ? 'border-red-500 ring-1 ring-red-500' : 'border-gray-300'
                  }`}
                />
                {fieldErrors.quantity && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.quantity}</p>
                )}
              </div>

              {/* Live SUBTRACT calculations card */}
              <div className="bg-gray-50 p-3 rounded-md border border-gray-200 text-xs space-y-1">
                <div className="flex justify-between text-gray-600">
                  <span>Pérdida económica imputada:</span>
                  <span className="font-semibold text-red-600 font-mono text-sm">
                    {subtractCalculations.financialLoss !== null
                      ? `$${subtractCalculations.financialLoss.toFixed(2)}`
                      : '$0.00'}
                  </span>
                </div>
                {selectedBatch && (
                  <div className="flex justify-between text-gray-600">
                    <span>Saldo remanente en lote:</span>
                    <span className="font-semibold text-gray-800">
                      {subtractCalculations.batchRemainingAfter} {unit}
                    </span>
                  </div>
                )}
                <div className="flex justify-between text-gray-600">
                  <span>Stock global resultante:</span>
                  <span className="font-semibold text-gray-800">
                    {subtractCalculations.globalResultingStock} {unit}
                  </span>
                </div>
              </div>

              <div>
                <label htmlFor="subtractJustificationInput" className="block text-sm font-medium text-gray-700">
                  Justificación *
                </label>
                <textarea
                  id="subtractJustificationInput"
                  rows={3}
                  value={subtractJustification}
                  onChange={(e) => setSubtractJustification(e.target.value)}
                  placeholder="Detallar causa de la merma, vencimiento o desperdicio..."
                  className={`mt-1 block w-full px-3 py-2 border rounded-md shadow-sm text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white ${
                    fieldErrors.justification ? 'border-red-500 ring-1 ring-red-500' : 'border-gray-300'
                  }`}
                />
                {fieldErrors.justification && (
                  <p className="mt-1 text-xs text-red-600">{fieldErrors.justification}</p>
                )}
              </div>
            </div>
          )}

          {/* Footer buttons */}
          <div className="mt-6 flex justify-end space-x-3 pt-4 border-t border-gray-200">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting || isLoading}
              className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting || isLoading}
              className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
            >
              {submitting
                ? 'Procesando...'
                : activeTab === 'ADD'
                ? 'Confirmar ingreso'
                : 'Confirmar merma'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default StockAdjustmentModal;
