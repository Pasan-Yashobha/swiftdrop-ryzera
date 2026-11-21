import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  signal,
  viewChild,
} from '@angular/core';
import { AuditLog } from '../../core/models/audit.model';

/**
 * Modal with everything stored about one audit record: who, what, when, the details (IDs and short facts) and the
 * two hashes that chain it to the previous record. All content is rendered as text.
 */
@Component({
  selector: 'app-audit-detail-dialog',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-detail-dialog.html',
  styleUrls: ['../../shared/ui/dialog.css', './audit-detail-dialog.css'],
})
export class AuditDetailDialog {
  protected readonly entry = signal<AuditLog | null>(null);

  protected readonly details = computed(() =>
    Object.entries(this.entry()?.details ?? {}).sort(([a], [b]) => a.localeCompare(b)),
  );

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private returnFocusTo: HTMLElement | null = null;

  get isOpen(): boolean {
    return this.dialog().nativeElement.open;
  }

  open(entry: AuditLog, trigger: HTMLElement | null = null): void {
    this.returnFocusTo = trigger;
    this.entry.set(entry);
    const dialog = this.dialog().nativeElement;
    if (!dialog.open) {
      dialog.showModal();
    }
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  /** Fires for every way the dialog closes, including Escape. */
  protected onClosed(): void {
    this.entry.set(null);
    this.returnFocusTo?.focus();
    this.returnFocusTo = null;
  }
}
