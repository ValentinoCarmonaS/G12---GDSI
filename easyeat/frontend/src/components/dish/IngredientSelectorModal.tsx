import React, { useState, useEffect } from 'react';
import { inventoryApi } from '@/api/inventoryApi';
import type {
  ChefIngredientItemResponse,
  ChefIngredientPageResponse,
  IngredientCategory,
} from '@/types/inventory';
import { BrutalistModal } from '@/components/chef/BrutalistModal';
import { BrutalistCard } from '@/components/chef/BrutalistCard';

export interface IngredientSelectorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectIngredient: (ingredient: ChefIngredientItemResponse) => void;
  selectedIngredientIds?: string[];
}

const CATEGORIES: { label: string; value: string }[] = [
  { label: 'Todas las categorías', value: '' },
  { label: 'Produce', value: 'Produce' },
  { label: 'Meat', value: 'Meat' },
  { label: 'Dairy', value: 'Dairy' },
  { label: 'Dry Goods', value: 'Dry Goods' },
  { label: 'Spices', value: 'Spices' },
];

export const IngredientSelectorModal: React.FC<IngredientSelectorModalProps> = ({
  isOpen,
  onClose,
  onSelectIngredient,
  selectedIngredientIds = [],
}) => {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [catalog, setCatalog] = useState<ChefIngredientPageResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    setLoading(true);
    setError(null);

    inventoryApi
      .getChefCatalog({
        search: search || undefined,
        category: (category || undefined) as IngredientCategory | undefined,
        page,
        size: 10,
      })
      .then((res) => {
        if (isMounted) setCatalog(res);
      })
      .catch(() => {
        if (isMounted) setError('No se pudo cargar el catálogo de ingredientes.');
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, search, category, page]);

  return (
    <BrutalistModal
      isOpen={isOpen}
      onClose={onClose}
      title="Seleccionar ingrediente"
    >
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="ingredient-search" className="sr-only">
              Buscar ingrediente
            </label>
            <input
              id="ingredient-search"
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Buscar ingrediente..."
              className="w-full px-3 py-2 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-sm font-medium focus:outline-none focus:ring-2 focus:ring-[#0A0A0A]"
            />
          </div>
          <div>
            <label htmlFor="ingredient-category-filter" className="sr-only">
              Filtrar por categoría
            </label>
            <select
              id="ingredient-category-filter"
              aria-label="Filtrar por categoría"
              value={category}
              onChange={(e) => {
                setCategory(e.target.value);
                setPage(0);
              }}
              className="w-full px-3 py-2 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-sm font-medium focus:outline-none focus:ring-2 focus:ring-[#0A0A0A]"
            >
              {CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>
                  {cat.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {loading && (
          <p className="text-sm font-mono text-[#52525B] py-4 text-center">
            Cargando ingredientes...
          </p>
        )}

        {error && (
          <p role="alert" className="text-sm font-bold text-[#DC2626] py-2">
            {error}
          </p>
        )}

        {!loading && !error && catalog && (() => {
          const items = (catalog as { items?: ChefIngredientItemResponse[]; content?: ChefIngredientItemResponse[] }).items ?? (catalog as { content?: ChefIngredientItemResponse[] }).content ?? [];
          return (
            <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
              {items.length === 0 ? (
                <p className="text-sm font-mono text-[#52525B] py-6 text-center">
                  No se encontraron ingredientes.
                </p>
              ) : (
                items.map((ing) => {
                  const alreadySelected = selectedIngredientIds.includes(ing.id);
                  return (
                    <BrutalistCard
                      key={ing.id}
                      className="p-3 flex items-center justify-between gap-3"
                    >
                      <div className="min-w-0">
                        <p className="font-bold text-sm text-[#0A0A0A] truncate">
                          {ing.name}
                        </p>
                        <p className="font-mono text-xs text-[#52525B]">
                          {ing.category} · Unidad: {ing.unitOfMeasure}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={alreadySelected}
                        onClick={() => onSelectIngredient(ing)}
                        className="min-h-[40px] px-3 py-1.5 border-2 border-[#0A0A0A] bg-[#0A0A0A] text-[#FFFFFF] text-xs font-bold uppercase tracking-wide hover:bg-[#27272A] disabled:bg-[#F5F5F4] disabled:text-[#52525B] disabled:cursor-not-allowed shrink-0"
                      >
                        {alreadySelected ? 'Ya en la receta' : 'Seleccionar'}
                      </button>
                    </BrutalistCard>
                  );
                })
              )}
            </div>
          );
        })()}

        {catalog && catalog.totalPages > 1 && (
          <div className="flex items-center justify-between pt-3 border-t-2 border-[#0A0A0A]">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="px-3 py-1.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-xs font-bold uppercase disabled:opacity-40"
            >
              Anterior
            </button>
            <span className="font-mono text-xs text-[#0A0A0A]">
              Página {page + 1} de {catalog.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= catalog.totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1.5 border-2 border-[#0A0A0A] bg-[#FFFFFF] text-[#0A0A0A] text-xs font-bold uppercase disabled:opacity-40"
            >
              Siguiente
            </button>
          </div>
        )}
      </div>
    </BrutalistModal>
  );
};

export default IngredientSelectorModal;
