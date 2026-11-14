import React, { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import { ArrowLeft, ShoppingBag, RefreshCw, CheckCircle2 } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { FulfillmentModeSelector } from '../components/checkout/FulfillmentModeSelector';
import { OperatingDatePicker, OperatingDayItem } from '../components/checkout/OperatingDatePicker';
import { TimeSlotGrid } from '../components/checkout/TimeSlotGrid';
import { CheckoutSummary } from '../components/checkout/CheckoutSummary';
import { ConflictNotificationModal } from '../components/checkout/ConflictNotificationModal';
import { ConcurrencyAlertModal } from '../components/checkout/ConcurrencyAlertModal';
import { PendingOrderCountdown } from '../components/checkout/PendingOrderCountdown';
import { InProcessPaymentPanel } from '../components/checkout/InProcessPaymentPanel';
import { PaymentAlertBanner } from '../components/checkout/PaymentAlertBanner';
import { useCart } from '../context/CartContext';
import { useCheckoutFulfillment } from '../hooks/useCheckoutFulfillment';
import { useTimeSlotPolling } from '../hooks/useTimeSlotPolling';
import { cancelOrder, createOrder, getMyOrders } from '../api/orderApi';
import { initiatePayment, cancelPayment, getOrderPaymentStatus } from '../api/paymentApi';
import { confirmZeroAmountOrder } from '../api/checkoutApi';
import { DinerOrderSummaryResponse, OrderDetailResponse } from '../types/order';
import { PaymentInitiationRequest, OrderPaymentStatusResponse, PaymentMethod } from '../types/payment';

/**
 * Formats a local Date object into a YYYY-MM-DD string without UTC timezone shift.
 */
export function formatDateToIsoString(d: Date): string {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * Parses a YYYY-MM-DD string into a local Date object without UTC timezone conversion.
 */
export function parseLocalDate(dateStr: string): Date {
  const [year, month, day] = dateStr.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/**
 * Generates the weekly operating calendar structured strictly starting on Monday (ISO 8601: Lun, Mar, Mié, Jue, Vie, Sáb, Dom).
 * Restricts the view to exactly the 7 days of the current week up to Sunday (FR-006, C-12).
 * Past days within the week are marked with isOperating = false (disabled).
 */
export function generateWeeklyOperatingDays(referenceDate: Date = new Date()): OperatingDayItem[] {
  const days: OperatingDayItem[] = [];
  const dayNames = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
  const now = new Date(referenceDate);
  now.setHours(0, 0, 0, 0);

  // ISO 8601: Monday is first day of the week (day 1), Sunday is 0
  const dayOfWeek = now.getDay();
  const diffToMonday = (dayOfWeek + 6) % 7; // Monday -> 0, Tuesday -> 1, ..., Sunday -> 6

  const monday = new Date(now);
  monday.setDate(now.getDate() - diffToMonday);
  monday.setHours(0, 0, 0, 0);

  const todayStr = formatDateToIsoString(now);

  // Generate exactly 7 days of the same week (from Monday to Sunday)
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    const dateStr = formatDateToIsoString(d);

    const isPast = dateStr < todayStr;

    days.push({
      date: dateStr,
      dayName: dayNames[d.getDay()],
      dayNumber: d.getDate(),
      isOperating: !isPast,
    });
  }

  return days;
}

export const CheckoutPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const { items, totalAmount, clearCart } = useCart();

  const establishmentId = useMemo(() => {
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

    return searchParams.get('establishmentId') || items[0]?.tenantId || fromActiveTenant || '';
  }, [searchParams, items]);

  const operatingDays = useMemo(() => generateWeeklyOperatingDays(), []);

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

  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<PaymentMethod>('MERCADO_PAGO');
  const [pendingOrder, setPendingOrder] = useState<OrderDetailResponse | null>(null);
  const [inProcessPayment, setInProcessPayment] = useState<OrderPaymentStatusResponse | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isCancelling, setIsCancelling] = useState<boolean>(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const inProcessPanelRef = useRef<HTMLDivElement | null>(null);

  const buildFallbackInProcessStatus = useCallback(
    (orderId: string, fallbackSummary?: DinerOrderSummaryResponse): OrderPaymentStatusResponse => ({
      orderId,
      orderStatus: fallbackSummary?.status ?? 'PENDING_PAYMENT',
      reservation: null,
      payment: fallbackSummary
        ? {
            id: orderId,
            status: 'IN_PROCESS',
            statusDetail: null,
            redirectUrl: fallbackSummary.redirectUrl,
            sandbox: false,
          }
        : null,
      fulfillment: {
        mode: fallbackSummary?.fulfillmentMode ?? 'IMMEDIATE',
        pickupDate: fallbackSummary?.pickupDate ?? null,
        timeSlotStart: fallbackSummary?.timeSlotStart ?? null,
        timeSlotEnd: fallbackSummary?.timeSlotEnd ?? null,
      },
      totalAmount: fallbackSummary?.totalAmount ?? totalAmount,
      currency: 'ARS',
      serverTime: new Date().toISOString(),
    }),
    [totalAmount]
  );

  const fetchInProcessPaymentByOrderId = useCallback(
    async (orderId: string, fallbackSummary?: DinerOrderSummaryResponse) => {
      try {
        const statusRes = await getOrderPaymentStatus(orderId);
        setInProcessPayment(statusRes);
      } catch {
        setInProcessPayment(buildFallbackInProcessStatus(orderId, fallbackSummary));
      }
    },
    [buildFallbackInProcessStatus]
  );

  const refreshActivePendingOrder = useCallback(async () => {
    try {
      const orders = await getMyOrders();
      const pending = Array.isArray(orders)
        ? orders.find((o) => o.status === 'PENDING_PAYMENT')
        : undefined;
      if (!pending) {
        setInProcessPayment(null);
        return;
      }
      await fetchInProcessPaymentByOrderId(pending.orderId, pending);
    } catch {
      // No bloquear checkout si no se pudo consultar el historial al montar
    }
  }, [fetchInProcessPaymentByOrderId]);

  useEffect(() => {
    refreshActivePendingOrder();
  }, [refreshActivePendingOrder]);

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

  const [concurrencyModal, setConcurrencyModal] = useState<{
    isOpen: boolean;
    type: 'INSUFFICIENT_STOCK' | 'SLOT_CAPACITY_EXCEEDED';
    message: string;
    unavailableDishes?: any[];
  }>({
    isOpen: false,
    type: 'INSUFFICIENT_STOCK',
    message: '',
    unavailableDishes: [],
  });

  const isZeroAmount = useMemo(() => {
    return totalAmount === 0 && items.length > 0;
  }, [totalAmount, items]);

  const isFormValid = useMemo(() => {
    if (items.length === 0) return false;
    if (!fulfillmentMode) return false;
    if (fulfillmentMode === 'SCHEDULED' && (!selectedDate || !selectedSlot)) return false;
    if (!isZeroAmount && !selectedPaymentMethod) return false;
    return true;
  }, [items, fulfillmentMode, selectedDate, selectedSlot, selectedPaymentMethod, isZeroAmount]);

  const handle409Conflict = async (data: any, allowSlotRefresh: boolean) => {
    const code = data?.code;

    if (code === 'INSUFFICIENT_STOCK') {
      setConcurrencyModal({
        isOpen: true,
        type: 'INSUFFICIENT_STOCK',
        message:
          data?.detail ||
          'Los insumos de uno o más platos se agotaron por alta demanda concurrente.',
        unavailableDishes: data?.unavailableDishes || [],
      });
      return;
    }

    if (code === 'SLOT_CAPACITY_EXCEEDED') {
      setConcurrencyModal({
        isOpen: true,
        type: 'SLOT_CAPACITY_EXCEEDED',
        message: data?.detail || 'La franja horaria seleccionada acaba de agotarse.',
      });
      return;
    }

    if (
      (code === 'PAYMENT_IN_PROCESS' || code === 'ACTIVE_RESERVATION_EXISTS') &&
      data?.orderId
    ) {
      await fetchInProcessPaymentByOrderId(data.orderId);
    }

    setConflictModal({
      isOpen: true,
      errorCode: code || 'SLOT_SOLD_OUT',
      errorMessage:
        data?.detail ||
        data?.message ||
        'La franja horaria seleccionada acaba de agotarse o ya tenés un pago en curso.',
    });

    if (allowSlotRefresh && (code === 'SLOT_SOLD_OUT' || !code)) {
      refreshSlots();
      setSelectedSlot(null);
    }
  };

  // Handler for Confirmar pedido / Checkout Payment (T035, AS-1, AS-5, FR-001, FR-002)
  const handleConfirmOrder = async () => {
    if (!isFormValid || !establishmentId || isSubmitting || inProcessPayment) return;

    setIsSubmitting(true);
    setSubmissionError(null);
    setInfoMessage(null);

    // Flujo especial para orden bonificada $0 (AS-4, FR-005)
    if (isZeroAmount) {
      try {
        const orderPayload = {
          establishmentId,
          fulfillmentMode: fulfillmentMode!,
          pickupDate: fulfillmentMode === 'SCHEDULED' ? selectedDate : undefined,
          timeSlotStart: fulfillmentMode === 'SCHEDULED' ? selectedSlot?.startTime : undefined,
          timeSlotEnd: fulfillmentMode === 'SCHEDULED' ? selectedSlot?.endTime : undefined,
          items: items.map((i) => ({
            dishId: i.dishId,
            quantity: i.quantity,
          })),
        };

        const createdOrder = await createOrder(orderPayload as any);
        const zeroResponse = await confirmZeroAmountOrder({ orderId: createdOrder.orderId });

        clearCart();
        navigate(`/checkout/confirmacion/${zeroResponse.orderId}`, {
          state: { message: zeroResponse.message },
        });
      } catch (err: any) {
        const status = err?.response?.status;
        const data = err?.response?.data;

        if (status === 409) {
          await handle409Conflict(data, false);
          return;
        }
        setSubmissionError(data?.detail || data?.message || 'Error al confirmar orden bonificada.');
      } finally {
        setIsSubmitting(false);
      }
      return;
    }

    const payload: PaymentInitiationRequest = {
      establishmentId,
      fulfillmentMode: fulfillmentMode!,
      paymentMethod: selectedPaymentMethod,
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

      // Redirect immediately to Mercado Pago init_point when available
      if (response.redirectUrl && response.redirectUrl.trim().length > 0) {
        window.location.href = response.redirectUrl.trim();
      } else {
        setSubmissionError(
          'No pudimos obtener el enlace de pago de Mercado Pago. Podés cancelar el intento activo y volver a intentarlo.'
        );
        if (response.orderId) {
          await fetchInProcessPaymentByOrderId(response.orderId);
        }
      }
    } catch (err: any) {
      const status = err?.response?.status;
      const data = err?.response?.data;

      if (status === 409) {
        await handle409Conflict(data, true);
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
      await refreshActivePendingOrder();
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
          onClick={() => navigate('/catalogo')}
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

        {/* Panel de pago en curso (AS-5, FR-010, Q3, US3) */}
        {inProcessPayment && (
          <div ref={inProcessPanelRef} tabIndex={-1} className="outline-none">
            <InProcessPaymentPanel
              redirectUrl={inProcessPayment.payment?.redirectUrl}
              onCancelAttempt={handleCancelPaymentAttempt}
              isCancelling={isCancelling}
            />
          </div>
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
        ) : items.length === 0 && !inProcessPayment ? (
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
                onClick={() => navigate('/catalogo')}
                className="px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-700 transition-colors shadow-sm"
              >
                Explorar carta
              </button>
            </div>
          </div>
        ) : items.length > 0 ? (
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

              {/* Step 3: Payment Method Selection */}
              {!isZeroAmount && (
                <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm space-y-4">
                  <div>
                    <h3 className="text-base font-semibold text-gray-900">
                      Método de pago
                    </h3>
                    <p className="text-xs text-gray-500 mt-0.5">
                      Seleccioná la plataforma para realizar el pago online seguro
                    </p>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3" role="radiogroup" aria-label="Método de pago">
                    <div
                      onClick={() => setSelectedPaymentMethod('MERCADO_PAGO')}
                      className={`relative flex items-center justify-between p-4 rounded-xl border-2 cursor-pointer transition-all ${
                        selectedPaymentMethod === 'MERCADO_PAGO'
                          ? 'border-sky-500 bg-sky-50/50 shadow-sm'
                          : 'border-gray-200 hover:border-gray-300 bg-white'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-lg bg-sky-100 flex items-center justify-center font-bold text-sky-600 text-xs shrink-0">
                          MP
                        </div>
                        <div>
                          <div className="font-semibold text-gray-900 text-sm">Mercado Pago</div>
                          <div className="text-xs text-gray-500">Checkout Pro / Sandbox</div>
                        </div>
                      </div>
                      {selectedPaymentMethod === 'MERCADO_PAGO' && (
                        <CheckCircle2 className="w-5 h-5 text-sky-600 shrink-0" />
                      )}
                    </div>
                  </div>
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
                onClick={handleConfirmOrder}
                disabled={!isFormValid || isSubmitting || Boolean(inProcessPayment)}
                aria-busy={isSubmitting}
                className={`w-full py-3.5 px-6 rounded-xl font-bold text-white text-base shadow-md transition-all flex items-center justify-center gap-2 ${
                  !isFormValid || isSubmitting || Boolean(inProcessPayment)
                    ? 'bg-gray-300 opacity-60 cursor-not-allowed pointer-events-none shadow-none'
                    : isZeroAmount
                    ? 'bg-[#c9a96e] text-[#0b0b0d] hover:bg-[#d8ba7f] cursor-pointer hover:shadow-lg'
                    : 'bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 cursor-pointer hover:shadow-lg'
                }`}
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw className="w-5 h-5 animate-spin" />
                    <span>{isZeroAmount ? 'Confirmando...' : 'Iniciando pago...'}</span>
                  </>
                ) : (
                  <span>{isZeroAmount ? 'Confirmar pedido bonificado ($0)' : 'Confirmar pedido'}</span>
                )}
              </button>
            </div>
          </div>
        ) : null}

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
            const panelContainer = inProcessPanelRef.current;
            if (panelContainer) {
              panelContainer.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
              panelContainer.focus();
            }
          }}
        />

        {/* Modal de Concurrencia (Agotamiento de Insumos y Saturación de Franjas) */}
        <ConcurrencyAlertModal
          isOpen={concurrencyModal.isOpen}
          type={concurrencyModal.type}
          message={concurrencyModal.message}
          unavailableDishes={concurrencyModal.unavailableDishes}
          onClose={() => setConcurrencyModal((prev) => ({ ...prev, isOpen: false }))}
          onAction={() => {
            setConcurrencyModal((prev) => ({ ...prev, isOpen: false }));
            if (concurrencyModal.type === 'INSUFFICIENT_STOCK') {
              // Mantener carrito y permitir modificar
            } else {
              refreshSlots();
              setSelectedSlot(null);
            }
          }}
        />
      </div>
    </WorkspaceLayout>
  );
};
