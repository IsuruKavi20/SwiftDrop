import { env } from '../../config/env';
import { authorizedFetch } from '../../features/auth/api/authApi';
import type {
  CourierDataService,
  DashboardStats,
  DeliverySummary,
  DeliveryDetails,
  CourierProfile,
  ActivityUpdate,
  StatusUpdateRequest,
  DeliveryConfirmationRequest,
  DeliveryFailureRequest,
  ParcelStatus,
  StatusHistoryItem,
  DeliveryProof,
  DeliveryFailure,
} from '../../types/courier';
import {
  mockCourierProfile,
  initialMockDeliveries,
  initialMockUpdates,
} from '../../mocks/courier/mockData';

/**
 * Standard API error structure mirroring RFC 9457 ProblemDetail payloads
 * emitted by CourierApiExceptionHandler.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/** Formats an ISO timestamp into a friendly human-readable relative duration. */
function formatTimeAgo(dateStr?: string): string {
  if (!dateStr) return 'Just now';
  try {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    if (diffMs < 0) return 'Just now';
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  } catch {
    return dateStr;
  }
}

// ---------------------------------------------------------------------------
// Backend DTO Contracts
// ---------------------------------------------------------------------------

interface BackendDashboardResponse {
  assignedToday: number;
  inProgress: number;
  delivered: number;
  failed: number;
  completionPercentage: number;
}

interface BackendProfileResponse {
  userId: string;
  fullName?: string;
  email?: string;
  phoneNumber?: string;
  vehicleType?: string;
  assignedZone?: string;
  isActive: boolean;
  avatarUrl?: string;
}

interface BackendDeliverySummary {
  id: number;
  trackingNumber: string;
  recipientName?: string;
  recipientPhone?: string;
  deliveryAddress?: string;
  city?: string;
  postalCode?: string;
  status: ParcelStatus;
  specialInstructions?: string;
  assignedAt?: string;
  isToday: boolean;
}

interface BackendPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface BackendDeliveryDetails extends BackendDeliverySummary {
  deliveryType?: string;
  pickupContactName?: string;
  pickupContactPhone?: string;
  pickupAddress?: string;
  pickupCity?: string;
  statusHistory?: Array<{
    id?: number;
    status: ParcelStatus;
    description?: string;
    locationName?: string;
    eventTime?: string;
    recordedByName?: string;
  }>;
  proof?: {
    id?: number;
    proofType?: string;
    recipientSignName: string;
    deliveryNotes?: string;
    proofMediaUrl?: string;
    capturedAt?: string;
  };
  latestFailure?: {
    id?: number;
    failureReason: string;
    failureRemarks: string;
    reportedAt?: string;
    location?: string;
  };
}

// ---------------------------------------------------------------------------
// Real HTTP Courier Data Service
// ---------------------------------------------------------------------------

export class HttpCourierDataServiceImpl implements CourierDataService {
  private baseUrl: string;

  constructor(baseUrl: string = env.apiUrl) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    let response: Response;
    try {
      response = await authorizedFetch(`${this.baseUrl}${path}`, {
        ...init,
        headers: {
          'Content-Type': 'application/json',
          ...init.headers,
        },
      });
    } catch (err: unknown) {
      if (err instanceof ApiError) throw err;
      throw new ApiError(0, `Cannot reach the SwiftDrop server at ${this.baseUrl}. Is the backend running?`);
    }

    if (!response.ok) {
      const problem = await response.json().catch(() => null);
      const message = problem?.detail || problem?.title || `Request failed with status ${response.status}`;
      throw new ApiError(response.status, message, problem?.errors);
    }

    if (response.status === 204) {
      return undefined as unknown as T;
    }

