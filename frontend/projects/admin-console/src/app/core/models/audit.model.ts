/** Entity types the Audit Log Service derives from the event type (the part before the first dot, upper-cased). */
export const AUDIT_ENTITY_TYPES = ['USER', 'MERCHANT', 'PARCEL', 'WEBHOOK', 'ADMIN'] as const;

/** One audit record (GET /admin/audit-logs). Records are append-only; there is no way to change one. */
export interface AuditLog {
  id: string;
  /** Position in the hash chain (insertion order). */
  seq: number;
  eventId: string;
  occurredAt: string;
  recordedAt: string;
  actorId: string | null;
  actorRole: string | null;
  /** For example MERCHANT_APPROVED. */
  action: string;
  entityType: string | null;
  entityId: string | null;
  ipAddress: string | null;
  correlationId: string | null;
  producer: string | null;
  /** IDs and short facts only: secrets and personal data are removed before storing. */
  details: Record<string, string> | null;
  prevHash: string;
  rowHash: string;
}

export interface AuditFilters {
  action?: string | null;
  actor?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  /** ISO instants. */
  from?: string | null;
  to?: string | null;
}

export interface AuditQuery extends AuditFilters {
  page: number;
  size: number;
}

/** GET /admin/audit-logs/verify: the result of re-computing the whole hash chain. */
export interface ChainVerification {
  valid: boolean;
  checked: number;
  /** The first record that does not match; null when the chain is intact. */
  firstBrokenSeq: number | null;
  reason: string | null;
  verifiedAt: string;
}
