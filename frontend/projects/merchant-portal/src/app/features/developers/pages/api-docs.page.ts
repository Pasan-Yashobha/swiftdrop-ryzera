import { DOCUMENT } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import {
  DOC_SECTIONS,
  ERROR_CODES,
  OPENAPI_PATH,
  ORDER_FIELDS,
  ORDER_STATUS_DOCS,
  RATE_LIMIT_HEADERS,
  WEBHOOK_EVENT_DOCS,
  WEBHOOK_HEADERS,
  WEBHOOK_RETRY_DELAYS,
} from '../docs/api-docs.content';
import {
  ADVANCE_SANDBOX_CURL,
  CREATE_ORDER_BODY,
  CREATE_ORDER_CURL,
  CREATE_ORDER_RESPONSE,
  ERROR_RESPONSE,
  FAIL_SANDBOX_CURL,
  GET_ORDER_CURL,
  LIST_ORDERS_CURL,
  SIGNATURE_VERIFICATION_SAMPLES,
  TOKEN_ERROR_RESPONSE,
  TOKEN_RESPONSE,
  WEBHOOK_PAYLOAD,
  WEBHOOK_REQUEST_EXAMPLE,
} from '../docs/api-docs.samples';
import { CodeTabs } from '../docs/code-tabs';
import { CodeBlock } from '../shared/code-block';
import {
  PING_WITH_ACCESS_TOKEN_CURL,
  PING_WITH_API_KEY_CURL,
  TOKEN_REQUEST_CURL,
  withBaseUrl,
} from '../shared/curl-samples';
import { injectPublicApiUrl } from '../shared/public-api-url';

/** The merchant API guide: static content with a table of contents that follows the reader. */
@Component({
  selector: 'app-api-docs-page',
  imports: [RouterLink, CodeBlock, CodeTabs, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './api-docs.page.html',
  styleUrl: './api-docs.page.css',
})
export class ApiDocsPage {
  protected readonly baseUrl = injectPublicApiUrl();
  protected readonly openApiPath = OPENAPI_PATH;
  protected readonly sections = DOC_SECTIONS;
  protected readonly orderFields = ORDER_FIELDS;
  protected readonly orderStatuses = ORDER_STATUS_DOCS;
  protected readonly errorCodes = ERROR_CODES;
  protected readonly rateLimitHeaders = RATE_LIMIT_HEADERS;
  protected readonly webhookEvents = WEBHOOK_EVENT_DOCS;
  protected readonly webhookHeaders = WEBHOOK_HEADERS;
  protected readonly retryDelays = WEBHOOK_RETRY_DELAYS;
  protected readonly verificationSamples = SIGNATURE_VERIFICATION_SAMPLES;

  protected readonly samples = {
    ping: withBaseUrl(PING_WITH_API_KEY_CURL, this.baseUrl),
    tokenRequest: withBaseUrl(TOKEN_REQUEST_CURL, this.baseUrl),
    pingWithToken: withBaseUrl(PING_WITH_ACCESS_TOKEN_CURL, this.baseUrl),
    createOrderCurl: withBaseUrl(CREATE_ORDER_CURL, this.baseUrl),
    createOrderBody: CREATE_ORDER_BODY,
    createOrderResponse: CREATE_ORDER_RESPONSE,
    getOrder: withBaseUrl(GET_ORDER_CURL, this.baseUrl),
    listOrders: withBaseUrl(LIST_ORDERS_CURL, this.baseUrl),
    advance: withBaseUrl(ADVANCE_SANDBOX_CURL, this.baseUrl),
    failSandbox: withBaseUrl(FAIL_SANDBOX_CURL, this.baseUrl),
    tokenResponse: TOKEN_RESPONSE,
    tokenError: TOKEN_ERROR_RESPONSE,
    error: ERROR_RESPONSE,
    webhookRequest: WEBHOOK_REQUEST_EXAMPLE,
    webhookPayload: WEBHOOK_PAYLOAD,
  };

  /** Only the phone layout collapses the table of contents; on wide screens it is always shown. */
  protected readonly tocOpen = signal(false);

  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly fragment = toSignal(inject(ActivatedRoute).fragment);

  constructor() {
    // Deep links such as /developers/docs#verify-signatures scroll to (and focus) the section heading.
    effect(() => {
      const id = this.fragment();
      if (id) {
        afterNextRender(() => this.reveal(id), { injector: this.injector });
      }
    });
  }

  protected toggleToc(): void {
    this.tocOpen.update((open) => !open);
  }

  private reveal(id: string): void {
    const heading = this.document.getElementById(id);
    heading?.scrollIntoView();
    heading?.focus({ preventScroll: true });
  }
}
