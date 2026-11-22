export interface UnassignedParcel {
  parcelId: string;
  trackingNumber: string;
  pickupZone?: string;
  pickupArea?: string;
  size: string;
  weight: number;
  createdAt: string;
}

export interface CourierWorkload {
  courierId: string;
  zoneId?: string;
  activeDeliveries: number;
  maxActive: number;
  available: boolean;
  lastAssignedAt?: string;
}

export interface ManualAssignRequest {
  parcelId: string;
  courierId: string;
}

export interface AutoAssignRequest {
  parcelIds: string[];
  strategy: 'LEAST_LOADED' | 'ZONE_BASED';
}

export interface AutoAssignResponse {
  assigned: { parcelId: string; courierId: string; strategy: string }[];
  skipped: { parcelId: string; reason: string }[];
}

export interface ReassignRequest {
  newCourierId: string;
  reason: string;
}

export interface Assignment {
  assignmentId: string;
  parcelId: string;
  courierId: string;
  strategy: string;
  status: 'ACTIVE' | 'REASSIGNED' | 'CANCELLED';
  assignedBy: string;
  assignedAt: string;
  reason?: string;
}

