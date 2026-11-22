/** Summary metrics for the delivery operations in a time window. */
export interface ReportSummary {
  created: number;
  delivered: number;
  failed: number;
  returned: number;
  attemptRate: number | null;
  finalOutcomeRate: number | null;
  activeCouriers: number;
  reconciliationMismatch: boolean;
  lastUpdatedAt: string | null;
}

/** Single day point in deliveries-per-day trend. */
export interface DailyDeliveryPoint {
  date: string;
  created: number;
  delivered: number;
  failed: number;
}

/** Success rate stats over a date window. */
export interface SuccessRateStats {
  from: string;
  to: string;
  attemptRate: number | null;
  finalOutcomeRate: number | null;
}

/** Row representing a courier's performance during the period. */
export interface CourierPerformance {
  courierId: string;
  courierName: string;
  assignedCount: number;
  reassignedAwayCount: number;
  deliveredCount: number;
  failedAttempts: number;
  totalMinutes: number;
  timedCount: number;
  averageDeliveryMinutes: number | null;
}

/** Delivery stats broken down by zone. */
export interface ZoneDeliveryStats {
  zoneId: string;
  zoneName: string;
  createdCount: number;
  deliveredCount: number;
  failedCount: number;
  feeTotal: number;
}

export type ReportPreset = 'today' | '7d' | '30d' | 'custom';

export interface ReportFilter {
  preset: ReportPreset;
  from: string;
  to: string;
  zoneId?: string;
  sort?: string;
}
