import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { IngredientCatalogTable } from '../components/inventory/IngredientCatalogTable';
import { IngredientFormModal } from '../components/inventory/IngredientFormModal';
import { inventoryApi } from '../api/inventoryApi';
import {
  AdminIngredientDetailResponse,
  CreateIngredientRequest,
  IngredientStatus,
  UpdateIngredientRequest,
} from '../types/inventory';

export const InventoryManagementPage: React.FC = () => {
  const [ingredients, setIngredients] = useState<AdminIngredientDetailResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Form modal state
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [selectedIngredient, setSelectedIngredient] = useState<AdminIngredientDetailResponse | null>(null);

  const fetchIngredients = useCallback(async () => {
    setIsLoading(true);
    setGeneralError(null);
    try {
      const data = await inventoryApi.listIngredients();
      setIngredients(data);
    } catch {
      setGeneralError('No se pudo cargar el catálogo de ingredientes. Intente nuevamente.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchIngredients();
  }, [fetchIngredients]);

  const handleOpenCreate = () => {
    setSelectedIngredient(null);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (ingredient: AdminIngredientDetailResponse) => {
    setSelectedIngredient(ingredient);
    setIsFormOpen(true);
  };

  const handleFormSubmit = async (data: CreateIngredientRequest | UpdateIngredientRequest) => {
    if (selectedIngredient) {
      await inventoryApi.updateIngredient(selectedIngredient.id, data as UpdateIngredientRequest);
      await fetchIngredients();
    } else {
      await inventoryApi.createIngredient(data as CreateIngredientRequest);
      await fetchIngredients();
    }
  };

  const handleStatusChange = async (id: string, newStatus: IngredientStatus) => {
    setGeneralError(null);
    try {
      await inventoryApi.updateStatus(id, newStatus);
      await fetchIngredients();
    } catch (err: unknown) {
      const error = err as {
        response?: { data?: { detail?: string; message?: string } };
      };
      setGeneralError(
        error.response?.data?.detail ||
          error.response?.data?.message ||
          'Error al actualizar el estado del ingrediente.'
      );
    }
  };

  return (
    <WorkspaceLayout
      title="Inventario y Materias Primas"
      subtitle="Gestión del catálogo maestro de ingredientes, costos y existencias"
    >
      <div className="space-y-6">
        {/* Navigation & Action Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <Link
              to="/admin"
              className="inline-flex items-center text-xs font-semibold text-blue-600 hover:text-blue-800 mb-2"
            >
              ← Volver al Panel de Administración
            </Link>
            <h2 className="text-2xl font-bold text-gray-900 tracking-tight">
              Catálogo de Materias Primas
            </h2>
          </div>

          <button
            type="button"
            onClick={handleOpenCreate}
            className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            + Nuevo Ingrediente
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

        {/* Catalog Table */}
        <IngredientCatalogTable
          ingredients={ingredients}
          onEdit={handleOpenEdit}
          onStatusChange={handleStatusChange}
          isLoading={isLoading}
        />

        {/* Ingredient Form Modal */}
        <IngredientFormModal
          isOpen={isFormOpen}
          onClose={() => setIsFormOpen(false)}
          onSubmit={handleFormSubmit}
          initialData={selectedIngredient}
        />
      </div>
    </WorkspaceLayout>
  );
};

export default InventoryManagementPage;
