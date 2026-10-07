import type { InvestigationRequest } from './api/types';

export interface FormValues {
  query: string;
  /** `datetime-local` value, e.g. 2026-08-24T14:25. No offset. */
  since: string;
  until: string;
  /** Comma separated, as typed. */
  services: string;
}

/**
 * Turns form input into the API request. `datetime-local` has no offset and the server
 * reads a bare local time in its own zone, so the browser's offset is sent explicitly
 * rather than letting the two sides disagree silently.
 */
export function buildRequest(
  values: FormValues,
  fallbackQuery: string,
  offsetMinutes: number = -new Date().getTimezoneOffset(),
): InvestigationRequest {
  const request: InvestigationRequest = { query: values.query.trim() || fallbackQuery };
  const offset = formatOffset(offsetMinutes);
  if (values.since) request.since = `${values.since}:00${offset}`;
  if (values.until) request.until = `${values.until}:00${offset}`;
  const services = values.services
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  if (services.length) request.services = services;
  return request;
}

export function formatOffset(minutes: number): string {
  const sign = minutes < 0 ? '-' : '+';
  const abs = Math.abs(minutes);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}
