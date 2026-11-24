import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Clock,
  ExternalLink,
  Loader2,
  Receipt,
  ShoppingBag,
  XCircle,
} from 'lucide-react';
import { getMyOrders } from '../../api/orderApi';
import { BentoCard } from '../diner/BentoCard';
import { EmptyState } from '../primitives/EmptyState';
import { FeedbackState } from '../primitives/FeedbackState';
import { LoadingSkeleton } from '../primitives/LoadingSkeleton';
import { cancelPayment } from '../../api/paymentApi';
import type { DinerOrderSummaryResponse, OrderStatus } from '../../types/order';

export interface MyOrdersSectionProps {
  onResumePayment?: (redirectUrl: string) => void;
}

const ORDER_STATUS_META: Record<OrderStatus, { label: string; badgeClass: string }> = {
  PENDING_PAYMENT: {
    label: 'Pendiente de pago',
    badgeClass: 'bg-amber-100 text-amber-900 border-amber-200',
  },
  PENDING_ACCREDITATION: {
    label: 'En acreditación',
    badgeClass: 'bg-sky-100 text-sky-900 border-sky-200',
  },
  IN_PROGRESS: {
    label: 'En preparación',
    badgeClass: 'bg-indigo-100 text-indigo-900 border-indigo-200',
  },
  READY_FOR_PICKUP: {
    label: 'Listo para retirar',
    badgeClass: 'bg-emerald-100 text-emerald-900 border-emerald-200',
  },
  COMPLETED: {
    label: 'Completado',
    badgeClass: 'bg-neutral-100 text-neutral-800 border-neutral-200',
  },
  PAID: {
    label: 'Pagado',
    badgeClass: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  },
  CONFIRMED: {
    label: 'Confirmado',
    badgeClass: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  },
  CANCELLED: {
    label: 'Cancelado',
    badgeClass: 'bg-red-50 text-red-800 border-red-200',
  },
  EXPIRED: {
    label: 'Expirado',
    badgeClass: 'bg-neutral-100 text-neutral-600 border-neutral-200',
  },
};

const TRACKABLE_STATUSES: ReadonlySet<OrderStatus> = new Set([
  'PENDING_ACCREDITATION',
  'IN_PROGRESS',
  'READY_FOR_PICKUP',
  'COMPLETED',
  'PAID',
  'CONFIRMED',
]);

const formatCurrency = (amount: number): string =>
  new Intl.NumberFormat('es-AR', {
    style: 'currency',
    currency: 'ARS',
    minimumFractionDigits: 2,
  }).format(amount);

const formatTimeShort = (timeStr: string | null): string => {
  if (!timeStr) return '';
  return timeStr.slice(0, 5);
};

const formatScheduleSummary = (order: DinerOrderSummaryResponse): string => {
  if (order.fulfillmentMode === 'IMMEDIATE') {
    return 'Retiro inmediato';
  }
  const datePart = order.pickupDate || '';
  const start = formatTimeShort(order.timeSlotStart);
  const end = formatTimeShort(order.timeSlotEnd);
  if (datePart && start && end) {
    return `Retiro programado: ${datePart} · ${start}–${end}`;
  }
  if (datePart) {
    return `Retiro programado: ${datePart}`;
  }
  return 'Retiro programado';
};