    return (await response.json()) as T;
  }

  private mapSummary(d: BackendDeliverySummary): DeliverySummary {
    return {
      id: String(d.id),
      trackingNumber: d.trackingNumber,
      recipientName: d.recipientName || 'Recipient',
      recipientPhone: d.recipientPhone || '',
      deliveryAddress: d.deliveryAddress || '',
      city: d.city || '',
      postalCode: d.postalCode || '',
      status: d.status,
      specialInstructions: d.specialInstructions || undefined,
      assignedAt: d.assignedAt ? new Date(d.assignedAt).toISOString() : new Date().toISOString(),
      isToday: Boolean(d.isToday),
    };
  }

  private mapDetails(d: BackendDeliveryDetails): DeliveryDetails {
    const summary = this.mapSummary(d);

    const statusHistory: StatusHistoryItem[] = (d.statusHistory || []).map((h, idx) => ({
      id: String(h.id ?? idx),
      toStatus: h.status,
      changedBy: h.recordedByName || 'Courier',
      timestamp: h.eventTime ? formatTimeAgo(h.eventTime) : 'Just now',
      notes: h.description || h.locationName || undefined,
    }));

    const proof: DeliveryProof | undefined = d.proof
      ? {
          id: String(d.proof.id ?? 'proof'),
          proofType: d.proof.proofType || 'RECIPIENT_CONFIRMATION',
          recipientSignName: d.proof.recipientSignName,
          deliveryNotes: d.proof.deliveryNotes || undefined,
          proofMediaUrl: d.proof.proofMediaUrl || undefined,
          capturedAt: d.proof.capturedAt ? formatTimeAgo(d.proof.capturedAt) : 'Just now',
        }
      : undefined;

    const failure: DeliveryFailure | undefined = d.latestFailure
      ? {
          id: String(d.latestFailure.id ?? 'failure'),
          failureReason: d.latestFailure.failureReason,
          failureRemarks: d.latestFailure.failureRemarks,
          reportedAt: d.latestFailure.reportedAt ? formatTimeAgo(d.latestFailure.reportedAt) : 'Just now',
        }
      : undefined;

    return {
      ...summary,
      statusHistory,
      proof,
      failure,
    };
  }

  async getDashboardStats(): Promise<DashboardStats> {
    const res = await this.request<BackendDashboardResponse>('/api/couriers/me/dashboard');
    return {
      assignedToday: Number(res.assignedToday ?? 0),
      inProgress: Number(res.inProgress ?? 0),
      delivered: Number(res.delivered ?? 0),
      failed: Number(res.failed ?? 0),
      completionPercentage: Number(res.completionPercentage ?? 0),
    };
  }

  async getAssignedDeliveries(filter?: { status?: string; search?: string }): Promise<DeliverySummary[]> {
    const params = new URLSearchParams();
    params.set('page', '0');
    params.set('size', '50');

    if (filter?.status && filter.status !== 'ALL') {
      params.set('status', filter.status);
    }
    if (filter?.search && filter.search.trim()) {
      params.set('search', filter.search.trim());
    }

    const res = await this.request<BackendPageResponse<BackendDeliverySummary>>(
      `/api/couriers/me/deliveries?${params.toString()}`
    );
    return (res.content || []).map((item) => this.mapSummary(item));
  }

  async getDeliveryDetails(trackingNumber: string): Promise<DeliveryDetails | null> {
    try {
      const res = await this.request<BackendDeliveryDetails>(
        `/api/couriers/me/deliveries/${encodeURIComponent(trackingNumber)}`
      );
      return this.mapDetails(res);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        return null;
      }
      throw err;
    }
  }

  async getDeliveryHistory(): Promise<DeliverySummary[]> {
    const params = new URLSearchParams();
    params.set('page', '0');
    params.set('size', '100');

    const res = await this.request<BackendPageResponse<BackendDeliverySummary>>(
      `/api/couriers/me/deliveries?${params.toString()}`
    );
    return (res.content || [])
      .filter((d) => d.status === 'DELIVERED' || d.status === 'FAILED')
      .map((d) => this.mapSummary(d));
  }

  async getCourierProfile(): Promise<CourierProfile> {
    const res = await this.request<BackendProfileResponse>('/api/couriers/me/profile');
    return {
      id: res.userId,
      fullName: res.fullName || 'Courier Personnel',
      email: res.email || '',
      phoneNumber: res.phoneNumber || '',
      vehicleType: res.vehicleType || 'Motorcycle',
      assignedZone: res.assignedZone || 'Assigned Zone',
      isActive: res.isActive ?? true,
      avatarUrl: res.avatarUrl,
    };
  }

  async updateCourierProfile(request: {
    phoneNumber?: string;
    vehicleType?: string;
  }): Promise<CourierProfile> {
    const res = await this.request<BackendProfileResponse>('/api/couriers/me/profile', {
      method: 'PUT',
      body: JSON.stringify({
        phoneNumber: request.phoneNumber,
        vehicleType: request.vehicleType,
      }),
    });
    return {
      id: res.userId,
      fullName: res.fullName || 'Courier Personnel',
      email: res.email || '',
      phoneNumber: res.phoneNumber || '',
      vehicleType: res.vehicleType || 'Motorcycle',
      assignedZone: res.assignedZone || 'Assigned Zone',
      isActive: res.isActive ?? true,
      avatarUrl: res.avatarUrl,
    };
  }

  async getRecentUpdates(): Promise<ActivityUpdate[]> {
    const params = new URLSearchParams({ page: '0', size: '10' });
    const res = await this.request<BackendPageResponse<BackendDeliverySummary>>(
      `/api/couriers/me/deliveries?${params.toString()}`
    );
    return (res.content || []).slice(0, 10).map((d) => ({
      id: `act-${d.id}-${d.status}`,
      trackingNumber: d.trackingNumber,
      recipientName: d.recipientName || 'Recipient',
      status: d.status,
      timestamp: formatTimeAgo(d.assignedAt),
      location: d.city || undefined,
    }));
  }

  async updateDeliveryStatus(
    trackingNumber: string,
    request: StatusUpdateRequest
  ): Promise<DeliveryDetails> {
    const res = await this.request<BackendDeliveryDetails>(
      `/api/couriers/me/deliveries/${encodeURIComponent(trackingNumber)}/status`,
      {
        method: 'PATCH',
        body: JSON.stringify({
          nextStatus: request.nextStatus,
          notes: request.notes,
          location: request.location,
        }),
      }
    );
    return this.mapDetails(res);
  }

  async confirmDelivery(
    trackingNumber: string,
    request: DeliveryConfirmationRequest
  ): Promise<DeliveryDetails> {
    const res = await this.request<BackendDeliveryDetails>(
      `/api/couriers/me/deliveries/${encodeURIComponent(trackingNumber)}/confirm`,
      {
        method: 'POST',
        body: JSON.stringify({
          recipientSignName: request.recipientSignName,
          proofType: request.proofType || 'RECIPIENT_CONFIRMATION',
          deliveryNotes: request.deliveryNotes,
          proofMediaUrl: request.proofMediaUrl,
          location: request.location,
        }),
      }
    );
    return this.mapDetails(res);
  }

  async reportFailure(
    trackingNumber: string,
    request: DeliveryFailureRequest
  ): Promise<DeliveryDetails> {
    const res = await this.request<BackendDeliveryDetails>(
      `/api/couriers/me/deliveries/${encodeURIComponent(trackingNumber)}/fail`,
      {
        method: 'POST',
        body: JSON.stringify({
          failureReason: request.failureReason,
          failureRemarks: request.failureRemarks,
          location: request.location,
        }),
      }
    );
    return this.mapDetails(res);
  }
}

