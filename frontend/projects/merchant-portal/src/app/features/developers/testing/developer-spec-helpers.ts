import { isSignal } from '@angular/core';
import { ComponentFixture } from '@angular/core/testing';
import { AbstractControl } from '@angular/forms';
import { ApiKey, OAuthClient } from '../../../core/models/credential.model';
import {
  WebhookConfigured,
  WebhookDelivery,
  WebhookDeliveryPage,
} from '../../../core/models/webhook.model';
import { element } from '../../../core/testing/dom';

/** Builders for the DTOs the developer specs share, so each spec only states what it cares about. */
export function anApiKey(overrides: Partial<ApiKey> = {}): ApiKey {
  return {
    id: 'key-1',
    label: 'Shop backend',
    environment: 'LIVE',
    keyPrefix: 'sd_live_ab12cd34',
    status: 'ACTIVE',
    expiresAt: null,
    lastUsedAt: null,
    createdAt: '2026-10-01T08:00:00Z',
    revokedAt: null,
    ...overrides,
  };
}

export function anOAuthClient(overrides: Partial<OAuthClient> = {}): OAuthClient {
  return {
    id: 'client-1',
    clientId: 'mc_abcdefghij0123456789',
    label: 'Fulfilment worker',
    environment: 'TEST',
    scopes: ['orders:write', 'orders:read'],
    status: 'ACTIVE',
    lastUsedAt: null,
    createdAt: '2026-10-02T08:00:00Z',
    revokedAt: null,
    ...overrides,
  };
}

export function aConfiguredWebhook(overrides: Partial<WebhookConfigured> = {}): WebhookConfigured {
  return {
    configured: true,
    url: 'https://shop.example.com/hooks/swiftdrop',
    active: true,
    events: ['*'],
    secretHint: 'whsec_ab12...',
    lastTestAt: null,
    lastTestStatus: null,
    ...overrides,
  };
}

export function aDelivery(overrides: Partial<WebhookDelivery> = {}): WebhookDelivery {
  return {
    id: 'delivery-1',
    eventId: 'event-1',
    eventType: 'parcel.status.delivered',
    status: 'DELIVERED',
    attempts: 1,
    lastHttpStatus: 200,
    durationMs: 120,
    createdAt: '2026-11-01T08:30:12Z',
    lastAttemptAt: '2026-11-01T08:30:13Z',
    error: null,
    ...overrides,
  };
}

export function aDeliveryPage(
  content: WebhookDelivery[],
  overrides: Partial<WebhookDeliveryPage> = {},
): WebhookDeliveryPage {
  return {
    available: true,
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  };
}

/** Waits for the DOM, form and dialog promises to settle after an interaction. */
export async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await new Promise((resolve) => setTimeout(resolve));
  fixture.detectChanges();
}

/** The one-time secret dialog of a page or section, which every credential flow ends in. */
export function secretDialog(fixture: ComponentFixture<unknown>): HTMLDialogElement {
  const dialog = element(fixture).querySelector<HTMLDialogElement>('app-secret-once-dialog dialog');
  if (!dialog) {
    throw new Error('The secret dialog is not in the template');
  }
  return dialog;
}

/** Ticks "I have saved it" and presses Done, as a user does before the secret disappears. */
export async function acknowledgeSecret(fixture: ComponentFixture<unknown>): Promise<void> {
  const dialog = secretDialog(fixture);
  dialog.querySelector<HTMLInputElement>('input[type="checkbox"]')?.click();
  await settle(fixture);
  dialog.querySelector<HTMLButtonElement>('.dialog__actions button')?.click();
  await settle(fixture);
}

/** The open confirm dialog's button with this label. */
export function confirmButton(
  fixture: ComponentFixture<unknown>,
  label: string,
): HTMLButtonElement {
  const match = Array.from(
    element(fixture).querySelectorAll<HTMLButtonElement>('app-confirm-dialog dialog button'),
  ).find((button) => button.textContent?.trim() === label);
  if (!match) {
    throw new Error(`The confirm dialog has no "${label}" button`);
  }
  return match;
}

function holds(value: unknown, secret: string, depth = 0): boolean {
  if (typeof value === 'string') {
    return value.includes(secret);
  }
  if (depth > 4 || value === null || typeof value !== 'object') {
    return false;
  }
  if (value instanceof AbstractControl) {
    return holds(value.value, secret, depth + 1);
  }
  const prototype = Object.getPrototypeOf(value) as unknown;
  if (prototype !== Object.prototype && prototype !== Array.prototype) {
    return false;
  }
  return Object.values(value).some((item) => holds(item, secret, depth + 1));
}

/** True when any field, signal or form control of the component still holds the secret. */
export function retainsSecret(component: object, secret: string): boolean {
  return Object.values(component).some((field) => holds(isSignal(field) ? field() : field, secret));
}
