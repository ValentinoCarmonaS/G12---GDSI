import React, { useEffect, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, ArrowRight, Clock } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { BentoCard } from '../components/diner/BentoCard';
import { Button } from '../components/primitives/Button';
import { FeedbackState } from '../components/primitives/FeedbackState';
import { LoadingSkeleton } from '../components/primitives/LoadingSkeleton';
import { StatusBadge } from '../components/primitives/StatusBadge';
import { PendingAccreditationBanner } from '../components/checkout/PendingAccreditationBanner';
import { useCart } from '../context/CartContext';
import { getOrderPaymentStatus } from '../api/paymentApi';
import { confirmPayment, registerPendingPayment } from '../api/checkoutApi';
import { OrderPaymentStatusResponse } from '../types/payment';

const CONFIRMED_ORDER_STATUSES = new Set([
  'PAID',
  'CONFIRMED',
  'IN_PROGRESS',
  'READY_FOR_PICKUP',
  'COMPLETED',
]);

export const PaymentReturnPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { clearCart } = useCart();

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [paymentStatus, setPaymentStatus] = useState<OrderPaymentStatusResponse | null>(null);
  const [isPending, setIsPending] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const orderId = searchParams.get('external_reference');
  const collectionStatus = searchParams.get('collection_status') || searchParams.get('status');
  const paymentId = searchParams.get('payment_id') || searchParams.get('collection_id');

  useEffect(() => {
    if (!orderId) {
      setError('No se proporcionó una referencia válida de orden en el retorno.');
      setIsLoading(false);
      return;
    }

    const verifyStatus = async () => {
      try {
        let conflictMessage: string | null = null;

        if (collectionStatus === 'pending' || collectionStatus === 'in_process') {
          setIsPending(true);
          try {
            await registerPendingPayment({
              orderId,
              gatewayPaymentId: paymentId || 'mp-pending',
              statusDetail: 'pending_contingency',
            });
          } catch (err: any) {
            if (err?.response?.status === 409) {
              conflictMessage =
                err?.response?.data?.detail ||
                'La orden ya se encuentra cancelada o expirada. Tu carrito sigue preservado.';
            }
          }
        } else if (collectionStatus === 'approved') {
          try {
            await confirmPayment({
              orderId,
              gatewayPaymentId: paymentId || 'mp-approved',
              status: 'approved',
            });
          } catch (err: any) {
            if (err?.response?.status === 409) {
              conflictMessage =
                err?.response?.data?.detail ||
                'La orden expiró, fue cancelada o no dispone de stock suficiente. Tu carrito sigue preservado.';
            }
          }
        }

        const response = await getOrderPaymentStatus(orderId);
        setPaymentStatus(response);

        const isExpiredOrRejected =
          response.orderStatus === 'EXPIRED' ||
          response.payment?.status === 'EXPIRED' ||
          response.orderStatus === 'CANCELLED' ||
          response.payment?.status === 'REJECTED';

        if (CONFIRMED_ORDER_STATUSES.has(response.orderStatus) && !isExpiredOrRejected) {
          clearCart();
          navigate(`/checkout/confirmacion/${orderId}`, { replace: true });
          return;
        }

        if (response.orderStatus === 'EXPIRED' || response.payment?.status === 'EXPIRED') {
          navigate('/checkout', {
            state: {
              paymentError:
                conflictMessage ||
                'La reserva temporal de tu pedido expiró antes de completarse el pago. Tu carrito sigue preservado para intentar nuevamente.',
            },
          });
          return;
        }

        if (response.payment?.status === 'REJECTED' || response.orderStatus === 'CANCELLED') {
          // Si el pago fue rechazado, redirigir a checkout preservando carrito (AS-3, Q2)
          navigate('/checkout', {
            state: {
              paymentError:
                conflictMessage ||
                'El pago fue rechazado por la entidad bancaria o fondos insuficientes. Tu carrito y franja siguen preservados.',
            },
          });
          return;
        }

        if (conflictMessage) {
          navigate('/checkout', {
            state: {
              paymentError: conflictMessage,
            },
          });
          return;
        }

        if (response.payment?.status === 'APPROVED') {
          clearCart();
          navigate(`/checkout/confirmacion/${orderId}`, { replace: true });
          return;
        }

        if (
          response.orderStatus === 'PENDING_ACCREDITATION' ||
          response.payment?.status === 'PENDING' ||
          collectionStatus === 'pending' ||
          collectionStatus === 'in_process'
        ) {
          setIsPending(true);
        } else if (collectionStatus === 'null' || !collectionStatus) {
          // Abandono voluntario sin pago
          navigate('/checkout', {
            state: {
              infoMessage: 'No se completó el pago. Podés retomarlo o reintentar cuando quieras.',
            },
          });
        }
      } catch (err: any) {
        setError('Error al consultar el estado de tu pago contra el servidor.');
      } finally {
        setIsLoading(false);
      }
    };

    verifyStatus();
  }, [orderId, collectionStatus, paymentId, clearCart, navigate]);

  return (
    <WorkspaceLayout title="Confirmación de Pago" subtitle="Verificación fehaciente con Mercado Pago">
      <div className="max-w-2xl mx-auto px-4 py-10">
        {isLoading ? (
          <BentoCard
            variant="elevated"
            padding="lg"
            radius="lg"
            data-testid="payment-return-card"
            className="text-center space-y-5"
          >
            <div className="flex justify-center">
              <StatusBadge variant="info">Verificando</StatusBadge>
            </div>
            <div className="space-y-2">
              <h2 className="font-ee-display text-xl font-bold text-ee-text">
                Verificando transacción...
              </h2>
              <p className="text-sm text-ee-text-secondary">
                Estamos confirmando fehacientemente tu pago con Mercado Pago.
              </p>
            </div>
            <LoadingSkeleton variant="text" lines={3} label="Verificando estado del pago" />
          </BentoCard>
        ) : error ? (
          <BentoCard
            variant="elevated"
            padding="lg"
            radius="lg"
            data-testid="payment-return-card"
            className="space-y-6"
          >
            <div className="flex items-center justify-between gap-2">
              <h2 className="font-ee-display text-xl font-bold text-ee-text">Atención</h2>
              <StatusBadge variant="danger">Error</StatusBadge>
            </div>
            <FeedbackState
              variant="danger"
              icon={<AlertTriangle className="w-5 h-5" />}
              message={error}
            />
            <div className="flex justify-center pt-2">
              <Button variant="primary" size="md" onClick={() => navigate('/checkout')}>
                Volver al checkout
              </Button>
            </div>
          </BentoCard>
        ) : isPending ? (
          <div className="space-y-6">
            <PendingAccreditationBanner orderId={orderId || ''} />
            <BentoCard
              variant="elevated"
              padding="lg"
              radius="lg"
              data-testid="payment-return-card"
              className="text-center space-y-6"
            >
              <div className="flex flex-col items-center gap-3">
                <div className="w-14 h-14 bg-ee-warning-bg text-ee-warning-fg border border-ee-warning-border rounded-ee-md flex items-center justify-center">
                  <Clock className="w-7 h-7" aria-hidden="true" />
                </div>
                <StatusBadge variant="warning">Pendiente de acreditación</StatusBadge>
              </div>
              <div className="space-y-2">
                <h2 className="font-ee-display text-2xl font-bold text-ee-text">
                  Acreditación en curso
                </h2>
                <p className="text-sm text-ee-text-secondary max-w-md mx-auto leading-relaxed">
                  Tu orden ya se encuentra registrada. En cuanto Mercado Pago notifique la aprobación definitiva, tu pedido pasará automáticamente a cocina.
                </p>
              </div>
              <div className="pt-2 flex flex-col sm:flex-row justify-center gap-3">
                <Link
                  to={`/pedidos/${orderId}/seguimiento`}
                  className="inline-flex items-center justify-center gap-2 min-h-[44px] px-5 py-2.5 rounded-ee-sm border border-ee-primary-border bg-ee-primary text-ee-on-primary hover:bg-ee-primary-hover font-ee-sans font-semibold text-sm shadow-ee-elevated transition-colors duration-150 motion-reduce:transition-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring"
                >
                  <span>Seguir pedido</span>
                  <ArrowRight className="w-4 h-4" aria-hidden="true" />
                </Link>
                <Link
                  to="/perfil#pedidos"
                  className="inline-flex items-center justify-center min-h-[44px] px-5 py-2.5 rounded-ee-sm border border-ee-border-strong bg-ee-surface-elevated text-ee-text hover:bg-ee-surface-subtle font-ee-sans font-semibold text-sm transition-colors duration-150 motion-reduce:transition-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ee-focus-ring"
                >
                  Mis pedidos
                </Link>
                <Button variant="secondary" size="md" onClick={() => navigate('/catalogo')}>
                  Volver al catálogo
                </Button>
              </div>
            </BentoCard>
          </div>
        ) : (
          <BentoCard
            variant="elevated"
            padding="lg"
            radius="lg"
            data-testid="payment-return-card"
            className="text-center space-y-6"
          >
            <div className="flex flex-col items-center gap-3">
              <div className="w-14 h-14 bg-ee-success-bg text-ee-success-fg border border-ee-success-border rounded-ee-md flex items-center justify-center">
                <CheckCircle2 className="w-7 h-7" aria-hidden="true" />
              </div>
              <StatusBadge variant="success">Aprobado</StatusBadge>
            </div>

            <div className="space-y-2">
              <h2 className="font-ee-display text-2xl font-bold text-ee-text">
                ¡Pago confirmado!
              </h2>
              <p className="text-sm text-ee-text-secondary max-w-md mx-auto leading-relaxed">
                Tu pedido fue procesado exitosamente. Ya enviamos la comanda a cocina y tu franja horaria se encuentra garantizada.
              </p>
            </div>

            <div className="bg-ee-surface-subtle rounded-ee-md p-4 border border-ee-border-subtle text-left space-y-2 text-sm">
              <div className="flex justify-between gap-2">
                <span className="text-ee-text-secondary">Número de pedido:</span>
                <span className="font-ee-mono tabular-nums font-semibold text-ee-text">
                  {orderId}
                </span>
              </div>
              <div className="flex justify-between gap-2">
                <span className="text-ee-text-secondary">Total abonado:</span>
                <span className="font-ee-mono tabular-nums font-bold text-ee-text">
                  ${paymentStatus?.totalAmount ? paymentStatus.totalAmount.toFixed(2) : '0.00'}
                </span>
              </div>
            </div>

            <div className="pt-2 flex flex-col sm:flex-row justify-center gap-3">
              <Button
                variant="primary"
                size="md"
                rightIcon={<ArrowRight className="w-4 h-4" />}
                onClick={() => navigate('/catalogo')}
              >
                Seguir explorando
              </Button>
            </div>
          </BentoCard>
        )}
      </div>
    </WorkspaceLayout>
  );
};
