import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';

/** Mirrors com.swiftdrop.backend.merchant.dto.MerchantResponse */
export interface MerchantResponse {
  id: number;
  complete: boolean;
  ownerName: string | null;
  businessName: string | null;
  businessEmail: string | null;
  contactNumber: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  province: string | null;
  postalCode: string | null;
  country: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors com.swiftdrop.backend.merchant.dto.MerchantUpdateRequest */
export interface MerchantUpdateRequest {
  businessName: string;
  businessEmail: string;
  contactNumber: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  province?: string | null;
  postalCode?: string | null;
  country: string;
}

export interface MerchantProfileUpdate {
  businessName: string;
  businessEmail: string;
  contactNumber: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  province: string;
  postalCode: string;
  country: string;
}

export type MerchantProfile = MerchantResponse;

export class MerchantApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'MerchantApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await authorizedFetch(`${env.apiUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init.headers },
    });
  } catch (error) {
    if (init?.signal?.aborted) throw error;
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
        ? 'Access denied. You do not have permission to access merchant profiles.'
        : response.status === 401
          ? 'Your session has expired. Please log in again.'
          : response.status === 400
            ? 'Some fields are invalid. Please check your entries and try again.'
            : 'Something went wrong. Please try again.');

    throw new MerchantApiError(response.status, message, fieldErrors);
  }

  return (response.status === 204 ? undefined : await response.json()) as T;
}

/** Fetches the logged-in merchant's profile (creates an empty profile on first visit). */
export function getMerchantProfile(): Promise<MerchantResponse> {
  return request<MerchantResponse>('/api/merchants/me');
}

/** Updates the business profile details. Sends only update DTO fields. */
export function updateMerchantProfile(
  details: MerchantUpdateRequest | MerchantProfileUpdate,
  signal?: AbortSignal
): Promise<MerchantResponse> {
  const payload: MerchantUpdateRequest = {
    businessName: details.businessName,
    businessEmail: details.businessEmail,
    contactNumber: details.contactNumber,
    addressLine1: details.addressLine1,
    addressLine2: details.addressLine2 ? details.addressLine2 : null,
    city: details.city,
    province: details.province ? details.province : null,
    postalCode: details.postalCode ? details.postalCode : null,
    country: details.country,
  };

  return request<MerchantResponse>('/api/merchants/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
    signal,
  });
}
