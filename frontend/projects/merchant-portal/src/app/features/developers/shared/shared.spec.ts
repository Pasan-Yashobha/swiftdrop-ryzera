import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppError } from '../../../core/http/app-error';
import { API_BASE_URL } from '../../../core/config/app-config';
import { CodeBlock } from './code-block';
import {
  PING_WITH_ACCESS_TOKEN_CURL,
  PING_WITH_API_KEY_CURL,
  TOKEN_REQUEST_CURL,
  withBaseUrl,
} from './curl-samples';
import { DeveloperError } from './developer-error';
import { injectPublicApiUrl } from './public-api-url';

describe('CodeBlock', () => {
  function create(code: string) {
    const fixture = TestBed.createComponent(CodeBlock);
    fixture.componentRef.setInput('title', 'cURL: ping');
    fixture.componentRef.setInput('code', code);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the title and the code exactly as given, keeping line breaks and indentation', () => {
    const code = 'curl -i https://x.example/ping \\\n  -H "X-API-Key: YOUR_API_KEY"';

    const root = create(code);

    expect(root.querySelector('figcaption')?.textContent).toContain('cURL: ping');
    expect(root.querySelector('pre code')?.textContent).toBe(code);
  });

  it('can be scrolled with the keyboard', () => {
    expect(create('x').querySelector('pre')?.tabIndex).toBe(0);
  });

  it('copies the code', () => {
    const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();

    create('echo hello').querySelector<HTMLButtonElement>('app-copy-button button')?.click();

    expect(writeText).toHaveBeenCalledWith('echo hello');
  });

  it('does not interpret markup in the code', () => {
    const root = create('<img src=x onerror=alert(1)>');

    expect(root.querySelector('img')).toBeNull();
    expect(root.querySelector('pre')?.textContent).toBe('<img src=x onerror=alert(1)>');
  });
});

describe('cURL samples', () => {
  it('replace every occurrence of the base URL placeholder', () => {
    const filled = withBaseUrl(
      TOKEN_REQUEST_CURL + PING_WITH_API_KEY_CURL,
      'https://h.example/api/v1',
    );

    expect(filled).not.toContain('{{BASE_URL}}');
    expect(filled).toContain('https://h.example/api/v1/oauth/token');
    expect(filled).toContain('https://h.example/api/v1/merchant-api/ping');
  });

  it('contain placeholders instead of credentials', () => {
    expect(PING_WITH_API_KEY_CURL).toContain('X-API-Key: YOUR_API_KEY');
    expect(TOKEN_REQUEST_CURL).toContain('YOUR_CLIENT_ID:YOUR_CLIENT_SECRET');
    expect(PING_WITH_ACCESS_TOKEN_CURL).toContain('Bearer YOUR_ACCESS_TOKEN');
  });
});

describe('injectPublicApiUrl', () => {
  it('joins the page origin and the API base', () => {
    TestBed.configureTestingModule({ providers: [{ provide: API_BASE_URL, useValue: '/api/v1' }] });

    expect(TestBed.runInInjectionContext(() => injectPublicApiUrl())).toBe(
      `${location.origin}/api/v1`,
    );
  });
});

@Component({
  imports: [DeveloperError],
  template: `<app-developer-error
    [error]="error()"
    [retryable]="true"
    (retry)="retries = retries + 1"
  />`,
})
class Host {
  readonly error = signal<AppError | null>(null);
  retries = 0;
}

describe('DeveloperError', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  it('renders nothing without an error', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('');
  });

  it('shows an ordinary error with a retry button', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.error.set(
      new AppError(503, 'PARCEL_SERVICE_UNAVAILABLE', 'Try later.'),
    );
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.textContent).toContain('Try later.');
    root.querySelector<HTMLButtonElement>('button')?.click();
    expect(fixture.componentInstance.retries).toBe(1);
  });

  it('turns MERCHANT_NOT_APPROVED into an explanation with a link to the status page', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.error.set(
      new AppError(403, 'MERCHANT_NOT_APPROVED', 'Your merchant account is pending.'),
    );
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'Your merchant account is pending.',
    );
    expect(root.textContent).toContain('once your business is approved');
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/status');
    expect(root.querySelector('button')).toBeNull();
  });
});