export const MyOrdersSection: React.FC<MyOrdersSectionProps> = ({ onResumePayment }) => {
  const [orders, setOrders] = useState<DinerOrderSummaryResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [cancellingOrderId, setCancellingOrderId] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getMyOrders();
      setOrders(Array.isArray(data) ? data : []);
    } catch {
      setError('No pudimos cargar tu historial de pedidos. Por favor, intentá nuevamente.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const handleResumePayment = (redirectUrl: string) => {
    if (onResumePayment) {
      onResumePayment(redirectUrl);
    } else {
      window.location.href = redirectUrl;
    }
  };

  const handleCancelAttempt = async (orderId: string) => {
    setActionError(null);
    setCancellingOrderId(orderId);
    try {
      await cancelPayment({ orderId });
      await loadOrders();
    } catch {
      setActionError('No pudimos cancelar el intento de pago. Intentá nuevamente.');
    } finally {
      setCancellingOrderId(null);
    }
  };

  if (loading) {
    return (
      <div data-testid="orders-loading-skeleton" className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <LoadingSkeleton variant="block" />
        <LoadingSkeleton variant="block" />
      </div>
    );
  }

  if (error) {
    return (
      <FeedbackState
        variant="danger"
        role="alert"
        title="No pudimos cargar tus pedidos"
        message={error}
        actionLabel="Reintentar"
        onAction={() => void loadOrders()}
      />
    );
  }

  if (orders.length === 0) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={<ShoppingBag className="h-6 w-6" aria-hidden="true" />}
          title="Todavía no realizaste pedidos"
          description="Cuando confirmes un pedido vas a poder seguir su estado y acceder al comprobante desde acá."
        />
        <div className="flex justify-center">
          <Link
            to="/catalogo"
            className="inline-flex min-h-[44px] items-center gap-2 rounded-ee-sm border border-ee-primary-border bg-ee-primary px-5 py-2.5 text-sm font-semibold text-ee-on-primary transition-colors hover:bg-ee-primary-hover"
          >
            Explorar catálogo
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {actionError && <FeedbackState variant="danger" role="alert" title="Error en la acción" message={actionError} />}

      <div data-testid="my-orders-bento-grid" className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {orders.map((order) => {
          const statusMeta = ORDER_STATUS_META[order.status] || {
            label: order.status,
            badgeClass: 'bg-neutral-100 text-neutral-800 border-neutral-200',
          };
          const isTrackable = TRACKABLE_STATUSES.has(order.status);
          const isPendingPayment = order.status === 'PENDING_PAYMENT';
          const validRedirectUrl =
            order.redirectUrl && order.redirectUrl.trim().length > 0
              ? order.redirectUrl.trim()
              : null;
          const isCancelling = cancellingOrderId === order.orderId;
          const trackingHref = order.trackingUrl || `/pedidos/${order.orderId}/seguimiento`;

          return (
            <BentoCard
              as="article"
              variant="elevated"
              padding="md"
              key={order.orderId}
              data-testid={`order-card-${order.orderId}`}
              className="space-y-4"
            >
            <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-mono text-neutral-500">
                    Pedido #{order.orderId.slice(0, 8).toUpperCase()}
                  </span>
                  {order.ticketNumber !== null && order.ticketNumber !== undefined && (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-neutral-900 text-amber-200 text-xs font-semibold">
                      <Receipt className="w-3 h-3" />
                      Comanda #{order.ticketNumber}
                    </span>
                  )}
                  <span
                    className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${statusMeta.badgeClass}`}
                  >
                    {statusMeta.label}
                  </span>
                </div>
                <h3 className="text-base font-semibold text-neutral-900">
                  {order.establishmentName}
                </h3>
                <p className="text-xs text-neutral-600 flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-neutral-400" />
                  <span>{formatScheduleSummary(order)}</span>
                </p>
              </div>

              <div className="sm:text-right">
                <p className="text-xs text-neutral-500">Total</p>
                <p
                  data-testid={`order-total-${order.orderId}`}
                  className="font-ee-mono text-lg font-bold tabular-nums text-ee-text"
                >
                  {formatCurrency(order.totalAmount)}
                </p>
              </div>
            </div>

            {order.items && order.items.length > 0 && (
              <ul className="border-t border-b border-neutral-100 py-3 space-y-1.5 text-sm text-neutral-700">
                {order.items.map((item) => (
                  <li
                    key={`${order.orderId}-${item.dishId}`}
                    className="flex items-center justify-between gap-2"
                  >
                    <span>
                      {item.quantity} × {item.dishName}
                    </span>
                    <span className="text-neutral-600 font-medium">
                      {formatCurrency(item.subtotal)}
                    </span>
                  </li>
                ))}
              </ul>
            )}

            {isPendingPayment && !validRedirectUrl && (
              <p className="text-xs text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                No hay una preferencia de pago activa para este pedido. Podés cancelar el intento para realizar una nueva compra.
              </p>
            )}

            {(isTrackable || isPendingPayment) && (
              <div className="flex flex-wrap items-center gap-3 pt-1">
                {isTrackable && (
                  <Link
                    to={trackingHref}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-neutral-900 hover:bg-neutral-800 text-white text-sm font-medium transition-colors"
                  >
                    Ver seguimiento
                    <ArrowRight className="w-4 h-4" />
                  </Link>
                )}

                {isPendingPayment && (
                  <>
                    <button
                      type="button"
                      disabled={!validRedirectUrl || isCancelling}
                      onClick={() => {
                        if (validRedirectUrl) {
                          handleResumePayment(validRedirectUrl);
                        }
                      }}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Retomar pago
                      <ExternalLink className="w-4 h-4" />
                    </button>

                    <button
                      type="button"
                      disabled={isCancelling}
                      onClick={() => void handleCancelAttempt(order.orderId)}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg border border-neutral-300 bg-white hover:bg-neutral-50 text-neutral-800 text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isCancelling ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Cancelando...
                        </>
                      ) : (
                        <>
                          <XCircle className="w-4 h-4 text-neutral-500" />
                          Cancelar intento
                        </>
                      )}
                    </button>
                  </>
                )}
              </div>
            )}
            </BentoCard>
          );
        })}
      </div>
    </div>
  );
};

