import { AuditLog, ChainVerification } from '../models/audit.model';
import { ServiceHealth } from '../models/system.model';

export function anAuditLog(overrides: Partial<AuditLog> = {}): AuditLog {
  return {
    id: 'a-1',
    seq: 1,
    eventId: '11111111-1111-1111-1111-111111111111',
    occurredAt: '2026-11-10T08:30:00Z',
    recordedAt: '2026-11-10T08:30:01Z',
    actorId: 'admin-7',
    actorRole: 'ADMIN',
    action: 'MERCHANT_APPROVED',
    entityType: 'MERCHANT',
    entityId: 'm-100',
    ipAddress: null,
    correlationId: 'corr-1',
    producer: 'merchant-service',
    details: { merchantId: 'm-100', decidedBy: 'admin-7' },
    prevHash: '0'.repeat(64),
    rowHash: 'a'.repeat(64),
    ...overrides,
  };
}

export function aVerification(overrides: Partial<ChainVerification> = {}): ChainVerification {
  return {
    valid: true,
    checked: 42,
    firstBrokenSeq: null,
    reason: null,
    verifiedAt: '2026-11-10T09:00:00Z',
    ...overrides,
  };
}

export function aServiceHealth(overrides: Partial<ServiceHealth> = {}): ServiceHealth {
  return { service: 'identity', status: 'UP', responseMs: 12, ...overrides };
}
