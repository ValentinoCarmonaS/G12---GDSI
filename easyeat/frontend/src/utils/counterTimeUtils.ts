/**
 * Timing and wait-duration formatting utilities for Counter Dispatch (Spec 020).
 * Implements RF-007, RF-008 and Invariante 8 (monotonic integer minutes from ready_at).
 */

const MILLISECONDS_PER_MINUTE = 60_000;

/**
 * Calculates whole minutes elapsed since `readyAtIso`, compensating for server/client clock skew.
 *
 * @param readyAtIso ISO-8601 timestamp string of when kitchen marked the ticket ready.
 * @param serverOffsetMs Optional clock skew offset in milliseconds (`serverTimeMs - clientTimeMs`).
 * @param clientNowMs Optional client reference timestamp in milliseconds (defaults to `Date.now()`).
 * @returns Non-negative integer minutes elapsed (`0` during seconds 0..59, `1` at 60s, etc.).
 */
export const calculateWaitMinutes = (
  readyAtIso: string,
  serverOffsetMs: number = 0,
  clientNowMs: number = Date.now()
): number => {
  const readyAtMs = Date.parse(readyAtIso);
  if (Number.isNaN(readyAtMs)) {
    return 0;
  }

  const effectiveNowMs = clientNowMs + serverOffsetMs;
  const elapsedMs = Math.max(0, effectiveNowMs - readyAtMs);
  return Math.floor(elapsedMs / MILLISECONDS_PER_MINUTE);
};

/**
 * Formats integer wait minutes deterministically as `${minutes} min`.
 */
export const formatWaitMinutes = (minutes: number): string => {
  const normalizedMinutes = Number.isFinite(minutes)
    ? Math.max(0, Math.floor(minutes))
    : 0;
  return `${normalizedMinutes} min`;
};
