import apiClient from './apiClient';
import type { CounterReadyOrder } from '../types/counter';

export interface FetchCounterOrdersOptions {
  status?: string;
  establishmentId?: string;
  signal?: AbortSignal;
  onServerDate?: (serverTimeMs: number) => void;
}

export const fetchCounterOrders = async (
  options: FetchCounterOrdersOptions = {}
): Promise<CounterReadyOrder[]> => {
  const {
    status = 'READY_FOR_PICKUP',
    establishmentId,
    signal,
    onServerDate,
  } = options;

  const params: Record<string, string> = { status };
  if (establishmentId) {
    params.establishmentId = establishmentId;
  }

  const response = await apiClient.get<CounterReadyOrder[]>('/counter/orders', {
    params,
    headers: {
      'Cache-Control': 'no-cache',
      Pragma: 'no-cache',
    },
    signal,
  });

  const serverDateHeader = response.headers?.date;
  if (onServerDate && typeof serverDateHeader === 'string') {
    const parsedTimeMs = Date.parse(serverDateHeader);
    if (!Number.isNaN(parsedTimeMs)) {
      onServerDate(parsedTimeMs);
    }
  }

  return response.data;
};

export const counterApi = {
  fetchCounterOrders,
};
