import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { NotificationAdminApi } from '../../core/api/notification-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { NotificationTemplate } from '../../core/models/notification.model';
import { templatePlaceholders } from '../../shared/util/template-placeholders';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { StatusBadge } from '../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../shared/ui/toast/toast.service';

/** Lists the notification templates and edits one at a time in a modal dialog. Bodies stay plain text. */
@Component({
  selector: 'app-templates-editor',
  imports: [ReactiveFormsModule, DatePipe, StatusBadge, ErrorAlert, EmptyState, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './templates-editor.html',
  styleUrl: '../../shared/ui/dialog.css',
})
export class TemplatesEditor {
  /** Shown as text (interpolated), so the braces are never parsed as an Angular binding. */
  protected readonly placeholderSyntax = '{{placeholder}}';

  protected readonly templates = signal<NotificationTemplate[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);

  protected readonly editing = signal<NotificationTemplate | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);

  protected readonly form = new FormGroup({
    subject: new FormControl('', { nonNullable: true }),
    body: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    active: new FormControl(true, { nonNullable: true }),
  });

  private readonly bodyValue = toSignal(this.form.controls.body.valueChanges, { initialValue: '' });
  protected readonly placeholders = computed(() => templatePlaceholders(this.bodyValue()));

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly api = inject(NotificationAdminApi);
  private readonly toasts = inject(ToastService);
  private request: Subscription | null = null;
  private returnFocusTo: HTMLElement | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
    this.load();
  }

  protected load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api.templates().subscribe({
      next: (templates) => {
        this.templates.set(templates);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }

  /** Subject only matters for e-mail; SMS templates without one do not show the field. */
  protected hasSubject(template: NotificationTemplate): boolean {
    return template.channel === 'EMAIL' || template.subject !== null;
  }

  protected edit(template: NotificationTemplate, trigger: HTMLElement): void {
    this.returnFocusTo = trigger;
    this.editing.set(template);
    this.saveError.set(null);
    this.form.reset({
      subject: template.subject ?? '',
      body: template.body,
      active: template.active,
    });
    const dialog = this.dialog().nativeElement;
    if (!dialog.open) {
      dialog.showModal();
    }
  }

  protected cancel(): void {
    this.dialog().nativeElement.close();
  }

  /** Fires for every way the dialog closes, including Escape. */
  protected onClosed(): void {
    this.editing.set(null);
    this.returnFocusTo?.focus();
    this.returnFocusTo = null;
  }

  protected save(): void {
    const template = this.editing();
    if (!template || this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const subject = this.hasSubject(template) ? value.subject.trim() || null : null;
    this.saving.set(true);
    this.saveError.set(null);
    this.api
      .updateTemplate(template.id, { subject, body: value.body, active: value.active })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.templates.update((list) =>
            (list ?? []).map((item) => (item.id === updated.id ? updated : item)),
          );
          this.toasts.success(`Template ${updated.eventType} (${updated.channel}) saved.`);
          this.dialog().nativeElement.close();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveError.set(toAppError(error));
        },
      });
  }
}
