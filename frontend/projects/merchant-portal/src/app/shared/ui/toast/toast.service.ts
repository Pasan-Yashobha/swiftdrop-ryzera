import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

const MAX_VISIBLE = 4;
const DEFAULT_DURATION_MS: Readonly<Record<ToastKind, number>> = {
  success: 5000,
  info: 5000,
  error: 9000,
};

/** Short-lived notifications, rendered by <app-toast-outlet> (mounted once in the app shell). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly toastList = signal<Toast[]>([]);

  readonly toasts = this.toastList.asReadonly();

  success(message: string, durationMs = DEFAULT_DURATION_MS.success): void {
    this.show('success', message, durationMs);
  }

  info(message: string, durationMs = DEFAULT_DURATION_MS.info): void {
    this.show('info', message, durationMs);
  }

  error(message: string, durationMs = DEFAULT_DURATION_MS.error): void {
    this.show('error', message, durationMs);
  }

  dismiss(id: number): void {
    this.toastList.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  private show(kind: ToastKind, message: string, durationMs: number): void {
    const toast: Toast = { id: this.nextId++, kind, message };
    this.toastList.update((toasts) => [...toasts, toast].slice(-MAX_VISIBLE));
    setTimeout(() => this.dismiss(toast.id), durationMs);
  }
}
