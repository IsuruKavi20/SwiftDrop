import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';
import { MerchantApiError } from './merchantApi';

export interface MerchantApplicationRequest {
  businessName: string;
  registrationNumber: string;
  website?: string | null;
}

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface MerchantApplicationResponse {
  id?: number | string;
  businessName: string;
  registrationNumber: string;
  website?: string | null;
  status: ApplicationStatus;
  rejectionReason?: string | null;
  createdAt?: string;
  decidedAt?: string;
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
    if (response.status === 404) {
      // 404 indicates no application submitted yet
      return null as T;
    }

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
      (response.status === 409
        ? 'An application for this business or account already exists.'
        : response.status === 400
          ? 'Some application fields are invalid. Please check your entries.'
          : 'Something went wrong submitting your application. Please try again.');

    throw new MerchantApiError(response.status, message, fieldErrors);
  }

  return (response.status === 204 ? undefined : await response.json()) as T;
}

/** Fetches the current merchant application status for the logged-in user. Returns null if none exists. */
export function getMerchantApplication(): Promise<MerchantApplicationResponse | null> {
  return request<MerchantApplicationResponse | null>('/api/v1/merchants/application');
}

/** Submits a new merchant application to the auth service. */
export function submitMerchantApplication(
  data: MerchantApplicationRequest
): Promise<MerchantApplicationResponse> {
  const payload = {
    businessName: data.businessName.trim(),
    registrationNumber: data.registrationNumber.trim(),
    website: data.website?.trim() ? data.website.trim() : undefined,
  };

  return request<MerchantApplicationResponse>('/api/v1/merchants/application', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
