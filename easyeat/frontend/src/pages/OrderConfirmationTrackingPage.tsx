import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Mail, Utensils } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { BrandThemeProvider } from '../components/common/BrandThemeProvider';
import { PendingAccreditationBanner } from '../components/checkout/PendingAccreditationBanner';
import { OrderSuccessGreenBanner } from '../components/tracking/OrderSuccessGreenBanner';
import { LiveTrackingWidget } from '../components/tracking/LiveTrackingWidget';
import { BentoCard } from '../components/diner';
import { Button, FeedbackState, LoadingSkeleton, StatusBadge } from '../components/primitives';
import { orderTrackingApi } from '../api/orderTrackingApi';
import type { OrderConfirmationTrackingResponse } from '../types/orderTracking';

export const OrderConfirmationTrackingPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<OrderConfirmationTrackingResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchOrderDetails = useCallback(async () => {
    if (!orderId) {
      setErrorMessage('Identificador de pedido no proporcionado.');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const data = await orderTrackingApi.getConfirmationTracking(orderId);
      setOrder(data);
    } catch (err: any) {
      console.error('Error fetching confirmation tracking details:', err);
      setErrorMessage(
        err?.response?.data?.detail ||
        'No pudimos cargar la información del pedido. Por favor, verificá el enlace o intentá más tarde.'
      );
    } finally {
      setIsLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    fetchOrderDetails();
  }, [fetchOrderDetails]);

  const isPendingApproval = Boolean(
    order && (order.status === 'PENDING_ACCREDITATION' || order.approvalStatus === 'PENDING')
  );

  useEffect(() => {
    if (!orderId || !isPendingApproval) {
      return;
    }

    let cancelled = false;
    const timerId = setTimeout(async () => {
      try {
        const updated = await orderTrackingApi.getConfirmationTracking(orderId);
        if (!cancelled) {
          setOrder(updated);
        }
      } catch {
        // Mantiene vista provisional ante errores transitorios y reintenta en el próximo ciclo
      }
    }, 5000);

    return () => {
      cancelled = true;
      clearTimeout(timerId);
    };
  }, [orderId, order, isPendingApproval]);

  const handleLiveStatusChange = useCallback(
    (status: string, ticketNumber?: number | null) => {
      setOrder((prev) => {
        if (!prev) return prev;
        const nextTicketNumber = ticketNumber ?? prev.ticketNumber;
        if (prev.status === status && prev.ticketNumber === nextTicketNumber) {
          return prev;
        }
        return {
          ...prev,
          status: status as OrderConfirmationTrackingResponse['status'],
          ticketNumber: nextTicketNumber,
        };
      });
    },
    []
  );

  return (
    <WorkspaceLayout
      title="Confirmación de Pedido"
      subtitle="Detalle de compra, respaldo por correo y seguimiento en vivo"
    >
      <BrandThemeProvider establishmentId={order?.establishmentId}>
        <div className="max-w-4xl mx-auto px-4 py-6 sm:py-8 space-y-6">
          {/* Navigation back */}
          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={() => navigate('/catalogo')}
              className="inline-flex items-center gap-2 min-h-[44px] px-3 py-2 rounded-ee-sm text-sm font-semibold text-ee-text-secondary hover:text-ee-text hover:bg-ee-surface-subtle transition-colors duration-150 motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
            >
              <ArrowLeft className="w-4 h-4" aria-hidden="true" />
              Volver a la carta
            </button>
          </div>

          {/* Loading State */}
          {isLoading && (
            <BentoCard variant="elevated" padding="lg" radius="lg" className="space-y-4">
              <LoadingSkeleton variant="block" />
              <LoadingSkeleton variant="block" />
              <p className="text-ee-text-secondary font-medium text-sm text-center">
                Cargando confirmación del pedido...
              </p>
            </BentoCard>
          )}

          {/* Error State */}
          {!isLoading && errorMessage && (
            <FeedbackState
              variant="danger"
              title="No pudimos cargar la información del pedido"
              message={errorMessage}
              actionLabel="Reintentar"
              onAction={fetchOrderDetails}
            />
          )}

          {/* Provisional / Confirmation View */}
          {!isLoading && order && (
            <div
              data-testid="order-confirmation-content"
              data-order-status={order.status}
              className="space-y-6"
            >
              {isPendingApproval ? (
                <div className="space-y-6">
                  <PendingAccreditationBanner orderId={order.orderId} />
                  <BentoCard
                    variant="elevated"
                    padding="md"
                    radius="lg"
                    className="border-ee-warning-border flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4"
                  >
                    <div className="space-y-1">
                      <h2 className="text-lg font-bold text-ee-text">
                        Esperando acreditación de pago
                      </h2>
                      <p className="text-sm text-ee-text-secondary">
                        Actualizamos el estado automáticamente cada 5 segundos. Cuando Mercado Pago acredite el pago, tu pedido pasará a cocina y verás el comprobante completo.
                      </p>
                    </div>
                    <Link
                      to="/perfil#pedidos"
                      className="inline-flex items-center justify-center min-h-[44px] px-5 py-2.5 rounded-ee-sm font-semibold text-sm bg-ee-primary text-ee-on-primary border border-ee-primary-border hover:bg-ee-primary-hover transition-colors duration-150 motion-reduce:transition-none shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ee-focus-ring"
                    >
                      Mis pedidos
                    </Link>
                  </BentoCard>
                </div>
              ) : (
                <>
                  {/* Confirmation Banner (AS-1, AS-3, INV-004, FR-008) */}
                  <OrderSuccessGreenBanner
                    ticketNumber={order.ticketNumber}
                    pickupDate={order.pickupDate}
                    timeSlotStart={order.timeSlotStart}
                    timeSlotEnd={order.timeSlotEnd}
                    establishmentName={order.establishmentName}
                    fulfillmentMode={order.fulfillmentMode}
                  />

                  {/* Live Tracking Widget (FR-006..FR-012) */}
                  <LiveTrackingWidget
                    orderId={order.orderId}
                    ticketNumber={order.ticketNumber}
                    fulfillmentMode={order.fulfillmentMode}
                    timeSlotStart={order.timeSlotStart}
                    timeSlotEnd={order.timeSlotEnd}
                    onStatusChange={handleLiveStatusChange}
                  />

                  {/* Email Guarantee Notice (AS-2, FR-002) */}
                  <BentoCard
                    variant="subtle"
                    padding="sm"
                    radius="md"
                    className="flex items-start gap-3 border-ee-success-border bg-ee-success-bg text-ee-success-fg"
                  >
                    <Mail className="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
                    <div className="text-sm">
                      <p className="font-bold">Comprobante emitido con respaldo por correo</p>
                      <p className="text-xs mt-0.5">
                        Te enviamos el comprobante formal con los datos de retiro y el enlace directo de seguimiento a tu casilla registrada.
                      </p>
                    </div>
                  </BentoCard>
                </>
              )}

              {/* Order Items Breakdown & Summary (AS-3) */}
              <BentoCard variant="elevated" padding="md" radius="lg" className="space-y-5">
                <div className="flex items-center justify-between gap-3 border-b border-ee-border-subtle pb-4">
                  <div className="flex items-center gap-2 text-ee-text font-bold text-lg">
                    <Utensils className="w-5 h-5 text-ee-text-secondary" aria-hidden="true" />
                    <h2>Detalle del Pedido</h2>
                  </div>
                  <StatusBadge variant="neutral">
                    {order.items.length} {order.items.length === 1 ? 'producto' : 'productos'}
                  </StatusBadge>
                </div>

                {/* Items List */}
                <div className="divide-y divide-ee-border-subtle">
                  {order.items.map((item, index) => (
                    <div
                      key={item.dishId || index}
                      className="py-3 flex items-center justify-between gap-3 text-sm"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <span className="w-8 h-8 rounded-ee-sm bg-ee-surface-subtle border border-ee-border-subtle text-ee-text font-ee-mono tabular-nums font-bold flex items-center justify-center text-xs shrink-0">
                          {item.quantity}x
                        </span>
                        <span className="font-semibold text-ee-text truncate">{item.dishName}</span>
                      </div>
                      <div className="text-right shrink-0">
                        <span className="font-ee-mono tabular-nums font-bold text-ee-text">
                          ${item.subtotal.toFixed(2)}
                        </span>
                        {item.quantity > 1 && (
                          <span className="block text-[11px] font-ee-mono tabular-nums text-ee-text-secondary">
                            ${item.unitPrice.toFixed(2)} c/u
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                {/* Total Summary */}
                <div className="border-t border-ee-border-subtle pt-4 flex items-center justify-between gap-4">
                  <div>
                    <span className="text-xs font-ee-mono font-semibold text-ee-text-secondary uppercase tracking-wider block">
                      {isPendingApproval ? 'Total del Pedido' : 'Total Abonado'}
                    </span>
                    <span
                      className={`text-xs font-medium ${isPendingApproval ? 'text-ee-warning-fg' : 'text-ee-success-fg'
                        }`}
                    >
                      {isPendingApproval
                        ? 'Acreditación pendiente'
                        : 'Abonado mediante pasarela segura'}
                    </span>
                  </div>
                  <span className="text-2xl sm:text-3xl font-ee-mono tabular-nums font-bold text-ee-text">
                    ${order.totalAmount.toFixed(2)}
                  </span>
                </div>
              </BentoCard>

              {/* Action Bar (AS-3, FR-003) */}
              <div className="flex justify-center pt-2">
                <Button
                  type="button"
                  variant="primary"
                  size="lg"
                  onClick={() => navigate('/catalogo')}
                  className="w-full sm:w-auto"
                >
                  Explorar carta
                </Button>
              </div>
            </div>
          )}
        </div>
      </BrandThemeProvider>
    </WorkspaceLayout>
  );
};
