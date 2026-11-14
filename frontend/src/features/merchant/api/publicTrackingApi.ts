import { env } from '../../../config/env';

export interface PublicTracking {
  trackingNumber: string;
  status: string;
  fromCity: string | null;
  toCity: string | null;
  events: {
    status: string;
    description: string;
    locationName: string | null;
    eventTime: string;
  }[];
}

export async function findPublicTracking(number: string, signal: AbortSignal): Promise<PublicTracking | null> {
  // The public endpoint accepts unauthenticated requests. An expired bearer token can cause a 401 here.
  const response = await fetch(`${env.apiUrl}/api/public/tracking/${encodeURIComponent(number)}`, { signal });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Tracking lookup failed.');
  return response.json() as Promise<PublicTracking>;
}
