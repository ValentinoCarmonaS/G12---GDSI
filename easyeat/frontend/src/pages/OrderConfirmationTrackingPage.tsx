import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, RefreshCw, AlertCircle, Utensils } from 'lucide-react';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { OrderSuccessGreenBanner } from '../components/tracking/OrderSuccessGreenBanner';
import { LiveTrackingWidget } from '../components/tracking/LiveTrackingWidget';
import { orderTrackingApi } from '../api/orderTrackingApi';
import type { OrderConfirmationTrackingResponse } from '../types/orderTracking';

export const OrderConfirmationTrackingPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<OrderConfirmationTrackingResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchOrderDetails = async () => {
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
  };

  useEffect(() => {
    fetchOrderDetails();
  }, [orderId]);

  return (
    <WorkspaceLayout
      title="Confirmación de Pedido"
      subtitle="Detalle de compra, respaldo por correo y seguimiento en vivo"
    >
      <div className="max-w-4xl mx-auto px-4 py-8 space-y-8 animate-fade-in">
        {/* Navigation back */}
        <div className="flex items-center justify-between">
          <button
            type="button"
            onClick={() => navigate('/catalogo')}
            className="inline-flex items-center gap-2 text-sm font-semibold text-gray-600 hover:text-emerald-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Volver a la carta
          </button>
        </div>

        {/* Loading State */}
        {isLoading && (
          <div className="bg-white rounded-3xl border border-gray-100 p-16 text-center shadow-sm space-y-4">
            <RefreshCw className="w-10 h-10 animate-spin text-emerald-600 mx-auto" />
            <p className="text-gray-600 font-medium text-sm">
              Cargando confirmación del pedido...
            </p>
          </div>
        )}

        {/* Error State */}
        {!isLoading && errorMessage && (
          <div className="bg-red-50 border border-red-200 rounded-3xl p-8 text-center space-y-4 shadow-sm">
            <AlertCircle className="w-12 h-12 text-red-500 mx-auto" />
            <h3 className="text-lg font-bold text-red-900">
              No pudimos cargar la información del pedido
            </h3>
            <p className="text-sm text-red-700 max-w-md mx-auto">{errorMessage}</p>
            <div className="pt-2">
              <button
                type="button"
                onClick={fetchOrderDetails}
                className="px-6 py-2.5 rounded-xl font-semibold text-sm text-white bg-red-600 hover:bg-red-700 transition-colors shadow-sm"
              >
                Reintentar
              </button>
            </div>
          </div>
        )}

        {/* Successful Confirmation View */}
        {!isLoading && order && (
          <div className="space-y-8">
            {/* Green Success Banner (AS-1, AS-3, INV-004) */}
            <OrderSuccessGreenBanner
              ticketNumber={order.ticketNumber}
              pickupDate={order.pickupDate}
              timeSlotStart={order.timeSlotStart}
              timeSlotEnd={order.timeSlotEnd}
              establishmentName={order.establishmentName}
              fulfillmentMode={order.fulfillmentMode}
            />

            {/* Live Tracking Widget (FR-006, FR-007, FR-008, FR-009, AS-5, AS-6, AS-7, AS-8) */}
            <LiveTrackingWidget
              orderId={order.orderId}
              ticketNumber={order.ticketNumber}
              fulfillmentMode={order.fulfillmentMode}
              timeSlotStart={order.timeSlotStart}
              timeSlotEnd={order.timeSlotEnd}
            />

            {/* Email Guarantee Notice (AS-2, FR-002) */}
            <div className="p-4 rounded-2xl bg-emerald-50/80 border border-emerald-200/60 text-emerald-900 text-sm flex items-start gap-3">
              <span className="text-lg">📧</span>
              <div>
                <p className="font-bold">Comprobante emitido con respaldo por correo</p>
                <p className="text-xs text-emerald-700 mt-0.5">
                  Te enviamos el comprobante formal con los datos de retiro y el enlace directo de seguimiento a tu casilla registrada.
                </p>
              </div>
            </div>

            {/* Order Items Breakdown & Summary (AS-3) */}
            <div className="bg-white rounded-3xl border border-gray-200/80 p-6 sm:p-8 shadow-sm space-y-6">
              <div className="flex items-center justify-between border-b border-gray-100 pb-4">
                <div className="flex items-center gap-2 text-gray-900 font-bold text-lg">
                  <Utensils className="w-5 h-5 text-emerald-600" />
                  <h2>Detalle del Pedido</h2>
                </div>
                <span className="text-xs font-semibold px-3 py-1 bg-gray-100 text-gray-700 rounded-full">
                  {order.items.length} {order.items.length === 1 ? 'producto' : 'productos'}
                </span>
              </div>

              {/* Items List */}
              <div className="divide-y divide-gray-100">
                {order.items.map((item, index) => (
                  <div key={item.dishId || index} className="py-3 flex items-center justify-between text-sm">
                    <div className="flex items-center gap-3">
                      <span className="w-7 h-7 rounded-lg bg-emerald-100/70 text-emerald-800 font-bold flex items-center justify-center text-xs">
                        {item.quantity}x
                      </span>
                      <span className="font-semibold text-gray-800">{item.dishName}</span>
                    </div>
                    <div className="text-right">
                      <span className="font-bold text-gray-900">${item.subtotal.toFixed(2)}</span>
                      {item.quantity > 1 && (
                        <span className="block text-[11px] text-gray-400">
                          ${item.unitPrice.toFixed(2)} c/u
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Total Summary */}
              <div className="border-t border-gray-100 pt-4 flex items-center justify-between">
                <div>
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider block">
                    Total Abonado
                  </span>
                  <span className="text-xs text-emerald-600 font-medium">
                    Abonado mediante pasarela segura
                  </span>
                </div>
                <span className="text-2xl sm:text-3xl font-extrabold text-gray-900">
                  ${order.totalAmount.toFixed(2)}
                </span>
              </div>
            </div>

            {/* Action Bar (AS-3, FR-003) */}
            <div className="flex justify-center pt-2">
              <button
                type="button"
                onClick={() => navigate('/catalogo')}
                className="w-full sm:w-auto py-4 px-8 rounded-2xl font-bold text-white bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 transition-all text-center shadow-lg hover:shadow-xl flex items-center justify-center gap-2"
              >
                Explorar carta
              </button>
            </div>
          </div>
        )}
      </div>
    </WorkspaceLayout>
  );
};
