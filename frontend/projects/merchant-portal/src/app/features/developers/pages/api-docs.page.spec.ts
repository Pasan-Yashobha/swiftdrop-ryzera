import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ERROR_CODES, DOC_SECTIONS, WEBHOOK_EVENT_DOCS } from '../docs/api-docs.content';
import { settle } from '../testing/developer-spec-helpers';
import { ApiDocsPage } from './api-docs.page';

describe('ApiDocsPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'docs', component: ApiDocsPage }])],
    });
  });

  async function open(
    url = '/docs',
  ): Promise<{ root: HTMLElement; harness: RouterTestingHarness }> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ApiDocsPage);
    await settle(harness.fixture);
    return { root: harness.routeNativeElement as HTMLElement, harness };
  }

  const text = (root: HTMLElement) => root.textContent?.replace(/\s+/g, ' ') ?? '';

  describe('overview', () => {
    it('shows the base URL and links to the OpenAPI document', async () => {
      const { root } = await open();

      expect(root.querySelector('h1')?.textContent).toBe('API documentation');
      expect(text(root)).toContain(`${location.origin}/api/v1`);
      const links = Array.from(root.querySelectorAll<HTMLAnchorElement>('a[href$="openapi.yaml"]'));
      expect(links.length).toBeGreaterThan(0);
      for (const link of links) {
        expect(link.getAttribute('href')).toBe('/api/v1/merchant-api/openapi.yaml');
        expect(link.rel).toContain('noopener');
      }
    });

    it('fills the real API address into every cURL example', async () => {
      const { root } = await open();

      const samples = Array.from(root.querySelectorAll('pre')).map((pre) => pre.textContent ?? '');
      expect(samples.some((sample) => sample.startsWith('curl'))).toBeTrue();
      expect(samples.join('\n')).not.toContain('{{BASE_URL}}');
      expect(samples.join('\n')).toContain(`${location.origin}/api/v1/merchant-api/orders`);
    });

    it('never shows anything that looks like a real credential', async () => {
      const { root } = await open();

      expect(text(root)).not.toMatch(/sd_(live|test)_[a-z0-9]{8}_[A-Za-z0-9_-]{20,}/);
      expect(text(root)).not.toMatch(/whsec_[A-Za-z0-9_-]{20,}/);
    });
  });

  describe('table of contents', () => {
    it('links to every section, in reading order', async () => {
      const { root } = await open();

      const links = Array.from(root.querySelectorAll<HTMLAnchorElement>('.toc a'));
      expect(links.map((link) => link.textContent?.trim())).toEqual(
        DOC_SECTIONS.map((section) => section.title),
      );
      expect(links.map((link) => link.getAttribute('href'))).toEqual(
        DOC_SECTIONS.map((section) => `/docs#${section.id}`),
      );
    });

    it('has a heading for each entry, and each section is named by it', async () => {
      const { root } = await open();

      for (const section of DOC_SECTIONS) {
        const heading = root.querySelector(`h2#${section.id}`);
        expect(heading?.textContent?.trim()).toBe(section.title);
        expect(heading?.closest('section')?.getAttribute('aria-labelledby')).toBe(section.id);
      }
    });

    it('is a navigation landmark and collapsed on small screens until opened', async () => {
      const { root, harness } = await open();
      const toggle = root.querySelector<HTMLButtonElement>('.toc__toggle') as HTMLButtonElement;
      const list = root.querySelector('#docs-toc-list') as HTMLElement;

      expect(root.querySelector('nav.toc')?.getAttribute('aria-label')).toBe('On this page');
      expect(toggle.getAttribute('aria-controls')).toBe('docs-toc-list');
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(list.classList).not.toContain('toc__list--open');

      toggle.click();
      harness.detectChanges();
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      expect(list.classList).toContain('toc__list--open');

      toggle.click();
      harness.detectChanges();
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
    });

    it('closes the list after a section is chosen and moves focus to its heading', async () => {
      const { root, harness } = await open();
      const toggle = root.querySelector<HTMLButtonElement>('.toc__toggle') as HTMLButtonElement;
      toggle.click();
      harness.detectChanges();

      const link = Array.from(root.querySelectorAll<HTMLAnchorElement>('.toc a')).find(
        (candidate) => candidate.textContent?.trim() === 'Errors',
      ) as HTMLAnchorElement;
      link.click();
      await harness.fixture.whenStable();
      await settle(harness.fixture);

      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(TestBed.inject(Router).url).toBe('/docs#errors');
      expect(document.activeElement?.id).toBe('errors');
    });

    it('opens at the section named in the address', async () => {
      await open('/docs#verify-signatures');

      expect(document.activeElement?.id).toBe('verify-signatures');
    });

    it('ignores an address fragment that matches no section', async () => {
      const { root } = await open('/docs#nonsense');

      expect(root.querySelector('h1')).not.toBeNull();
      expect(document.activeElement?.id).not.toBe('nonsense');
    });
  });

  describe('content', () => {
    it('explains live and test environments including the advance endpoint', async () => {
      const { root } = await open();

      const section = text(root.querySelector('#environments')?.closest('section') as HTMLElement);
      expect(section).toContain('sd_live_');
      expect(section).toContain('sd_test_');
      expect(section).toContain('SBX');
      expect(section).toContain('/advance');
      expect(section).toContain('SANDBOX_ONLY');
    });

    it('documents both authentication methods with the exact requests', async () => {
      const { root } = await open();

      const section = text(
        root.querySelector('#authentication')?.closest('section') as HTMLElement,
      );
      expect(section).toContain('X-API-Key');
      expect(section).toContain('grant_type=client_credentials');
      expect(section).toContain('"token_type": "Bearer"');
      expect(section).toContain('"expires_in": 3600');
      expect(section).toContain('Authorization: Bearer YOUR_ACCESS_TOKEN');
    });

    it('documents order creation with a field table, idempotency and external references', async () => {
      const { root } = await open();

      const section = root.querySelector('#create-order')?.closest('section') as HTMLElement;
      expect(section.querySelectorAll('tbody tr').length).toBeGreaterThan(10);
      expect(text(section)).toContain('Idempotency-Key');
      expect(text(section)).toContain('Idempotent-Replayed: true');
      expect(text(section)).toContain('IDEMPOTENCY_KEY_REUSED');
      expect(text(section)).toContain('DUPLICATE_EXTERNAL_REF');
      expect(text(section)).toContain('"trackingNo": "SD24000123"');
    });

    it('documents how to retrieve one order and list orders', async () => {
      const { root } = await open();

      const section = text(
        root.querySelector('#retrieve-orders')?.closest('section') as HTMLElement,
      );
      expect(section).toContain('/merchant-api/orders/{trackingNo}');
      expect(section).toContain('externalRef');
      expect(section).toContain('size');
    });

    it('shows the status lifecycle and a row per status', async () => {
      const { root } = await open();

      const section = root.querySelector('#lifecycle')?.closest('section') as HTMLElement;
      expect(text(section)).toContain(
        'CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED or FAILED',
      );
      expect(section.querySelectorAll('tbody tr').length).toBe(10);
    });

    it('lists every error code in the error table', async () => {
      const { root } = await open();

      const section = root.querySelector('#errors')?.closest('section') as HTMLElement;
      const codes = Array.from(section.querySelectorAll('tbody th code')).map(
        (code) => code.textContent,
      );
      expect(codes).toEqual(ERROR_CODES.map((item) => item.code));
      expect(text(section)).toContain('correlationId');
    });

    it('documents the rate limit headers', async () => {
      const { root } = await open();

      const section = text(root.querySelector('#rate-limits')?.closest('section') as HTMLElement);
      expect(section).toContain('X-RateLimit-Limit');
      expect(section).toContain('X-RateLimit-Remaining');
      expect(section).toContain('Retry-After');
      expect(section).toContain('429 RATE_LIMITED');
    });

    it('documents webhook events, headers, retries and the delivery rules', async () => {
      const { root } = await open();

      const section = root.querySelector('#webhooks')?.closest('section') as HTMLElement;
      const eventTable = section.querySelector('table') as HTMLTableElement;
      const events = Array.from(eventTable.querySelectorAll('tbody th code')).map(
        (code) => code.textContent,
      );
      expect(events).toEqual(WEBHOOK_EVENT_DOCS.map((event) => event.type));
      const content = text(section);
      for (const header of [
        'X-SwiftDrop-Event-Id',
        'X-SwiftDrop-Event-Type',
        'X-SwiftDrop-Timestamp',
        'X-SwiftDrop-Signature',
      ]) {
        expect(content).toContain(header);
      }
      expect(content).toContain('5 seconds, 30 seconds, 2 minutes, 10 minutes, 30 minutes');
      expect(content).toContain('within 5 seconds');
      expect(content).toContain('Any other 4xx status fails the delivery immediately');
    });

    it('explains the four verification checks', async () => {
      const { root } = await open();

      const section = text(
        root.querySelector('#verify-signatures')?.closest('section') as HTMLElement,
      );
      expect(section).toContain('timestamp + "." + body');
      expect(section).toContain('constant-time');
      expect(section).toContain('more than 5 minutes');
      expect(section).toContain('X-SwiftDrop-Event-Id');
      expect(section).toContain('occurredAt');
    });

    it('offers verification samples for Node.js, Python and Java in tabs', async () => {
      const { root } = await open();

      const section = root.querySelector('#verify-signatures')?.closest('section') as HTMLElement;
      const labels = Array.from(section.querySelectorAll('[role="tab"]')).map((tab) =>
        tab.textContent?.trim(),
      );
      expect(labels).toEqual(['Node.js', 'Python', 'Java']);
      expect(section.querySelector('[role="tablist"]')?.getAttribute('aria-label')).toContain(
        'Language',
      );
    });
  });
});
