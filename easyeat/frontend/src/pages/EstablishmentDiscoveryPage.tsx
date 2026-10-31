import React from 'react';
import { Link } from 'react-router-dom';
import { Store, Utensils, ShieldCheck, ArrowRight } from 'lucide-react';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { EstablishmentSearchBar } from '@/components/catalog/EstablishmentSearchBar';
import { useTenant } from '@/hooks/useTenant';

export const EstablishmentDiscoveryPage: React.FC = () => {
  const { tenant } = useTenant();

  return (
    <WorkspaceLayout
      title="Descubrimiento de Restaurantes"
      subtitle="Buscá y seleccioná un establecimiento para explorar su menú en vivo"
    >
      <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6">
        {/* Active Tenant Quick Access Banner (if already selected) */}
        {tenant && (
          <div className="mb-8 p-4 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/50 rounded-2xl flex flex-col sm:flex-row items-center justify-between gap-4 animate-fade-in shadow-sm">
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-amber-100 dark:bg-amber-900/50 rounded-xl text-amber-700 dark:text-amber-300">
                <Store className="w-5 h-5" />
              </div>
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider text-amber-700 dark:text-amber-400">
                  Restaurante Activo
                </span>
                <h3 className="text-base font-bold text-gray-900 dark:text-gray-100">
                  {tenant.name}
                </h3>
              </div>
            </div>

            <Link
              to="/catalogo"
              className="inline-flex items-center gap-2 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-semibold rounded-xl shadow-sm transition-colors cursor-pointer"
            >
              <span>Ver Catálogo</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        )}

        {/* Central Search Hero */}
        <div className="bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700/60 rounded-3xl p-8 sm:p-12 shadow-sm text-center mb-10">
          <div className="w-16 h-16 mx-auto mb-6 bg-gradient-to-tr from-amber-500 to-amber-400 text-white rounded-2xl flex items-center justify-center shadow-lg shadow-amber-500/25">
            <Utensils className="w-8 h-8" />
          </div>

          <h2 className="text-2xl sm:text-3xl font-extrabold text-gray-900 dark:text-gray-100 mb-3 tracking-tight">
            ¿Dónde querés pedir hoy?
          </h2>
          <p className="text-base text-gray-600 dark:text-gray-400 max-w-lg mx-auto mb-8">
            Ingresá al menos 3 letras del nombre del restaurante para ver locales disponibles y acceder a su carta gastronómica.
          </p>

          <div className="max-w-xl mx-auto">
            <EstablishmentSearchBar autoFocus />
          </div>
        </div>

        {/* Feature Highlights Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="p-6 bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700/60 rounded-2xl shadow-sm">
            <div className="w-10 h-10 mb-4 bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400 rounded-xl flex items-center justify-center">
              <Store className="w-5 h-5" />
            </div>
            <h4 className="font-bold text-gray-900 dark:text-gray-100 text-base mb-1">
              Catálogo en Vivo
            </h4>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Explorá platos divididos por categorías gastronómicas con precios comerciales aprobados y fotos.
            </p>
          </div>

          <div className="p-6 bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700/60 rounded-2xl shadow-sm">
            <div className="w-10 h-10 mb-4 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 rounded-xl flex items-center justify-center">
              <Utensils className="w-5 h-5" />
            </div>
            <h4 className="font-bold text-gray-900 dark:text-gray-100 text-base mb-1">
              Stock de Cocina
            </h4>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Disponibilidad sincronizada al instante con los ingredientes reales del inventario de cada sucursal.
            </p>
          </div>

          <div className="p-6 bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700/60 rounded-2xl shadow-sm">
            <div className="w-10 h-10 mb-4 bg-sky-50 dark:bg-sky-950/40 text-sky-600 dark:text-sky-400 rounded-xl flex items-center justify-center">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <h4 className="font-bold text-gray-900 dark:text-gray-100 text-base mb-1">
              Filtro de Alérgenos
            </h4>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Conformidad automática con tus restricciones dietarias registradas para un pedido seguro.
            </p>
          </div>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default EstablishmentDiscoveryPage;

