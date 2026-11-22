import React from 'react';
import { Link } from 'react-router-dom';
import { Store, Utensils, ShieldCheck, ArrowRight } from 'lucide-react';
import { WorkspaceLayout } from '@/components/common/WorkspaceLayout';
import { EstablishmentSearchBar } from '@/components/catalog/EstablishmentSearchBar';
import { BentoCard } from '@/components/diner';
import { useTenant } from '@/hooks/useTenant';

export const EstablishmentDiscoveryPage: React.FC = () => {
  const { tenant } = useTenant();

  const featureHighlights = [
    {
      icon: <Store aria-hidden="true" className="w-5 h-5" />,
      title: 'Catálogo en Vivo',
      description:
        'Explorá platos divididos por categorías gastronómicas con precios comerciales aprobados y fotos.',
    },
    {
      icon: <Utensils aria-hidden="true" className="w-5 h-5" />,
      title: 'Stock de Cocina',
      description:
        'Disponibilidad sincronizada al instante con los ingredientes reales del inventario de cada sucursal.',
    },
    {
      icon: <ShieldCheck aria-hidden="true" className="w-5 h-5" />,
      title: 'Filtro de Alérgenos',
      description:
        'Conformidad automática con tus restricciones dietarias registradas para un pedido seguro.',
    },
  ];

  return (
    <WorkspaceLayout
      title="Descubrimiento de Restaurantes"
      subtitle="Buscá y seleccioná un establecimiento para explorar su menú en vivo"
    >
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Active Tenant Quick Access Bento Card (if already selected) */}
        {tenant && (
          <BentoCard
            as="section"
            variant="subtle"
            padding="md"
            radius="lg"
            aria-label="Restaurante activo"
            className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"
          >
            <div className="flex items-center gap-3 min-w-0">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-ee-md bg-ee-surface-elevated border border-ee-border-subtle text-ee-primary-border">
                <Store aria-hidden="true" className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <span className="font-ee-mono text-xs font-semibold uppercase tracking-wider text-ee-text-secondary">
                  Restaurante Activo
                </span>
                <h3 className="font-ee-display text-base font-bold text-ee-text truncate">
                  {tenant.name}
                </h3>
              </div>
            </div>

            <Link
              to="/catalogo"
              className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-ee-sm border border-ee-primary-border bg-ee-primary px-4 py-2 text-sm font-semibold text-ee-on-primary shadow-ee-elevated transition-colors motion-reduce:transition-none hover:bg-ee-primary-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring shrink-0"
            >
              <span>Ver Catálogo</span>
              <ArrowRight aria-hidden="true" className="w-4 h-4" />
            </Link>
          </BentoCard>
        )}

        {/* Central Search Hero Bento Block */}
        <BentoCard
          as="section"
          variant="elevated"
          padding="lg"
          radius="lg"
          className="text-center"
        >
          <div className="w-14 h-14 mx-auto mb-5 bg-ee-surface-subtle border border-ee-border-subtle text-ee-primary-border rounded-ee-md flex items-center justify-center">
            <Utensils aria-hidden="true" className="w-7 h-7" />
          </div>

          <h2 className="font-ee-display text-2xl sm:text-3xl font-bold text-ee-text mb-2 tracking-tight">
            ¿Dónde querés pedir hoy?
          </h2>
          <p className="text-sm sm:text-base text-ee-text-secondary max-w-lg mx-auto mb-6 leading-relaxed">
            Ingresá al menos 3 letras del nombre del restaurante para ver locales disponibles y acceder a su carta gastronómica.
          </p>

          <div className="max-w-xl mx-auto">
            <EstablishmentSearchBar autoFocus />
          </div>
        </BentoCard>

        {/* Feature Highlights Bento Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-6">
          {featureHighlights.map((item) => (
            <BentoCard
              key={item.title}
              as="article"
              variant="elevated"
              padding="md"
              radius="md"
              className="space-y-3"
            >
              <div className="w-10 h-10 bg-ee-surface-subtle border border-ee-border-subtle text-ee-primary-border rounded-ee-sm flex items-center justify-center">
                {item.icon}
              </div>
              <h3 className="font-ee-display font-bold text-ee-text text-base">{item.title}</h3>
              <p className="text-sm text-ee-text-secondary leading-relaxed">{item.description}</p>
            </BentoCard>
          ))}
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default EstablishmentDiscoveryPage;
