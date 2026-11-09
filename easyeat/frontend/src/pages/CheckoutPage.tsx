import React, { useState, useMemo, useEffect } from 'react';
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import { ArrowLeft, ShoppingBag, RefreshCw } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { FulfillmentModeSelector } from '../components/checkout/FulfillmentModeSelector';
import { OperatingDatePicker, OperatingDayItem } from '../components/checkout/OperatingDatePicker';
import { TimeSlotGrid } from '../components/checkout/TimeSlotGrid';
import { CheckoutSummary } from '../components/checkout/CheckoutSummary';
import { ConflictNotificationModal } from '../components/checkout/ConflictNotificationModal';
import { PendingOrderCountdown } from '../components/checkout/PendingOrderCountdown';
import { InProcessPaymentPanel } from '../components/checkout/InProcessPaymentPanel';
import { PaymentAlertBanner } from '../components/checkout/PaymentAlertBanner';
import { useCart } from '../context/CartContext';
import { useCheckoutFulfillment } from '../hooks/useCheckoutFulfillment';
import { useTimeSlotPolling } from '../hooks/useTimeSlotPolling';
import { cancelOrder } from '../api/orderApi';
import { initiatePayment, cancelPayment, getOrderPaymentStatus } from '../api/paymentApi';
import { OrderDetailResponse } from '../types/order';
import { PaymentInitiationRequest, OrderPaymentStatusResponse } from '../types/payment';

function generateNext7Days(): OperatingDayItem[] {
  const days: OperatingDayItem[] = [];
  const dayNames = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
  const now = new Date();

  for (let i = 0; i < 7; i++) {
    const d = new Date(now);
    d.setDate(now.getDate() + i);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    const dateStr = `${yyyy}-${mm}-${dd}`;

    days.push({
      date: dateStr,
      dayName: dayNames[d.getDay()],
      dayNumber: d.getDate(),
      isOperating: true,
    });
  }

  return days;
}

