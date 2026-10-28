import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { dishApi } from '@/api/dishApi';
import { BasicIdentityStep } from '@/components/dish/BasicIdentityStep';
import { DishStatusBadge } from '@/components/dish/DishStatusBadge';
import type { DishDetailResponse, CreateDishRequest } from '@/types/dish';

export const ChefDishEditorPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [dish, setDish] = useState<DishDetailResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(!!id);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const fetchDish = useCallback(async (dishId: string) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await dishApi.getDishDetail(dishId);
      setDish(data);
    } catch (err: unknown) {
      setErrorMessage('No se pudo cargar la información del plato.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (id) {
      fetchDish(id);
    } else {
      setDish(null);
      setIsLoading(false);
    }
  }, [id, fetchDish]);

  const handleSave = async (data: CreateDishRequest, photoFile?: File) => {
    setIsSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      if (!id) {
        // Create draft
        const created = await dishApi.createDishDraft(data);
        if (photoFile) {
          await dishApi.uploadPhoto(created.id, photoFile);
        }
        setSuccessMessage('Borrador creado correctamente.');
        navigate(`/cocina/platos/${created.id}`);
      } else {
        // Update draft
        const updated = await dishApi.updateDish(id, data);
        if (photoFile) {
          await dishApi.uploadPhoto(id, photoFile);
        }
        setDish(updated);
        setSuccessMessage('Borrador actualizado correctamente.');
        await fetchDish(id);
      }
    } catch (err: any) {
      const message =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Error al guardar la información del plato.';
      setErrorMessage(message);
    } finally {
      setIsSaving(false);
    }
  };

  const handlePhotoDelete = async () => {
    if (!id) return;
    try {
      await dishApi.deletePhoto(id);
      setSuccessMessage('Foto eliminada correctamente.');
      await fetchDish(id);
    } catch (err: unknown) {
      setErrorMessage('Error al eliminar la foto.');
    }
  };

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-12 max-w-4xl flex justify-center text-zinc-500">
        Cargando ficha técnica...
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      {/* Navigation & Header */}
      <div className="mb-6">
        <Link
          to="/cocina/platos"
          className="text-xs font-semibold text-amber-600 dark:text-amber-400 hover:underline mb-2 inline-block"
        >
          ← Volver a Platos
        </Link>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">
              {id ? `Ficha Técnica: ${dish?.name || 'Cargando...'}` : 'Nueva Ficha Técnica'}
            </h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1">
              Paso 1: Identidad básica del plato y fotografía.
            </p>
          </div>
          {dish && (
            <div>
              <DishStatusBadge status={dish.status} />
            </div>
          )}
        </div>
      </div>

      {/* Notifications */}
      {errorMessage && (
        <div className="p-4 mb-6 rounded-lg bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 text-sm text-red-700 dark:text-red-400">
          {errorMessage}
        </div>
      )}
      {successMessage && (
        <div className="p-4 mb-6 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-900 text-sm text-emerald-700 dark:text-emerald-400">
          {successMessage}
        </div>
      )}

      {/* Basic Identity Form Component */}
      <BasicIdentityStep
        initialData={
          dish
            ? {
                name: dish.name,
                category: dish.category,
                description: dish.description,
                photoUrl: dish.photoUrl,
              }
            : undefined
        }
        onSave={handleSave}
        onPhotoDelete={handlePhotoDelete}
        isReadOnly={dish ? dish.status === 'PENDING_PRICE' : false}
        isSaving={isSaving}
      />
    </div>
  );
};

export default ChefDishEditorPage;
