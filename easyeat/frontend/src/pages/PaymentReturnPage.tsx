import React, { useEffect, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, ArrowRight, RefreshCw, Clock } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
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
      <div className="max-w-2xl mx-auto px-4 py-12">
        {isLoading ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center shadow-sm space-y-4">
            <RefreshCw className="w-10 h-10 animate-spin text-sky-600 mx-auto" />
            <h3 className="text-lg font-bold text-gray-900">Verificando transacción...</h3>
            <p className="text-sm text-gray-500">
              Estamos confirmando fehacientemente tu pago con Mercado Pago.
            </p>
          </div>
        ) : error ? (
          <div className="bg-white rounded-2xl border border-rose-200 p-8 text-center shadow-sm space-y-4">
            <div className="w-12 h-12 bg-rose-100 text-rose-600 rounded-full flex items-center justify-center mx-auto">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <h3 className="text-lg font-bold text-gray-900">Atención</h3>
            <p className="text-sm text-gray-600">{error}</p>
            <div className="pt-4">
              <button
                type="button"
                onClick={() => navigate('/checkout')}
                className="px-6 py-2.5 bg-neutral-900 hover:bg-neutral-800 text-white font-medium rounded-xl text-sm transition-colors shadow-sm"
              >
                Volver al checkout
              </button>
            </div>
          </div>
        ) : isPending ? (
          <div className="space-y-6">
            <PendingAccreditationBanner orderId={orderId || ''} />
            <div className="bg-white rounded-2xl border border-amber-200 p-8 text-center shadow-sm space-y-6">
              <div className="w-16 h-16 bg-amber-100 text-amber-600 rounded-full flex items-center justify-center mx-auto">
                <Clock className="w-8 h-8" />
              </div>
              <div className="space-y-2">
                <h2 className="text-2xl font-extrabold text-gray-900">Acreditación en curso</h2>
                <p className="text-sm text-gray-600 max-w-md mx-auto">
                  Tu orden ya se encuentra registrada. En cuanto Mercado Pago notifique la aprobación definitiva, tu pedido pasará automáticamente a cocina.
                </p>
              </div>
              <div className="pt-2 flex flex-col sm:flex-row justify-center gap-3">
                <Link
                  to={`/pedidos/${orderId}/seguimiento`}
                  className="px-6 py-3 bg-emerald-700 hover:bg-emerald-800 text-white font-semibold rounded-xl text-sm transition-colors shadow-md flex items-center justify-center gap-2"
                >
                  <span>Seguir pedido</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
                <Link
                  to="/perfil#pedidos"
                  className="px-6 py-3 bg-white border border-gray-300 hover:bg-gray-50 text-gray-800 font-semibold rounded-xl text-sm transition-colors shadow-sm flex items-center justify-center"
                >
                  Mis pedidos
                </Link>
                <button
                  type="button"
                  onClick={() => navigate('/catalogo')}
                  className="px-6 py-3 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-xl text-sm transition-colors shadow-md flex items-center justify-center gap-2"
                >
                  <span>Volver al catálogo</span>
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-emerald-200 p-8 text-center shadow-sm space-y-6">
            <div className="w-16 h-16 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-8 h-8" />
            </div>

            <div className="space-y-2">
              <h2 className="text-2xl font-extrabold text-gray-900">¡Pago confirmado!</h2>
              <p className="text-sm text-gray-600 max-w-md mx-auto">
                Tu pedido fue procesado exitosamente. Ya enviamos la comanda a cocina y tu franja horaria se encuentra garantizada.
              </p>
            </div>

            <div className="bg-neutral-50 rounded-xl p-4 border border-neutral-200 text-left space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-neutral-500">Número de pedido:</span>
                <span className="font-mono font-semibold text-neutral-800">{orderId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-neutral-500">Total abonado:</span>
                <span className="font-semibold text-emerald-700">
                  ${paymentStatus?.totalAmount ? paymentStatus.totalAmount.toFixed(2) : '0.00'}
                </span>
              </div>
            </div>

            <div className="pt-2 flex flex-col sm:flex-row justify-center gap-3">
              <button
                type="button"
                onClick={() => navigate('/catalogo')}
                className="px-6 py-3 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-xl text-sm transition-colors shadow-md flex items-center justify-center gap-2"
              >
                <span>Seguir explorando</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};