// ---------------------------------------------------------------------------
// Mock Courier Data Service (Development and Offline Testing Only)
// ---------------------------------------------------------------------------

export class MockCourierDataServiceImpl implements CourierDataService {
  private deliveries: DeliveryDetails[] = [...initialMockDeliveries];
  private updates: ActivityUpdate[] = [...initialMockUpdates];
  private profile: CourierProfile = { ...mockCourierProfile };

  private delay(ms = 120): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async getDashboardStats(): Promise<DashboardStats> {
    await this.delay();
    const todayDeliveries = this.deliveries.filter((d) => d.isToday);
    const assignedToday = todayDeliveries.length;
    const delivered = todayDeliveries.filter((d) => d.status === 'DELIVERED').length;
    const failed = todayDeliveries.filter((d) => d.status === 'FAILED').length;
    const inProgress = todayDeliveries.filter((d) =>
      ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(d.status)
    ).length;

    const completionPercentage =
      assignedToday > 0 ? Math.round((delivered / assignedToday) * 100) : 0;

    return {
      assignedToday,
      inProgress,
      delivered,
      failed,
      completionPercentage,
    };
  }

  async getAssignedDeliveries(filter?: {
    status?: string;
    search?: string;
  }): Promise<DeliverySummary[]> {
    await this.delay();
    let result = [...this.deliveries];

    if (filter?.status && filter.status !== 'ALL') {
      const s = filter.status.toUpperCase();
      if (s === 'PENDING') {
        result = result.filter((d) => ['CREATED', 'PENDING', 'CONFIRMED', 'PICKED_UP'].includes(d.status));
      } else if (s === 'IN_TRANSIT') {
        result = result.filter((d) => ['IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(d.status));
      } else {
        result = result.filter((d) => d.status === s);
      }
    }

    if (filter?.search && filter.search.trim()) {
      const q = filter.search.trim().toLowerCase();
      result = result.filter(
        (d) =>
          d.trackingNumber.toLowerCase().includes(q) ||
          d.recipientName.toLowerCase().includes(q) ||
          d.city.toLowerCase().includes(q) ||
          d.deliveryAddress.toLowerCase().includes(q)
      );
    }

    return result.map((d) => ({
      id: d.id,
      trackingNumber: d.trackingNumber,
      recipientName: d.recipientName,
      recipientPhone: d.recipientPhone,
      deliveryAddress: d.deliveryAddress,
      city: d.city,
      postalCode: d.postalCode,
      status: d.status,
      specialInstructions: d.specialInstructions,
      assignedAt: d.assignedAt,
      isToday: d.isToday,
    }));
  }

  async getDeliveryDetails(trackingNumber: string): Promise<DeliveryDetails | null> {
    await this.delay();
    const match = this.deliveries.find(
      (d) => d.trackingNumber.toUpperCase() === trackingNumber.toUpperCase()
    );
    return match ? { ...match } : null;
  }

  async getDeliveryHistory(): Promise<DeliverySummary[]> {
    await this.delay();
    return this.deliveries
      .filter((d) => ['DELIVERED', 'FAILED'].includes(d.status))
      .map((d) => ({
        id: d.id,
        trackingNumber: d.trackingNumber,
        recipientName: d.recipientName,
        recipientPhone: d.recipientPhone,
        deliveryAddress: d.deliveryAddress,
        city: d.city,
        postalCode: d.postalCode,
        status: d.status,
        specialInstructions: d.specialInstructions,
        assignedAt: d.assignedAt,
        isToday: d.isToday,
      }));
  }

  async getCourierProfile(): Promise<CourierProfile> {
    await this.delay();
    return { ...this.profile };
  }

  async updateCourierProfile(request: {
    phoneNumber?: string;
    vehicleType?: string;
  }): Promise<CourierProfile> {
    await this.delay();
    if (request.phoneNumber !== undefined) {
      this.profile.phoneNumber = request.phoneNumber;
    }
    if (request.vehicleType !== undefined) {
      this.profile.vehicleType = request.vehicleType;
    }
    return { ...this.profile };
  }

  async getRecentUpdates(): Promise<ActivityUpdate[]> {
    await this.delay();
    return [...this.updates];
  }

  async updateDeliveryStatus(
    trackingNumber: string,
    request: StatusUpdateRequest
  ): Promise<DeliveryDetails> {
    await this.delay();
    const index = this.deliveries.findIndex(
      (d) => d.trackingNumber.toUpperCase() === trackingNumber.toUpperCase()
    );

    if (index === -1) {
      throw new ApiError(404, `Parcel with tracking number ${trackingNumber} not found.`);
    }

    const current = this.deliveries[index];
    const newHistoryItem: StatusHistoryItem = {
      id: `h-${Date.now()}`,
      fromStatus: current.status,
      toStatus: request.nextStatus,
      changedBy: this.profile.fullName,
      timestamp: 'Just now',
      notes: request.notes || `Status advanced to ${request.nextStatus}`,
    };

    const updated: DeliveryDetails = {
      ...current,
      status: request.nextStatus,
      statusHistory: [newHistoryItem, ...current.statusHistory],
    };

    this.deliveries[index] = updated;

    this.updates.unshift({
      id: `u-${Date.now()}`,
      trackingNumber: updated.trackingNumber,
      recipientName: updated.recipientName,
      status: updated.status,
      timestamp: 'Just now',
      location: updated.city,
    });

    return { ...updated };
  }

  async confirmDelivery(
    trackingNumber: string,
    request: DeliveryConfirmationRequest
  ): Promise<DeliveryDetails> {
    await this.delay();
    const index = this.deliveries.findIndex(
      (d) => d.trackingNumber.toUpperCase() === trackingNumber.toUpperCase()
    );

    if (index === -1) {
      throw new ApiError(404, `Parcel ${trackingNumber} not found.`);
    }

    const current = this.deliveries[index];
    const proof: DeliveryProof = {
      id: `p-${Date.now()}`,
      proofType: request.proofType || 'RECIPIENT_CONFIRMATION',
      recipientSignName: request.recipientSignName,
      deliveryNotes: request.deliveryNotes,
      proofMediaUrl: request.proofMediaUrl,
      capturedAt: 'Just now',
    };

    const newHistoryItem: StatusHistoryItem = {
      id: `h-${Date.now()}`,
      fromStatus: current.status,
      toStatus: 'DELIVERED',
      changedBy: this.profile.fullName,
      timestamp: 'Just now',
      notes: request.deliveryNotes || 'Signed and received by customer',
    };

    const updated: DeliveryDetails = {
      ...current,
      status: 'DELIVERED',
      proof,
      statusHistory: [newHistoryItem, ...current.statusHistory],
    };

    this.deliveries[index] = updated;

    this.updates.unshift({
      id: `u-${Date.now()}`,
      trackingNumber: updated.trackingNumber,
      recipientName: updated.recipientName,
      status: 'DELIVERED',
      timestamp: 'Just now',
      location: updated.city,
    });

    return { ...updated };
  }

  async reportFailure(
    trackingNumber: string,
    request: DeliveryFailureRequest
  ): Promise<DeliveryDetails> {
    await this.delay();
    const index = this.deliveries.findIndex(
      (d) => d.trackingNumber.toUpperCase() === trackingNumber.toUpperCase()
    );

    if (index === -1) {
      throw new ApiError(404, `Parcel ${trackingNumber} not found.`);
    }

    const current = this.deliveries[index];
    const failure: DeliveryFailure = {
      id: `f-${Date.now()}`,
      failureReason: request.failureReason,
      failureRemarks: request.failureRemarks,
      reportedAt: 'Just now',
    };

    const newHistoryItem: StatusHistoryItem = {
      id: `h-${Date.now()}`,
      fromStatus: current.status,
      toStatus: 'FAILED',
      changedBy: this.profile.fullName,
      timestamp: 'Just now',
      notes: `Delivery failed: ${request.failureReason}. ${request.failureRemarks}`,
    };

    const updated: DeliveryDetails = {
      ...current,
      status: 'FAILED',
      failure,
      statusHistory: [newHistoryItem, ...current.statusHistory],
    };

    this.deliveries[index] = updated;

    this.updates.unshift({
      id: `u-${Date.now()}`,
      trackingNumber: updated.trackingNumber,
      recipientName: updated.recipientName,
      status: 'FAILED',
      timestamp: 'Just now',
      location: updated.city,
    });

    return { ...updated };
  }
}

// ---------------------------------------------------------------------------
// Service Export Strategy
// ---------------------------------------------------------------------------

/**
 * Singleton data service instance used by the Courier Portal pages.
 * Defaults to authenticated HttpCourierDataServiceImpl in production and live dev.
 * Uses MockCourierDataServiceImpl ONLY when VITE_COURIER_MOCK_MODE=true in development.
 */
export const courierService: CourierDataService = env.isCourierMockMode
  ? new MockCourierDataServiceImpl()
  : new HttpCourierDataServiceImpl();
