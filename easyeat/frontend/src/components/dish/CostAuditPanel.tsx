import React from 'react';
import { formatAuditQuantity } from '@/utils/foodCostAudit';
import { FoodCostAuditResponse } from '@/types/dish';

interface CostAuditPanelProps {
  auditData: FoodCostAuditResponse;
  onRefresh?: () => void;
}

export const CostAuditPanel: React.FC<CostAuditPanelProps> = ({ auditData, onRefresh }) => {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
      <div className="flex justify-between items-center border-b border-gray-100 pb-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Auditoría Financiera de Costos (Food Cost)</h2>
          <p className="text-sm text-gray-500">Plato: <span className="font-semibold text-gray-700">{auditData.dishName}</span></p>
        </div>
        {onRefresh && (
          <button
            onClick={onRefresh}
            className="px-3 py-1.5 text-sm bg-gray-100 hover:bg-gray-200 text-gray-700 rounded transition-colors"
          >
            Actualizar Costos
          </button>
        )}
      </div>

      {auditData.hasUnpricedIngredients && (
        <div className="bg-amber-50 border-l-4 border-amber-500 p-4 rounded text-amber-800 space-y-1" role="alert">
          <p className="font-semibold">Advertencia de Insumos sin Precio</p>
          <p className="text-sm">Uno o más insumos no tienen costo de adquisición cargado en inventario. El costo calculado es parcial.</p>
          {auditData.warningNotes.length > 0 && (
            <ul className="list-disc pl-5 text-sm mt-1">
              {auditData.warningNotes.map((note, idx) => (
                <li key={idx}>{note}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div>
        <h3 className="text-sm font-semibold text-gray-700 mb-2">Desglose de Receta Técnica e Insumos</h3>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Insumo</th>
                <th className="px-4 py-2 text-right font-medium text-gray-500">Cantidad</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Unidad</th>
                <th className="px-4 py-2 text-right font-medium text-gray-500">Costo Unit. Compra</th>
                <th className="px-4 py-2 text-right font-medium text-gray-500">Subtotal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {auditData.items.map((item) => (
                <tr key={item.ingredientId} className={item.unpricedWarning ? 'bg-amber-50/50' : ''}>
                  <td className="px-4 py-2 font-medium text-gray-900">
                    {item.ingredientName}
                    {item.unpricedWarning && (
                      <span className="ml-2 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800">
                        Sin costo
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-2 text-right text-gray-700">
                    {formatAuditQuantity(item)}
                  </td>
                  <td className="px-4 py-2 text-gray-700">{item.unitOfMeasure}</td>
                  <td className="px-4 py-2 text-right text-gray-700">
                    ${item.unitPurchaseCost.toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-right font-semibold text-gray-900">
                    ${item.lineSubtotal.toFixed(2)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-gray-50 font-bold">
              <tr>
                <td colSpan={4} className="px-4 py-3 text-right text-gray-900">
                  Costo Total de Materia Prima (Food Cost):
                </td>
                <td className="px-4 py-3 text-right text-lg text-emerald-600">
                  ${auditData.totalFoodCost.toFixed(2)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      {auditData.requiredDietaryRestrictions && auditData.requiredDietaryRestrictions.length > 0 && (
        <div className="border-t border-gray-100 pt-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">Etiquetas y Restricciones Dietarias Declaradas</h3>
          <div className="flex flex-wrap gap-2">
            {auditData.requiredDietaryRestrictions.map((code) => (
              <span
                key={code}
                className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800"
              >
                {code}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default CostAuditPanel;