export const CheckoutPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const { items, totalAmount } = useCart();

  const establishmentId = useMemo(() => {
    const fromStorage =
      typeof window !== 'undefined' && window.localStorage
        ? window.localStorage.getItem('selectedEstablishmentId')
        : null;
    const fromActiveTenant =
      typeof window !== 'undefined' && window.sessionStorage
        ? (() => {
            try {
              const active = window.sessionStorage.getItem('easyeat_active_tenant');
              return active ? JSON.parse(active)?.id : null;
            } catch {
              return null;
            }
          })()
        : null;

    return searchParams.get('establishmentId') || items[0]?.tenantId || fromActiveTenant || fromStorage || '';
  }, [searchParams, items]);

  const operatingDays = useMemo(() => generateNext7Days(), []);

  const {
    fulfillmentMode,
    selectedDate,
    selectedSlot,
    setFulfillmentMode,
    setSelectedDate,
    setSelectedSlot,
  } = useCheckoutFulfillment();

  const { slots, isLoading: isLoadingSlots, refresh: refreshSlots } = useTimeSlotPolling({
    establishmentId,
    date: selectedDate || undefined,
    enabled: Boolean(establishmentId && fulfillmentMode === 'SCHEDULED' && selectedDate),
  });

  const [pendingOrder, setPendingOrder] = useState<OrderDetailResponse | null>(null);
  const [inProcessPayment, setInProcessPayment] = useState<OrderPaymentStatusResponse | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isCancelling, setIsCancelling] = useState<boolean>(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);

  // Read alert messages passed from redirect location state (e.g. from PaymentReturnPage)
  useEffect(() => {
    if (location.state?.paymentError) {
      setSubmissionError(location.state.paymentError);
    }
    if (location.state?.infoMessage) {
      setInfoMessage(location.state.infoMessage);
    }
  }, [location.state]);

  const [conflictModal, setConflictModal] = useState<{
    isOpen: boolean;
    errorCode: string;
    errorMessage: string;
  }>({
    isOpen: false,
    errorCode: '',
    errorMessage: '',
  });

  const isFormValid = useMemo(() => {
    if (items.length === 0) return false;
    if (!fulfillmentMode) return false;
    if (fulfillmentMode === 'SCHEDULED' && (!selectedDate || !selectedSlot)) return false;
    return true;
  }, [items, fulfillmentMode, selectedDate, selectedSlot]);

  // Handler for Mercado Pago Checkout Pro (T035, AS-1, AS-5, FR-001, FR-002)
  const handlePayWithMercadoPago = async () => {
    if (!isFormValid || !establishmentId || inProcessPayment) return;

    setIsSubmitting(true);
    setSubmissionError(null);
    setInfoMessage(null);

    const payload: PaymentInitiationRequest = {
      establishmentId,
      fulfillmentMode: fulfillmentMode!,
      paymentMethod: 'MERCADO_PAGO',
      pickupDate: fulfillmentMode === 'SCHEDULED' ? selectedDate : undefined,
      timeSlotStart: fulfillmentMode === 'SCHEDULED' ? selectedSlot?.startTime : undefined,
      timeSlotEnd: fulfillmentMode === 'SCHEDULED' ? selectedSlot?.endTime : undefined,
      items: items.map((i) => ({
        dishId: i.dishId,
        quantity: i.quantity,
      })),
    };

    try {
      const response = await initiatePayment(payload);

      // Save ongoing payment info in state and redirect immediately to Sandbox/Production init_point
      if (response.redirectUrl) {
        window.location.href = response.redirectUrl;
      }
    } catch (err: any) {
      const status = err?.response?.status;
      const data = err?.response?.data;
      const code = data?.code;

      if (status === 409) {
        if (code === 'PAYMENT_IN_PROCESS' && data?.orderId) {
          // Fetch existing in-process payment details to display panel
          try {
            const statusRes = await getOrderPaymentStatus(data.orderId);
            setInProcessPayment(statusRes);
          } catch {
            // fallback
          }
        }

        setConflictModal({
          isOpen: true,
          errorCode: code || 'SLOT_SOLD_OUT',
          errorMessage:
            data?.detail ||
            data?.message ||
            'La franja horaria seleccionada acaba de agotarse o ya tenés un pago en curso.',
        });

        if (code === 'SLOT_SOLD_OUT' || !code) {
          refreshSlots();
          setSelectedSlot(null);
        }
      } else if (status === 502) {
        setSubmissionError(
          data?.message || 'Fallo al conectar con Mercado Pago. No se realizó ningún cargo. Podés intentar nuevamente.'
        );
      } else {
        setSubmissionError(
          data?.detail || data?.message || 'Ocurrió un error al procesar el pago. Por favor, intentá nuevamente.'
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancelPaymentAttempt = async () => {
    if (!inProcessPayment?.orderId) return;
    setIsCancelling(true);
    try {
      await cancelPayment({ orderId: inProcessPayment.orderId });
      setInProcessPayment(null);
      setInfoMessage('El intento de pago fue cancelado y los recursos fueron liberados.');
    } catch (err: any) {
      setSubmissionError('Error al cancelar el intento de pago.');
    } finally {
      setIsCancelling(false);
    }
  };

  const handleCancelOrder = async () => {
    if (!pendingOrder) return;
    setIsCancelling(true);
    try {
      await cancelOrder(pendingOrder.orderId);
      setPendingOrder(null);
      setInfoMessage('Tu reserva ha sido cancelada exitosamente y el cupo fue liberado.');
    } catch (err: any) {
      console.error('Error al cancelar la orden:', err);
    } finally {
      setIsCancelling(false);
    }
  };

  const handleOrderExpire = () => {
    // Keep order in expired state so user sees PendingOrderCountdown expiry notification
  };

  return (
    <WorkspaceLayout title="Checkout" subtitle="Configuración de retiro y pago con Mercado Pago">
      <div className="max-w-6xl mx-auto px-4 py-6 space-y-6">
        <button
          type="button"
          onClick={() => navigate('/catalog')}
          className="inline-flex items-center gap-2 text-sm font-semibold text-gray-600 hover:text-emerald-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Volver a la carta
        </button>

        {infoMessage && (
          <PaymentAlertBanner
            type="info"
            message={infoMessage}
            onClose={() => setInfoMessage(null)}
          />
        )}

        {submissionError && (
          <PaymentAlertBanner
            type="error"
            message={submissionError}
            onClose={() => setSubmissionError(null)}
          />
        )}

        {/* Panel de pago en curso (AS-5, FR-010, Q3) */}
        {inProcessPayment && (
          <InProcessPaymentPanel
            redirectUrl={inProcessPayment.payment?.redirectUrl}
            onCancelAttempt={handleCancelPaymentAttempt}
            isCancelling={isCancelling}
          />
        )}

        {/* View 1: Active Pending Order (Countdown & Post-Checkout State) */}
        {pendingOrder ? (
          <div className="space-y-6">
            <PendingOrderCountdown
              orderId={pendingOrder.orderId}
              expiresAt={pendingOrder.expiresAt}
              serverTime={pendingOrder.serverTime}
              onCancel={handleCancelOrder}
              onExpire={handleOrderExpire}
              isCancelling={isCancelling}
            />

            <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm space-y-4">
              <h3 className="text-base font-bold text-gray-900 border-b border-gray-100 pb-3">
                Detalle de la reserva
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-500 block text-xs">Modalidad:</span>
                  <span className="font-semibold text-gray-900">
                    {pendingOrder.fulfillmentMode === 'IMMEDIATE'
                      ? 'Lo antes posible'
                      : 'Turno programado'}
                  </span>
                </div>
                <div>
                  <span className="text-gray-500 block text-xs">Día y Franja:</span>
                  <span className="font-semibold text-gray-900">
                    {pendingOrder.pickupDate} ({pendingOrder.timeSlotStart.slice(0, 5)} - {pendingOrder.timeSlotEnd.slice(0, 5)})
                  </span>
                </div>
                <div>
                  <span className="text-gray-500 block text-xs">Importe Total:</span>
                  <span className="font-semibold text-emerald-700 text-base">
                    ${pendingOrder.totalAmount.toFixed(2)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        ) : items.length === 0 ? (
          /* Empty Cart State */
          <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center shadow-sm space-y-4">
            <div className="inline-flex p-4 rounded-full bg-gray-100 text-gray-400">
              <ShoppingBag className="w-10 h-10" />
            </div>
            <h3 className="text-lg font-bold text-gray-900">Tu carrito está vacío</h3>
            <p className="text-sm text-gray-500 max-w-sm mx-auto">
              No tenés platos seleccionados para continuar con la compra.
            </p>
            <div className="pt-2">
              <button
                type="button"
                onClick={() => navigate('/catalog')}
                className="px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-700 transition-colors shadow-sm"
              >
                Explorar carta
              </button>
            </div>
          </div>
        ) : (
          /* View 2: Checkout Selection & Configuration */
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 space-y-6">
              {/* Step 1: Fulfillment Mode Selection */}
              <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm">
                <FulfillmentModeSelector
                  selectedMode={fulfillmentMode}
                  onSelectMode={setFulfillmentMode}
                />
              </div>

              {/* Step 2: Scheduled Time Slots (only when SCHEDULED) */}
              {fulfillmentMode === 'SCHEDULED' && (
                <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm space-y-6 animate-fade-in">
                  <OperatingDatePicker
                    operatingDays={operatingDays}
                    selectedDate={selectedDate || ''}
                    onSelectDate={(date) => {
                      setSelectedDate(date);
                      setSelectedSlot(null);
                    }}
                  />

                  {selectedDate && (
                    <TimeSlotGrid
                      slots={slots}
                      selectedSlot={selectedSlot}
                      onSelectSlot={setSelectedSlot}
                      isLoading={isLoadingSlots}
                    />
                  )}
                </div>
              )}
            </div>

            {/* Right Column: Checkout Summary & Payment Action */}
            <div className="space-y-6">
              <CheckoutSummary
                items={items}
                fulfillmentMode={fulfillmentMode}
                selectedDate={selectedDate}
                selectedSlot={selectedSlot}
                totalAmount={totalAmount}
              />

              <button
                type="button"
                onClick={handlePayWithMercadoPago}
                disabled={!isFormValid || isSubmitting || Boolean(inProcessPayment)}
                className={`w-full py-3.5 px-6 rounded-xl font-bold text-white text-base shadow-md transition-all flex items-center justify-center gap-2 ${
                  !isFormValid || isSubmitting || Boolean(inProcessPayment)
                    ? 'bg-gray-300 cursor-not-allowed shadow-none'
                    : 'bg-sky-600 hover:bg-sky-700 active:bg-sky-800 cursor-pointer hover:shadow-lg'
                }`}
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw className="w-5 h-5 animate-spin" />
                    <span>Iniciando Mercado Pago...</span>
                  </>
                ) : (
                  <span>Pagar con Mercado Pago</span>
                )}
              </button>
            </div>
          </div>
        )}

        {/* 409 Conflict Recovery Modal */}
        <ConflictNotificationModal
          isOpen={conflictModal.isOpen}
          errorCode={conflictModal.errorCode}
          errorMessage={conflictModal.errorMessage}
          onClose={() => setConflictModal((prev) => ({ ...prev, isOpen: false }))}
          onRefreshSlots={() => {
            refreshSlots();
            setSelectedSlot(null);
          }}
          onViewExistingOrder={() => {
            setConflictModal((prev) => ({ ...prev, isOpen: false }));
          }}
        />
      </div>
    </WorkspaceLayout>
  );
};
