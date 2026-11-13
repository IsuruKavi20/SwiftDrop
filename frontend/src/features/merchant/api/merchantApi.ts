import { env } from '../../../config/env';
import { authorizedFetch } from '../../auth/api/authApi';

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

export type MerchantProfile = { [Field in keyof MerchantProfileUpdate]: string | null } & {
  id: number;
  complete: boolean;
  ownerName: string | null;
  createdAt: string;
  updatedAt: string;
};

const profileUrl = `${env.apiUrl}/api/merchants/me`;

async function profileRequest(init?: RequestInit): Promise<MerchantProfile> {
  let response: Response;
  try {
    response = await authorizedFetch(profileUrl, init);
  } catch (error) {
    if (init?.signal?.aborted) throw error;
    throw new Error('Could not reach SwiftDrop. Check your connection and try again.', { cause: error });
  }
  switch (response.status) {
    case 401: throw new Error('Your session has expired. Sign in again to access your profile.');
    case 403: throw new Error('Your account does not have access to this Merchant profile.');
    case 400: throw new Error('SwiftDrop rejected the profile details. Check the form and try again.');
    default:
      if (!response.ok) throw new Error('SwiftDrop could not process your profile request. Try again.');
      return response.json() as Promise<MerchantProfile>;
  }
}

export function getMerchantProfile() {
  return profileRequest();
}

export function updateMerchantProfile(details: MerchantProfileUpdate, signal: AbortSignal) {
  return profileRequest({
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(details),
    signal,
  });
}
