import React, { useState, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ShoppingBag, AlertCircle, CheckCircle, RefreshCw } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { FulfillmentModeSelector } from '../components/checkout/FulfillmentModeSelector';
import { OperatingDatePicker, OperatingDayItem } from '../components/checkout/OperatingDatePicker';
import { TimeSlotGrid } from '../components/checkout/TimeSlotGrid';
import { CheckoutSummary } from '../components/checkout/CheckoutSummary';
import { ConflictNotificationModal } from '../components/checkout/ConflictNotificationModal';
import { PendingOrderCountdown } from '../components/checkout/PendingOrderCountdown';
import { useCart } from '../context/CartContext';
import { useCheckoutFulfillment } from '../hooks/useCheckoutFulfillment';
import { useTimeSlotPolling } from '../hooks/useTimeSlotPolling';
import { createOrder, cancelOrder } from '../api/orderApi';
import { CreateOrderRequest, OrderDetailResponse } from '../types/order';

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
  const { items, totalAmount, clearCart } = useCart();

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
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isCancelling, setIsCancelling] = useState<boolean>(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [cancelledMessage, setCancelledMessage] = useState<string | null>(null);

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

  const handleConfirmOrder = async () => {
    if (!isFormValid || !establishmentId) return;

    setIsSubmitting(true);
    setSubmissionError(null);
    setCancelledMessage(null);

    const payload: CreateOrderRequest = {
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

    try {
      const order = await createOrder(payload);
      setPendingOrder(order);
      clearCart();
    } catch (err: any) {
      const status = err?.response?.status;
      const data = err?.response?.data;
      const code = data?.code;

      if (status === 409) {
        setConflictModal({
          isOpen: true,
          errorCode: code || 'SLOT_SOLD_OUT',
          errorMessage:
            data?.detail ||
            'La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.',
        });

        if (code === 'SLOT_SOLD_OUT' || !code) {
          refreshSlots();
          setSelectedSlot(null);
        }
      } else {
        setSubmissionError(
          data?.detail || 'Ocurrió un error al procesar el pedido. Por favor, intentá nuevamente.'
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancelOrder = async () => {
    if (!pendingOrder) return;
    setIsCancelling(true);
    try {
      await cancelOrder(pendingOrder.orderId);
      setPendingOrder(null);
      setCancelledMessage('Tu reserva ha sido cancelada exitosamente y el cupo fue liberado.');
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
    <WorkspaceLayout title="Checkout" subtitle="Configuración de retiro y confirmación de reserva">
      <div className="max-w-6xl mx-auto px-4 py-6 space-y-6">
        <button
          type="button"
          onClick={() => navigate('/catalog')}
          className="inline-flex items-center gap-2 text-sm font-semibold text-gray-600 hover:text-emerald-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Volver a la carta
        </button>

        {cancelledMessage && (
          <div className="flex items-center gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm">
            <CheckCircle className="w-5 h-5 flex-shrink-0 text-emerald-600" />
            <p>{cancelledMessage}</p>
          </div>
        )}

        {submissionError && (
          <div className="flex items-center gap-3 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm">
            <AlertCircle className="w-5 h-5 flex-shrink-0 text-red-600" />
            <p>{submissionError}</p>
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

            {/* Right Column: Checkout Summary & Confirmation Action */}
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
                disabled={!isFormValid || isSubmitting}
                className={`w-full py-3.5 px-6 rounded-xl font-bold text-white text-base shadow-md transition-all flex items-center justify-center gap-2 ${
                  !isFormValid || isSubmitting
                    ? 'bg-gray-300 cursor-not-allowed shadow-none'
                    : 'bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 cursor-pointer hover:shadow-lg'
                }`}
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw className="w-5 h-5 animate-spin" />
                    <span>Confirmando reserva...</span>
                  </>
                ) : (
                  <span>Confirmar pedido</span>
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
            // Can reload active order or close
            setConflictModal((prev) => ({ ...prev, isOpen: false }));
          }}
        />
      </div>
    </WorkspaceLayout>
  );
};
