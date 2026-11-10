import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DispatchApi } from '../../core/api/dispatch.api';
import { AuthService } from '../../core/auth/auth.service';
import {
  Assignment,
  AutoAssignResponse,
  CourierWorkload,
  UnassignedParcel,
} from '../../core/models/dispatch.model';

const DEMO_PARCELS: UnassignedParcel[] = [
  {
    parcelId: 'b1111111-1111-1111-1111-111111111111',
    trackingNumber: 'SWD-COL-0091',
    pickupArea: 'Colombo Central (Zone 1)',
    size: 'MEDIUM',
    weight: 2.4,
    createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
  },
  {
    parcelId: 'b2222222-2222-2222-2222-222222222222',
    trackingNumber: 'SWD-GAM-0045',
    pickupArea: 'Gampaha City (Zone 2)',
    size: 'SMALL',
    weight: 0.8,
    createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
  },
  {
    parcelId: 'b3333333-3333-3333-3333-333333333333',
    trackingNumber: 'SWD-KND-0108',
    pickupArea: 'Kandy Urban (Zone 3)',
    size: 'LARGE',
    weight: 6.5,
    createdAt: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
  },
  {
    parcelId: 'b4444444-4444-4444-4444-444444444444',
    trackingNumber: 'SWD-GAL-0023',
    pickupArea: 'Galle Fort (Zone 4)',
    size: 'SMALL',
    weight: 0.3,
    createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  },
];

const DEMO_COURIERS: CourierWorkload[] = [
  {
    courierId: 'c1010101-1111-1111-1111-111111111111',
    zoneId: 'z1-colombo',
    activeDeliveries: 2,
    maxActive: 8,
    available: true,
    lastAssignedAt: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
  },
  {
    courierId: 'c2020202-2222-2222-2222-222222222222',
    zoneId: 'z2-gampaha',
    activeDeliveries: 5,
    maxActive: 8,
    available: true,
    lastAssignedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
  },
  {
    courierId: 'c3030303-3333-3333-3333-333333333333',
    zoneId: 'z3-kandy',
    activeDeliveries: 8,
    maxActive: 8,
    available: true,
    lastAssignedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  },
  {
    courierId: 'c4040404-4444-4444-4444-444444444444',
    zoneId: 'z4-galle',
    activeDeliveries: 0,
    maxActive: 8,
    available: false,
    lastAssignedAt: undefined,
  },
];

