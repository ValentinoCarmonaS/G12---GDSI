import React from 'react';
import { ChefIngredientItemResponse } from '../../types/inventory';

export interface ChefIngredientTableProps {
  items: ChefIngredientItemResponse[];
  isLoading?: boolean;
}

export const ChefIngredientTable: React.FC<ChefIngredientTableProps> = ({
  items,
  isLoading = false,
}) => {
  return (
    <div className="bg-white shadow rounded-lg border border-gray-200 overflow-hidden">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
          <thead className="bg-gray-50 text-gray-500 font-medium text-xs uppercase tracking-wider">
            <tr>
              <th scope="col" className="px-6 py-3">Materia Prima</th>
              <th scope="col" className="px-6 py-3">Categoría</th>
              <th scope="col" className="px-6 py-3">Existencia Actual</th>
              <th scope="col" className="px-6 py-3">Disponibilidad</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {isLoading ? (
              <tr>
                <td colSpan={4} className="px-6 py-8 text-center text-gray-500">
                  Cargando catálogo de cocina...
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-6 py-8 text-center text-gray-500">
                  No hay materias primas disponibles en el catálogo de cocina.
                </td>
              </tr>
            ) : (
              items.map((item) => {
                const isInStock = item.stockStatus === 'In Stock';

                return (
                  <tr key={item.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 font-semibold text-gray-900 whitespace-nowrap">
                      {item.name}
                    </td>
                    <td className="px-6 py-4 text-gray-600 whitespace-nowrap text-xs">
                      {item.category}
                    </td>
                    <td className="px-6 py-4 font-mono font-medium whitespace-nowrap">
                      <span className={isInStock ? 'text-gray-900' : 'text-red-500'}>
                        {item.currentStock} {item.unitOfMeasure}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                          isInStock
                            ? 'bg-green-100 text-green-800 border-green-200'
                            : 'bg-red-100 text-red-800 border-red-200'
                        }`}
                      >
                        {item.stockStatus}
                      </span>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default ChefIngredientTable;
