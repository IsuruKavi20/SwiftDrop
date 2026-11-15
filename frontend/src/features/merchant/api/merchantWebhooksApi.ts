import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { MerchantApiError } from './merchantApi';

export interface WebhookConfig {
  id: number;
  endpointUrl: string;
  secretKey: string;
  subscribedEvents: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWebhookInput {
  endpointUrl: string;
  subscribedEvents: string;
  secretKey?: string;
}

export interface UpdateWebhookInput {
  endpointUrl: string;
  subscribedEvents: string;
  active?: boolean;
  secretKey?: string;
}

export interface WebhookDeliveryLog {
  id: number;
  webhookConfigId: number;
  deliveryId?: number | null;
  eventType: string;
  payload: string;
  httpStatusCode?: number | null;
  attemptCount: number;
  status: 'SUCCESS' | 'RETRYING' | 'FAILED';
  errorMessage?: string | null;
  createdAt: string;
  lastAttemptAt: string;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.apiUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init.headers },
    });
  } catch {
    throw new MerchantApiError(0, `Cannot reach the SwiftDrop server at ${env.apiUrl}. Is the backend running?`);
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
        ? 'Access denied. You do not have permission to manage webhooks.'
        : response.status === 401
          ? 'Your session has expired. Please log in again.'
          : response.status === 400
            ? 'Invalid webhook configuration.'
            : response.status === 404
              ? 'Webhook configuration not found.'
              : 'Something went wrong. Please try again.');

    throw new MerchantApiError(response.status, message, fieldErrors);
  }

  return (response.status === 204 ? undefined : await response.json()) as T;
}

/** Lists all webhook configurations for the authenticated merchant. */
export function getMerchantWebhooks(): Promise<WebhookConfig[]> {
  return request<WebhookConfig[]>('/api/merchants/me/webhooks');
}

/** Creates a new webhook configuration. */
export function createMerchantWebhook(input: CreateWebhookInput): Promise<WebhookConfig> {
  return request<WebhookConfig>('/api/merchants/me/webhooks', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** Gets a single webhook configuration by id. */
export function getMerchantWebhook(id: number): Promise<WebhookConfig> {
  return request<WebhookConfig>(`/api/merchants/me/webhooks/${id}`);
}

/** Updates an existing webhook configuration. */
export function updateMerchantWebhook(id: number, input: UpdateWebhookInput): Promise<WebhookConfig> {
  return request<WebhookConfig>(`/api/merchants/me/webhooks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

/** Deletes a webhook configuration. */
export function deleteMerchantWebhook(id: number): Promise<void> {
  return request<void>(`/api/merchants/me/webhooks/${id}`, {
    method: 'DELETE',
  });
}

/** Gets delivery logs for a specific webhook. */
export function getWebhookLogs(id: number): Promise<WebhookDeliveryLog[]> {
  return request<WebhookDeliveryLog[]>(`/api/merchants/me/webhooks/${id}/logs`);
}

/** Gets the 50 most recent webhook delivery logs across all configs for the merchant. */
export function getRecentWebhookLogs(): Promise<WebhookDeliveryLog[]> {
  return request<WebhookDeliveryLog[]>('/api/merchants/me/webhooks/logs/recent');
}

/** Sends a test ping event to the webhook endpoint. */
export function testMerchantWebhook(id: number): Promise<WebhookDeliveryLog> {
  return request<WebhookDeliveryLog>(`/api/merchants/me/webhooks/${id}/test`, {
    method: 'POST',
  });
}
