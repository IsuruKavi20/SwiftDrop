import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { MerchantApiError } from './merchantApi';

export interface MerchantClient {
  clientId: string;
  name: string;
  createdAt: string;
}

export interface CreatedMerchantClient extends MerchantClient {
  clientSecret: string;
}

export interface CreateClientInput {
  name: string;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.authUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init.headers },
    });
  } catch {
    throw new MerchantApiError(0, `Cannot reach the auth service at ${env.authUrl}. Is the service running?`);
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as Record<string, unknown> | null;

    const fieldErrors: Record<string, string> = {};
    if (problem && typeof problem.errors === 'object' && problem.errors !== null) {
      for (const [key, val] of Object.entries(problem.errors as Record<string, unknown>)) {
        if (typeof val === 'string') {
          fieldErrors[key] = val;
        }
      }
    }

    const message =
      (typeof problem?.detail === 'string' && problem.detail.trim().length > 0 ? problem.detail : null) ??
      (typeof problem?.title === 'string' && problem.title.trim().length > 0 ? problem.title : null) ??
      (typeof problem?.message === 'string' && problem.message.trim().length > 0 ? problem.message : null) ??
      (response.status === 403
        ? 'Access denied. You do not have permission to manage API credentials.'
        : response.status === 401
          ? 'Your session has expired. Please log in again.'
          : response.status === 400
            ? 'Invalid client information provided.'
            : 'Something went wrong. Please try again.');

    throw new MerchantApiError(response.status, message, fieldErrors);
  }

  return (response.status === 204 ? undefined : await response.json()) as T;
}

/** Lists all API clients/credentials for the merchant's software. */
export function getMerchantClients(): Promise<MerchantClient[]> {
  return request<MerchantClient[]>('/api/v1/merchants/me/clients');
}

/**
 * Creates a new API client credential.
 * The returned object contains `clientSecret` which is only revealed once.
 */
export function createMerchantClient(input: CreateClientInput): Promise<CreatedMerchantClient> {
  return request<CreatedMerchantClient>('/api/v1/merchants/me/clients', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** Revokes/deletes an API client credential. */
export function deleteMerchantClient(clientId: string): Promise<void> {
  return request<void>(`/api/v1/merchants/me/clients/${encodeURIComponent(clientId)}`, {
    method: 'DELETE',
  });
}
