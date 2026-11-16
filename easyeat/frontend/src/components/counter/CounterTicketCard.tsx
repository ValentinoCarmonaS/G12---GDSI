import React, { useEffect, useState } from 'react';
import { Clock, ChevronDown, ChevronUp } from 'lucide-react';
import type { CounterReadyOrder } from '../../types/counter';
import {
  calculateWaitMinutes,
  formatWaitMinutes,
} from '../../utils/counterTimeUtils';

export interface CounterTicketCardProps {
  order: CounterReadyOrder;
  isExpanded: boolean;
  onToggle: (ticketId: string) => void;
  serverOffsetMs?: number;
  nowMs?: number;
}

const formatSlotBoundary = (timeValue: string): string => {
  if (!timeValue) return '';
  return timeValue.length >= 5 ? timeValue.slice(0, 5) : timeValue;
};

export const CounterTicketCard: React.FC<CounterTicketCardProps> = ({
  order,
  isExpanded,
  onToggle,
  serverOffsetMs = 0,
  nowMs,
}) => {
  const [liveNowMs, setLiveNowMs] = useState<number>(() => Date.now());
  const [avatarFailed, setAvatarFailed] = useState<boolean>(false);

  useEffect(() => {
    setAvatarFailed(false);
  }, [order.customer.photoUrl]);

  useEffect(() => {
    if (typeof nowMs === 'number') {
      return;
    }
    const timer = setInterval(() => {
      setLiveNowMs(Date.now());
    }, 10_000);
    return () => clearInterval(timer);
  }, [nowMs]);

  const effectiveNowMs = typeof nowMs === 'number' ? nowMs : liveNowMs;
  const waitMinutes = calculateWaitMinutes(
    order.readyAt,
    serverOffsetMs,
    effectiveNowMs
  );
  const formattedWait = formatWaitMinutes(waitMinutes);
  const slotRange = `${formatSlotBoundary(order.timeSlot.start)} - ${formatSlotBoundary(order.timeSlot.end)}`;

  const handleHeaderKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onToggle(order.ticketId);
    }
  };

  return (
    <article
      data-testid={`counter-ticket-card-${order.ticketId}`}
      className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden transition-shadow hover:shadow-md"
    >
      {/* Closed/Header zone: bidirectional toggle (RF-006, RF-009, RF-011) */}
      <div
        data-testid="counter-card-header"
        role="button"
        tabIndex={0}
        aria-expanded={isExpanded}
        onClick={() => onToggle(order.ticketId)}
        onKeyDown={handleHeaderKeyDown}
        className="p-5 flex items-center justify-between gap-4 cursor-pointer select-none hover:bg-gray-50 transition-colors"
      >
        <div className="flex items-center gap-3">
          <span className="text-xl font-extrabold text-gray-900">
            #{order.ticketNumber}
          </span>
          <span className="text-xs font-semibold text-gray-700 bg-gray-100 px-2.5 py-1 rounded-lg border border-gray-200">
            {slotRange}
          </span>
        </div>

        <div className="flex items-center gap-2.5">
          <span
            data-testid="counter-card-wait-time"
            className="inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200"
          >
            <Clock className="w-3.5 h-3.5 text-emerald-600" />
            <span>{formattedWait}</span>
          </span>
          {isExpanded ? (
            <ChevronUp className="w-4 h-4 text-gray-500" />
          ) : (
            <ChevronDown className="w-4 h-4 text-gray-500" />
          )}
        </div>
      </div>

      {/* Expanded operational body (RF-011, RF-012, RF-013) */}
      {isExpanded && (
        <div
          data-testid="counter-card-expanded-body"
          className="px-5 pb-5 pt-3 border-t border-gray-100 space-y-4 bg-gray-50/40"
        >
          {/* Diner identity & 4-digit oral verification PIN */}
          <div className="flex items-center justify-between gap-3">
            <div
              data-testid="counter-card-customer"
              className="flex items-center gap-3 min-w-0"
            >
              {order.customer.photoUrl && !avatarFailed && (
                <img
                  src={order.customer.photoUrl}
                  alt={order.customer.fullName}
                  data-testid="counter-card-customer-avatar"
                  onError={() => setAvatarFailed(true)}
                  className="w-10 h-10 rounded-full object-cover border border-gray-200 shrink-0"
                />
              )}
              <span className="text-sm font-bold text-gray-900 truncate">
                {order.customer.fullName}
              </span>
            </div>

            <div className="text-right shrink-0">
              <span className="block text-[10px] font-semibold uppercase tracking-wider text-gray-500">
                Código retiro
              </span>
              <span
                data-testid="counter-card-security-code"
                className="font-mono text-base font-extrabold tracking-widest text-gray-900 bg-amber-50 border border-amber-200 px-2.5 py-0.5 rounded-lg inline-block"
              >
                {order.securityCode}
              </span>
            </div>
          </div>

          {/* Dish breakdown [Cantidad]x [Plato] with interior click protection (RF-011, RF-012, RF-013) */}
          <ul
            data-testid="counter-card-items"
            onClick={(e) => e.stopPropagation()}
            className="space-y-1.5 py-2 px-3 bg-white rounded-xl border border-gray-200 max-h-48 overflow-y-auto text-sm text-gray-800 font-medium"
          >
            {order.items.map((item, idx) => (
              <li key={`${order.ticketId}-item-${idx}`}>
                {item.quantity}x {item.dishName}
              </li>
            ))}
          </ul>

          {/* Visual container for delivery action button with interior click protection (RF-011, RF-012) */}
          <div
            data-testid="counter-card-delivery-container"
            onClick={(e) => e.stopPropagation()}
            className="pt-1"
          >
            <button
              type="button"
              data-testid="counter-card-delivery-button"
              className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-extrabold uppercase tracking-wider rounded-xl shadow-sm transition-colors"
            >
              ENTREGAR PEDIDO
            </button>
          </div>
        </div>
      )}
    </article>
  );
};

export default CounterTicketCard;
