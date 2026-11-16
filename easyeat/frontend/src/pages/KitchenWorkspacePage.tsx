import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { authApi } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';
import { chefReviewFeedbackApi } from '../api/chefReviewFeedbackApi';
import { orderTrackingApi } from '../api/orderTrackingApi';
import {
  fetchDashboardSummary,
  fetchKitchenOrders,
  type KitchenOrderPageResponse,
} from '../api/kitchenKdsApi';
import { DishRejectionFeedbackBanner } from '../components/chef/DishRejectionFeedbackBanner';
import type { ChefReviewFeedbackEntry } from '../types/dishReview';
import type { KdsDashboardSummaryResponse } from '../types/kds';
import { Clock, CheckCircle2, AlertTriangle, Send } from 'lucide-react';

type ActiveKitchenTicket = KitchenOrderPageResponse['content'][number];

const formatSlotBoundary = (timeValue: string): string => {
  if (!timeValue) return '';
  return timeValue.length >= 5 ? timeValue.slice(0, 5) : timeValue;
};

export const KitchenWorkspacePage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const returnedDishId = searchParams.get('dishId');
  const [feedbackEntries, setFeedbackEntries] = useState<ChefReviewFeedbackEntry[]>([]);
  const { user } = useAuth();

  // KDS delay state
  const [currentDelay, setCurrentDelay] = useState<number>(0);
  const [isUpdatingDelay, setIsUpdatingDelay] = useState<boolean>(false);
  const [delaySuccessMsg, setDelaySuccessMsg] = useState<string | null>(null);

  // KDS ticket completion state
  const [ticketIdInput, setTicketIdInput] = useState<string>('');
  const [isCompletingTicket, setIsCompletingTicket] = useState<boolean>(false);
  const [completingTicketIds, setCompletingTicketIds] = useState<Set<string>>(new Set());
  const [ticketSuccessMsg, setTicketSuccessMsg] = useState<string | null>(null);
  const [ticketErrorMsg, setTicketErrorMsg] = useState<string | null>(null);

  // Column 2 active preparation tickets (Spec 020 - US1, RF-001..RF-004)
  const [inPreparationTickets, setInPreparationTickets] = useState<ActiveKitchenTicket[]>([]);

  // KDS live dashboard metrics (T027, Contract 07)
  const [kdsSummary, setKdsSummary] = useState<KdsDashboardSummaryResponse>({
    queuedOrdersCount: 0,
    inPreparationOrdersCount: 0,
    readyForPickupOrdersCount: 0,
    completedTodayCount: 0,
  });

  useEffect(() => {
    authApi.me().catch(() => { /* handled by interceptor */ });
  }, []);

  useEffect(() => {
    if (!returnedDishId) {
      setFeedbackEntries([]);
      return;
    }
    chefReviewFeedbackApi.getFeedback(returnedDishId)
      .then((feedback) => setFeedbackEntries(feedback.entries))
      .catch(() => setFeedbackEntries([]));
  }, [returnedDishId]);

  useEffect(() => {
    const loadKdsData = async () => {
      try {
        const summary = await fetchDashboardSummary();
        if (summary) {
          setKdsSummary(summary);
        }
      } catch {
        // non-blocking fallback
      }

      try {
        const page = await fetchKitchenOrders('IN_PREPARATION');
        if (page && Array.isArray(page.content)) {
          setInPreparationTickets(
            page.content.filter((t) => t.status === 'IN_PREPARATION')
          );
        }
      } catch {
        // non-blocking fallback
      }
    };

    loadKdsData();
    const interval = setInterval(loadKdsData, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleUpdateDelay = async (minutes: number) => {
    const establishmentId = user?.tenantId;
    if (!establishmentId) {
      setDelaySuccessMsg('No se detectó el identificador del establecimiento asociado.');
      return;
    }

    setIsUpdatingDelay(true);
    setDelaySuccessMsg(null);

    try {
      const res = await orderTrackingApi.adjustKitchenDelay({
        establishmentId,
        globalDelayMinutes: minutes,
      });
      setCurrentDelay(res.globalDelayMinutes);
      setDelaySuccessMsg(
        minutes === 0
          ? 'Demora global restablecida a 0 min (operación normal).'
          : `Demora global ajustada a +${minutes} min.`
      );
    } catch (err) {
      console.error('Error updating kitchen delay:', err);
      setDelaySuccessMsg('Error al actualizar la demora en el sistema.');
    } finally {
      setIsUpdatingDelay(false);
    }
  };

  const handleDispatchTicketFromCard = async (ticketId: string) => {
    if (!ticketId || completingTicketIds.has(ticketId)) return;

    setCompletingTicketIds((prev) => new Set(prev).add(ticketId));
    setTicketErrorMsg(null);

    try {
      const res = await orderTrackingApi.markTicketReadyForPickup(ticketId);
      // RF-004 / AC-3 / Invariante 4: immediate removal from Column 2 upon confirmation
      setInPreparationTickets((prev) =>
        prev.filter((ticket) => ticket.ticketId !== ticketId)
      );
      setKdsSummary((prev) => ({
        ...prev,
        inPreparationOrdersCount: Math.max(0, prev.inPreparationOrdersCount - 1),
        readyForPickupOrdersCount: prev.readyForPickupOrdersCount + 1,
      }));
      setTicketSuccessMsg(
        `¡Pedido #${res.ticketNumber} marcado como LISTO PARA RETIRAR en mostrador!`
      );
    } catch (err: any) {
      setTicketErrorMsg(
        err?.response?.data?.detail || 'No se pudo finalizar la comanda seleccionada.'
      );
    } finally {
      setCompletingTicketIds((prev) => {
        const next = new Set(prev);
        next.delete(ticketId);
        return next;
      });
    }
  };

  const handleMarkReadyForPickup = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetId = ticketIdInput.trim();
    if (!targetId) return;

    setIsCompletingTicket(true);
    setTicketSuccessMsg(null);
    setTicketErrorMsg(null);

    try {
      const res = await orderTrackingApi.markTicketReadyForPickup(targetId);
      setInPreparationTickets((prev) =>
        prev.filter((ticket) => ticket.ticketId !== targetId && ticket.orderId !== targetId)
      );
      setTicketSuccessMsg(
        `¡Pedido #${res.ticketNumber} marcado como LISTO PARA RETIRAR en mostrador!`
      );
      setTicketIdInput('');
    } catch (err: any) {
      console.error('Error marking ticket ready for pickup:', err);
      setTicketErrorMsg(
        err?.response?.data?.detail || 'No se encontró la comanda con el ID ingresado.'
      );
    } finally {
      setIsCompletingTicket(false);
    }
  };

  return (
    <WorkspaceLayout
      title="Kitchen Display System (KDS)"
      subtitle="Comandas en preparación, demoras y despachos de cocina"
    >
      {feedbackEntries.length > 0 && (
        <div className="mb-6">
          <DishRejectionFeedbackBanner entries={feedbackEntries} />
        </div>
      )}

      {/* Columna 2: Comandas en Preparación Activa (Spec 020 - US1, RF-001..RF-004) */}
      <section
        data-testid="kds-column-in-preparation"
        aria-label="Columna 2: En preparación activa"
        className="bg-white p-6 rounded-2xl shadow-sm border border-gray-200 mb-8"
      >
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-blue-600" />
            <h3 className="text-base font-bold text-gray-900">
              Columna 2 — Comandas en preparación activa
            </h3>
          </div>
          <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-50 text-blue-700 border border-blue-200">
            {inPreparationTickets.length} activas
          </span>
        </div>

        {inPreparationTickets.length === 0 ? (
          <p className="text-xs text-gray-500 py-4 text-center">
            Sin pedidos en preparación activa en este momento.
          </p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {inPreparationTickets.map((ticket) => {
              const isDispatching = completingTicketIds.has(ticket.ticketId);
              const slotRange = `${formatSlotBoundary(ticket.timeSlotStart)} - ${formatSlotBoundary(ticket.timeSlotEnd)}`;

              return (
                <article
                  key={ticket.ticketId}
                  data-testid={`kds-ticket-card-${ticket.ticketId}`}
                  className="p-4 rounded-xl border border-blue-200 bg-blue-50/30 flex flex-col justify-between gap-4 shadow-sm"
                >
                  <div>
                    <div className="flex items-center justify-between border-b border-blue-100 pb-2 mb-3">
                      <span className="text-lg font-extrabold text-gray-900">
                        #{ticket.ticketNumber}
                      </span>
                      <span className="text-xs font-semibold text-blue-800 bg-blue-100 px-2.5 py-1 rounded-lg">
                        {slotRange}
                      </span>
                    </div>

                    <ul className="space-y-1.5 text-sm text-gray-800 font-medium">
                      {ticket.items.map((item, idx) => (
                        <li key={`${ticket.ticketId}-${item.dishId}-${idx}`}>
                          {item.quantity}x {item.dishName}
                        </li>
                      ))}
                    </ul>
                  </div>

                  <button
                    type="button"
                    disabled={isDispatching}
                    onClick={() => handleDispatchTicketFromCard(ticket.ticketId)}
                    className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-sm font-extrabold uppercase tracking-wide rounded-xl shadow-sm transition-colors"
                  >
                    LISTO
                  </button>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {/* Operational Controls: Delay & Dispatch */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Global Delay Modifier (FR-007, AS-7, INV-007) */}
        <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-200 flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center space-x-2 text-gray-900 font-bold mb-1">
              <Clock className="w-5 h-5 text-amber-600" />
              <h3 className="text-base">Ajuste de Demora Global de Cocina</h3>
            </div>
            <p className="text-xs text-gray-500">
              Modificá la demora adicional informada a los comensales en vivo ante picos de demanda.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {[0, 5, 10, 15, 20].map((mins) => (
              <button
                key={mins}
                type="button"
                disabled={isUpdatingDelay}
                onClick={() => handleUpdateDelay(mins)}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                  currentDelay === mins
                    ? 'bg-amber-600 text-white shadow-md'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {mins === 0 ? 'Sin demora' : `+${mins} min`}
              </button>
            ))}
          </div>

          {delaySuccessMsg && (
            <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
              <span>{delaySuccessMsg}</span>
            </div>
          )}
        </div>

        {/* Counter Dispatch: Ready for Pickup (FR-009, AS-8, INV-008) */}
        <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-200 flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center space-x-2 text-gray-900 font-bold mb-1">
              <CheckCircle2 className="w-5 h-5 text-emerald-600" />
              <h3 className="text-base">Despacho a Mostrador (Listo para Retirar)</h3>
            </div>
            <p className="text-xs text-gray-500">
              Ingresá el UUID de la orden para notificar al comensal y llamar a mostrador.
            </p>
          </div>

          <form onSubmit={handleMarkReadyForPickup} className="flex gap-2">
            <input
              type="text"
              placeholder="UUID de la comanda/orden..."
              value={ticketIdInput}
              onChange={(e) => setTicketIdInput(e.target.value)}
              className="flex-1 px-3 py-2 text-xs border border-gray-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
            <button
              type="submit"
              disabled={isCompletingTicket || !ticketIdInput.trim()}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-xs font-bold rounded-xl shadow-sm transition-colors flex items-center gap-1.5"
            >
              <Send className="w-3.5 h-3.5" />
              <span>Listo</span>
            </button>
          </form>

          {ticketSuccessMsg && (
            <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>{ticketSuccessMsg}</span>
            </div>
          )}

          {ticketErrorMsg && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-red-600 shrink-0" />
              <span>{ticketErrorMsg}</span>
            </div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 mb-6">
        <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200 flex flex-col justify-between gap-3">
          <div>
            <h4 className="text-sm font-semibold text-gray-900">Catálogo de Ingredientes y Materias Primas</h4>
            <p className="text-xs text-gray-500 mt-0.5">
              Consultá la disponibilidad de stock de insumos en tiempo real para la elaboración de platos.
            </p>
          </div>
          <Link
            to="/cocina/ingredientes"
            className="inline-flex items-center justify-center px-4 py-2 bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold rounded-md shadow-sm transition-colors whitespace-nowrap self-start"
          >
            Ver Ingredientes →
          </Link>
        </div>
      </div>

      {/* KDS 4 Status Cards (T027, Contract 07) */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-amber-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-amber-600">En Cola</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.queuedOrdersCount} comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-blue-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-blue-600">En Preparación</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.inPreparationOrdersCount} comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-green-500 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-green-600">Para Retirar</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.readyForPickupOrdersCount} comandas</p>
        </div>

        <div className="bg-white p-5 rounded-lg shadow-sm border-l-4 border-l-gray-400 border border-gray-200">
          <span className="text-xs font-bold uppercase tracking-wider text-gray-600">Completadas hoy</span>
          <p className="text-2xl font-bold text-gray-900 mt-2">{kdsSummary.completedTodayCount}</p>
        </div>
      </div>
    </WorkspaceLayout>
  );
};

export default KitchenWorkspacePage;
