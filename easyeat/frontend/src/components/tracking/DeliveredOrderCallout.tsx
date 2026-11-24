import React from 'react';
import { Link, useInRouterContext } from 'react-router-dom';
import { PackageCheck } from 'lucide-react';
import { BentoCard } from '../diner';
import { StatusBadge } from '../primitives';

const FINAL_MESSAGE = 'Pedido Entregado — ¡Que disfrutes tu comida!';

export interface DeliveredOrderCalloutProps {
  ticketNumber: number | null;
  message?: string;
}

/**
 * Diner's terminal view once the counter confirms the delivery (spec 022, US2, RF-014, contract 03 §4).
 * It never shows the PIN (RF-015, edge case 12); the purchase summary is the page's own block.
 */
export const DeliveredOrderCallout: React.FC<DeliveredOrderCalloutProps> = ({
  ticketNumber,
  message,
}) => {
  const inRouter = useInRouterContext();

  const primaryLinkClass =
    'inline-flex items-center justify-center min-h-[44px] px-5 py-2.5 rounded-ee-sm font-semibold text-sm bg-ee-primary text-ee-on-primary border border-ee-primary-border hover:bg-ee-primary-hover transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring';
  const secondaryLinkClass =
    'inline-flex items-center justify-center min-h-[44px] px-5 py-2.5 rounded-ee-sm font-semibold text-sm bg-ee-surface-elevated text-ee-text border border-ee-border-strong hover:bg-ee-surface-subtle transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring';

  return (
    <BentoCard
      as="section"
      variant="elevated"
      padding="lg"
      radius="lg"
      data-testid="delivered-order-callout"
      className="border-ee-success-border text-center space-y-5"
    >
      <div className="flex justify-center">
        <StatusBadge variant="success" className="gap-1.5 px-3 py-1 text-xs">
          <PackageCheck className="w-4 h-4" aria-hidden="true" />
          Entrega completada
        </StatusBadge>
      </div>

      <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-ee-text">
        {message || FINAL_MESSAGE}
      </h2>

      {ticketNumber != null && (
        <div className="py-3 px-6 bg-ee-surface-subtle border border-ee-border-subtle rounded-ee-md inline-block">
          <span className="text-3xl sm:text-4xl font-ee-mono tabular-nums font-bold text-ee-text tracking-wider">
            #{ticketNumber}
          </span>
        </div>
      )}

      <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
        {inRouter ? (
          <>
            <Link to="/catalogo" className={primaryLinkClass}>
              Explorar catálogo
            </Link>
            <Link to="/perfil#pedidos" className={secondaryLinkClass}>
              Mis pedidos
            </Link>
          </>
        ) : (
          <>
            <a href="/catalogo" className={primaryLinkClass}>
              Explorar catálogo
            </a>
            <a href="/perfil#pedidos" className={secondaryLinkClass}>
              Mis pedidos
            </a>
          </>
        )}
      </div>
    </BentoCard>
  );
};