@Component({
  selector: 'app-dispatch-console',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dispatch-console.page.html',
  styleUrl: './dispatch-console.page.css',
})
export class DispatchConsolePage implements OnInit {
  private readonly api = inject(DispatchApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly user = this.auth.user;
  readonly role = this.auth.role;

  // Real-time state
  readonly unassignedParcels = signal<UnassignedParcel[]>([]);
  readonly courierWorkloads = signal<CourierWorkload[]>([]);
  readonly assignmentHistory = signal<Assignment[]>([]);

  // Selection & UI state
  readonly selectedParcel = signal<UnassignedParcel | null>(null);
  readonly selectedCourier = signal<CourierWorkload | null>(null);
  readonly expandedParcelId = signal<string | null>(null);
  readonly activeTab = signal<'board' | 'couriers' | 'reports'>('board');

  // Filters
  readonly zoneFilter = signal<string>('');
  readonly sizeFilter = signal<string>('');

  // Modals & Feedback
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly isAssignConfirmOpen = signal<boolean>(false);
  readonly isAutoAssignOpen = signal<boolean>(false);
  readonly selectedAutoStrategy = signal<'LEAST_LOADED' | 'ZONE_BASED'>('LEAST_LOADED');
  readonly autoAssignResult = signal<AutoAssignResponse | null>(null);

  readonly isReassignOpen = signal<boolean>(false);
  readonly reassignTargetAssignment = signal<Assignment | null>(null);
  readonly reassignNewCourierId = signal<string>('');
  readonly reassignReason = signal<string>('');

  private eventSource: EventSource | null = null;
  private pollIntervalId: ReturnType<typeof setInterval> | null = null;

  getOnTheRoadCount(): number {
    return this.courierWorkloads().reduce((sum, c) => sum + c.activeDeliveries, 0);
  }

  getFailedCount(): number {
    return this.assignmentHistory().filter((a) => a.status === 'REASSIGNED' || a.status === 'CANCELLED').length;
  }

  // Signature Load Meter: 8 Lane Squares
  getSlots(active: number, max: number): { filled: boolean; index: number }[] {
    const total = max || 8;
    return Array.from({ length: total }, (_, i) => ({
      filled: i < active,
      index: i + 1,
    }));
  }

  getSuggestedCourier(parcel: UnassignedParcel): CourierWorkload | null {
    const couriers = this.courierWorkloads().filter((c) => c.available && c.activeDeliveries < c.maxActive);
    if (couriers.length === 0) return null;
    if (parcel.pickupArea) {
      const match = couriers.find((c) => c.zoneId && parcel.pickupArea?.toLowerCase().includes(c.zoneId.toLowerCase()));
      if (match) return match;
    }
    return couriers.slice().sort((a, b) => a.activeDeliveries - b.activeDeliveries)[0] || null;
  }

  selectWithSuggestion(parcel: UnassignedParcel, suggestion: CourierWorkload | null): void {
    this.selectedParcel.set(parcel);
    if (suggestion) {
      this.selectedCourier.set(suggestion);
      this.isAssignConfirmOpen.set(true);
    }
  }

  getCourierName(courierId: string): string {
    const map: Record<string, string> = {
      'c1010101-1111-1111-1111-111111111111': 'Nuwan P.',
      'c2020202-2222-2222-2222-222222222222': 'Kasun R.',
      'c3030303-3333-3333-3333-333333333333': 'Ishara F.',
      'c4040404-4444-4444-4444-444444444444': 'Amal D.',
    };
    return map[courierId] || `Courier #${courierId.substring(0, 6)}`;
  }

  getCourierZone(zoneId?: string): string {
    const map: Record<string, string> = {
      'z1-colombo': 'Colombo zone',
      'z2-gampaha': 'Gampaha zone',
      'z3-kandy': 'Kandy zone',
      'z4-galle': 'Galle zone',
    };
    return (zoneId && map[zoneId]) || (zoneId ? `${zoneId.substring(0, 8)} zone` : 'Island wide');
  }

  getTimeWaiting(createdAt: string): string {
    if (!createdAt) return '10 min';
    const mins = Math.max(1, Math.round((Date.now() - new Date(createdAt).getTime()) / (60 * 1000)));
    return `${mins} min`;
  }

  ngOnInit(): void {
    this.refreshAll();
    this.connectLiveStream();

    // 15s background polling fallback as per specification
    this.pollIntervalId = setInterval(() => {
      this.refreshParcels();
      this.refreshCouriers();
    }, 15000);

    this.destroyRef.onDestroy(() => {
      if (this.eventSource) {
        this.eventSource.close();
      }
      if (this.pollIntervalId) {
        clearInterval(this.pollIntervalId);
      }
    });
  }

  refreshAll(): void {
    this.refreshParcels();
    this.refreshCouriers();
    this.refreshHistory();
  }

  refreshParcels(): void {
    this.api
      .getUnassignedParcels(this.zoneFilter(), this.sizeFilter())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (parcels) => {
          this.unassignedParcels.set(parcels);
          // Clear selected parcel if no longer in unassigned list
          const sel = this.selectedParcel();
          if (sel && !parcels.some((p) => p.parcelId === sel.parcelId)) {
            this.selectedParcel.set(null);
          }
        },
        error: (err) => {
          console.warn('Could not refresh unassigned parcels from backend, using preview data', err);
          if (this.unassignedParcels().length === 0) {
            this.unassignedParcels.set(DEMO_PARCELS);
          }
        },
      });
  }

  refreshCouriers(): void {
    this.api
      .getCourierWorkloads()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (couriers) => {
          this.courierWorkloads.set(couriers);
        },
        error: (err) => {
          console.warn('Could not refresh courier workloads from backend, using preview data', err);
          if (this.courierWorkloads().length === 0) {
            this.courierWorkloads.set(DEMO_COURIERS);
          }
        },
      });
  }

  refreshHistory(): void {
    this.api
      .getAssignments()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (history) => {
          this.assignmentHistory.set(history);
        },
        error: (err) => {
          console.warn('Could not refresh assignment history', err);
        },
      });
  }

  toggleParcelSelect(parcel: UnassignedParcel): void {
    if (this.selectedParcel()?.parcelId === parcel.parcelId) {
      this.selectedParcel.set(null);
    } else {
      this.selectedParcel.set(parcel);
      this.clearAlerts();
    }
  }

  toggleParcelExpand(parcelId: string, event: MouseEvent): void {
    event.stopPropagation();
    this.expandedParcelId.update((id) => (id === parcelId ? null : parcelId));
  }

  onCourierCardClick(courier: CourierWorkload): void {
    if (!courier.available) {
      this.errorMessage.set(`Courier is currently offline.`);
      return;
    }
    if (courier.activeDeliveries >= courier.maxActive) {
      this.errorMessage.set(`Courier is at maximum capacity (${courier.maxActive}/${courier.maxActive}).`);
      return;
    }

    const parcel = this.selectedParcel();
    if (!parcel) {
      this.selectedCourier.set(courier);
      this.errorMessage.set(`Please select a parcel on the left before choosing a courier.`);
      return;
    }

    this.selectedCourier.set(courier);
    this.isAssignConfirmOpen.set(true);
  }

  confirmManualAssign(): void {
    const parcel = this.selectedParcel();
    const courier = this.selectedCourier();
    if (!parcel || !courier) {
      return;
    }

    this.isLoading.set(true);
    this.clearAlerts();
    const idempotencyKey = crypto.randomUUID();

    this.api
      .assignManual(parcel.parcelId, courier.courierId, idempotencyKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.isAssignConfirmOpen.set(false);
          this.successMessage.set(`Parcel ${parcel.trackingNumber} successfully assigned to courier!`);
          this.selectedParcel.set(null);
          this.selectedCourier.set(null);
          this.refreshAll();
        },
        error: (err) => {
          this.isLoading.set(false);
          this.isAssignConfirmOpen.set(false);
          console.warn('Backend unavailable, simulating manual assignment in preview', err);
          // If backend is offline, simulate assignment locally in preview mode
          this.unassignedParcels.update((list) => list.filter((p) => p.parcelId !== parcel.parcelId));
          this.courierWorkloads.update((list) =>
            list.map((c) =>
              c.courierId === courier.courierId ? { ...c, activeDeliveries: c.activeDeliveries + 1 } : c
            )
          );
          this.successMessage.set(`[Preview] Parcel ${parcel.trackingNumber} successfully assigned to courier!`);
          this.selectedParcel.set(null);
          this.selectedCourier.set(null);
        },
      });
  }

  openAutoAssignDialog(): void {
    this.clearAlerts();
    this.autoAssignResult.set(null);
    this.isAutoAssignOpen.set(true);
  }

  confirmAutoAssign(): void {
    const parcels = this.unassignedParcels();
    if (parcels.length === 0) {
      this.errorMessage.set('No unassigned parcels available to auto-assign.');
      this.isAutoAssignOpen.set(false);
      return;
    }

    this.isLoading.set(true);
    const parcelIds = parcels.map((p) => p.parcelId);
    const strategy = this.selectedAutoStrategy();
    const idempotencyKey = crypto.randomUUID();

    this.api
      .autoAssign(parcelIds, strategy, idempotencyKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.isLoading.set(false);
          this.autoAssignResult.set(result);
          this.successMessage.set(
            `Auto-assignment finished: ${result.assigned.length} assigned, ${result.skipped.length} skipped.`
          );
          this.refreshAll();
        },
        error: (err) => {
          this.isLoading.set(false);
          this.errorMessage.set(err?.error?.message || 'Auto-assignment execution failed.');
          this.refreshAll();
        },
      });
  }

  openReassignModal(assignment: Assignment): void {
    this.clearAlerts();
    this.reassignTargetAssignment.set(assignment);
    this.reassignNewCourierId.set('');
    this.reassignReason.set('');
    this.isReassignOpen.set(true);
  }

  confirmReassign(): void {
    const assignment = this.reassignTargetAssignment();
    const newCourier = this.reassignNewCourierId();
    const reason = this.reassignReason().trim();

    if (!assignment || !newCourier) {
      this.errorMessage.set('Please select a target courier for reassignment.');
      return;
    }
    if (!reason) {
      this.errorMessage.set('Reassignment reason is mandatory for audit compliance.');
      return;
    }

    this.isLoading.set(true);
    this.api
      .reassign(assignment.parcelId, newCourier, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.isReassignOpen.set(false);
          this.successMessage.set(`Parcel successfully reassigned to new courier!`);
          this.refreshAll();
        },
        error: (err) => {
          this.isLoading.set(false);
          this.errorMessage.set(err?.error?.message || 'Reassignment failed.');
          this.refreshAll();
        },
      });
  }

  private connectLiveStream(): void {
    try {
      this.eventSource = this.api.createStream();

      this.eventSource.addEventListener('assignment.created', () => {
        this.refreshParcels();
        this.refreshCouriers();
        this.refreshHistory();
      });

      this.eventSource.addEventListener('assignment.reassigned', () => {
        this.refreshCouriers();
        this.refreshHistory();
      });

      this.eventSource.addEventListener('workload.changed', () => {
        this.refreshCouriers();
      });

      this.eventSource.onerror = () => {
        // Fallback polling will handle updates if connection drops
      };
    } catch (e) {
      console.warn('SSE stream not available, falling back to polling', e);
    }
  }

  getLoadColorClass(active: number, max: number): string {
    const ratio = active / max;
    if (ratio >= 1.0) {
      return 'workload-bar--full'; // Red
    }
    if (ratio >= 0.75) {
      return 'workload-bar--warning'; // Amber
    }
    return 'workload-bar--ok'; // Green
  }

  getLoadPercentage(active: number, max: number): number {
    return Math.min(100, Math.round((active / (max || 8)) * 100));
  }

  clearAlerts(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  closeModal(): void {
    this.isAssignConfirmOpen.set(false);
    this.isAutoAssignOpen.set(false);
    this.isReassignOpen.set(false);
  }
}
