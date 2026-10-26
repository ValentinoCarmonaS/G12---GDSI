import React, { useState, useEffect } from 'react';
import {
  AdminIngredientDetailResponse,
  StockAuditEntryResponse,
} from '../../types/inventory';
import { inventoryApi } from '../../api/inventoryApi';

export interface StockAuditModalProps {
  isOpen: boolean;
  onClose: () => void;
  ingredient: AdminIngredientDetailResponse | null;
}

export const StockAuditModal: React.FC<StockAuditModalProps> = ({
  isOpen,
  onClose,
  ingredient,
}) => {
  const [auditEntries, setAuditEntries] = useState<StockAuditEntryResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen && ingredient) {
      setIsLoading(true);
      setError(null);
      inventoryApi
        .getAuditHistory(ingredient.id)
        .then((data) => {
          setAuditEntries(data);
        })
        .catch(() => {
          setError('Error al cargar la pista de auditoría.');
        })
        .finally(() => {
          setIsLoading(false);
        });
    } else {
      setAuditEntries([]);
      setError(null);
    }
  }, [isOpen, ingredient]);

  if (!isOpen || !ingredient) return null;

  const unit = ingredient.unitOfMeasure;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="stock-audit-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full p-6 space-y-5 border border-gray-100 max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b pb-3">
          <div>
            <h3 id="stock-audit-title" className="text-lg font-bold text-gray-900">
              Pista de Auditoría de Stock
            </h3>
            <p className="text-xs text-gray-500">
              <span className="font-semibold text-gray-700">{ingredient.name}</span>
              {' — Categoría: '}
              {ingredient.category}
              {' — Stock Actual: '}
              <span className="font-bold text-gray-800">
                {ingredient.currentStock} {unit}
              </span>
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

        {error && (
          <div
            role="alert"
            className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700"
          >
            {error}
          </div>
        )}

        {/* Audit Log Table Container */}
        <div className="flex-1 overflow-y-auto border border-gray-200 rounded-lg">
          <table className="min-w-full divide-y divide-gray-200 text-left text-xs">
            <thead className="bg-gray-50 text-gray-600 font-semibold sticky top-0">
              <tr>
                <th scope="col" className="px-4 py-3">Fecha y Hora</th>
                <th scope="col" className="px-4 py-3">Responsable</th>
                <th scope="col" className="px-4 py-3">Modo</th>
                <th scope="col" className="px-4 py-3">Stock Ant.</th>
                <th scope="col" className="px-4 py-3">Variación Neta</th>
                <th scope="col" className="px-4 py-3">Nuevo Stock</th>
                <th scope="col" className="px-4 py-3">Costo Unit.</th>
                <th scope="col" className="px-4 py-3">Pérdida ($)</th>
                <th scope="col" className="px-4 py-3">Justificación</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={9} className="px-4 py-8 text-center text-gray-500">
                    Cargando pista de auditoría...
                  </td>
                </tr>
              ) : auditEntries.length === 0 ? (
                <tr>
                  <td colSpan={9} className="px-4 py-8 text-center text-gray-500">
                    No se registran movimientos de stock para este ingrediente.
                  </td>
                </tr>
              ) : (
                auditEntries.map((entry) => {
                  const isPositive = entry.netDelta > 0;
                  const formattedDate = new Date(entry.createdAt).toLocaleString('es-AR', {
                    dateStyle: 'short',
                    timeStyle: 'medium',
                  });

                  return (
                    <tr key={entry.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-gray-500 whitespace-nowrap font-mono">
                        {formattedDate}
                      </td>
                      <td className="px-4 py-3 font-medium text-gray-800 whitespace-nowrap">
                        {entry.adminName || entry.userId}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span
                          className={`px-2 py-0.5 rounded text-[11px] font-semibold border ${
                            entry.adjustmentMode === 'ABSOLUTE'
                              ? 'bg-purple-50 text-purple-700 border-purple-200'
                              : entry.adjustmentMode === 'SUBTRACT'
                              ? 'bg-amber-50 text-amber-700 border-amber-200'
                              : 'bg-blue-50 text-blue-700 border-blue-200'
                          }`}
                        >
                          {entry.adjustmentMode}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-gray-600 whitespace-nowrap">
                        {entry.previousStock} {unit}
                      </td>
                      <td className="px-4 py-3 font-mono font-bold whitespace-nowrap">
                        <span className={isPositive ? 'text-green-600' : 'text-red-600'}>
                          {isPositive ? `+${entry.netDelta}` : entry.netDelta} {unit}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono font-bold text-gray-900 whitespace-nowrap">
                        {entry.newStock} {unit}
                      </td>
                      <td className="px-4 py-3 font-mono text-gray-600 whitespace-nowrap">
                        {entry.unitCost != null ? `$${entry.unitCost.toFixed(2)}` : '-'}
                      </td>
                      <td className="px-4 py-3 font-mono whitespace-nowrap">
                        {entry.financialLoss != null && entry.financialLoss > 0 ? (
                          <span className="text-red-600 font-bold">-${entry.financialLoss.toFixed(2)}</span>
                        ) : (
                          <span className="text-gray-400">-</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-gray-700">
                        {entry.justification}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between pt-3 border-t text-xs text-gray-500">
          <p>🔒 Registro inmutable append-only con fines de trazabilidad y auditoría fiscal.</p>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors"
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
};

export default StockAuditModal;

