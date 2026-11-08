import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, catchError, delay, of, throwError } from 'rxjs';
import { AuthSession } from '../models/auth.model';
import { ApiKey, ApiKeyCreated, CredentialEnvironment, OAuthClient, OAuthClientCreated } from '../models/credential.model';
import { Merchant } from '../models/merchant.model';
import { BulkResult, Order, OrderDetail, OrderStats } from '../models/order.model';
import { PageResponse } from '../models/page.model';
import { SavedWebhook, WebhookConfig, WebhookDeliveryPage, WebhookTestResult } from '../models/webhook.model';

let mockStatus: 'APPROVED' | 'PENDING' | 'REJECTED' | 'NEW' = 'APPROVED';

export function setMockMerchantStatus(status: 'APPROVED' | 'PENDING' | 'REJECTED' | 'NEW'): void {
  mockStatus = status;
}

export function getMockMerchantStatus(): 'APPROVED' | 'PENDING' | 'REJECTED' | 'NEW' {
  return mockStatus;
}

const MOCK_MERCHANT: Merchant = {
  id: '8e548e25-f14c-4454-811f-352b18de4181',
  businessName: 'Apex Retailers Ltd',
  businessRegNo: 'PV-2026-99881',
  contactEmail: 'dhanushkamg1234@gmail.com',
  contactPhone: '+94713553246',
  addressLine: '123 Galle Road',
  city: 'Colombo 03',
  website: 'https://apexretail.lk',
  status: 'APPROVED',
  decisionReason: null,
  decidedAt: '2026-11-13T10:00:00Z',
  rateLimitPerMinute: 120,
  createdAt: '2026-11-10T08:00:00Z',
  updatedAt: '2026-11-13T10:00:00Z',
};

const MOCK_ORDERS: Order[] = [
  {
    id: 'ord-1049',
    trackingNo: 'SD-2026-1049',
    externalRef: 'INV-9021',
    status: 'DELIVERED',
    sandbox: false,
    source: 'PORTAL',
    trackingUrl: 'http://localhost:4200/track/SD-2026-1049',
    recipient: { name: 'Sunil Silva', phone: '+94778901234' },
    pickup: { addressLine: '123 Galle Road', city: 'Colombo 03', postalCode: '00300' },
    dropoff: { addressLine: '45 Peradeniya Road', city: 'Kandy', postalCode: '20000' },
    parcel: { weightKg: 2.5, size: 'MEDIUM', description: 'Handmade Ceramic Set' },
    notes: 'Handle with care',
    fee: { amount: 650.0, currency: 'LKR' },
    failureReason: null,
    createdAt: '2026-11-13T09:30:00Z',
    updatedAt: '2026-11-13T14:15:00Z',
  },
  {
    id: 'ord-1048',
    trackingNo: 'SD-2026-1048',
    externalRef: 'INV-9020',
    status: 'IN_TRANSIT',
    sandbox: false,
    source: 'API',
    trackingUrl: 'http://localhost:4200/track/SD-2026-1048',
    recipient: { name: 'Ruwan Perera', phone: '+94771239876' },
    pickup: { addressLine: '123 Galle Road', city: 'Colombo 03', postalCode: '00300' },
    dropoff: { addressLine: '12 Main Street', city: 'Galle', postalCode: '80000' },
    parcel: { weightKg: 1.2, size: 'SMALL', description: 'Silk Scarf Box' },
    notes: null,
    fee: { amount: 470.0, currency: 'LKR' },
    failureReason: null,
    createdAt: '2026-11-13T10:00:00Z',
    updatedAt: '2026-11-13T12:45:00Z',
  },
  {
    id: 'ord-1047',
    trackingNo: 'SD-2026-1047',
    externalRef: null,
    status: 'OUT_FOR_DELIVERY',
    sandbox: false,
    source: 'PORTAL',
    trackingUrl: 'http://localhost:4200/track/SD-2026-1047',
    recipient: { name: 'Anura Fernando', phone: '+94714567890' },
    pickup: { addressLine: '123 Galle Road', city: 'Colombo 03', postalCode: '00300' },
    dropoff: { addressLine: '78 Sea Street', city: 'Negombo', postalCode: '11500' },
    parcel: { weightKg: 0.8, size: 'SMALL', description: 'Electronic Gadget' },
    notes: null,
    fee: { amount: 430.0, currency: 'LKR' },
    failureReason: null,
    createdAt: '2026-11-13T11:15:00Z',
    updatedAt: '2026-11-13T13:20:00Z',
  },
  {
    id: 'ord-1046',
    trackingNo: 'SD-2026-1046',
    externalRef: 'BULK-ROW-01',
    status: 'CREATED',
    sandbox: false,
    source: 'BULK',
    trackingUrl: 'http://localhost:4200/track/SD-2026-1046',
    recipient: { name: 'Kasun Jayawardena', phone: '+94765432109' },
    pickup: { addressLine: '123 Galle Road', city: 'Colombo 03', postalCode: '00300' },
    dropoff: { addressLine: '33 Temple Road', city: 'Kurunegala', postalCode: '60000' },
    parcel: { weightKg: 3.0, size: 'LARGE', description: 'Heavy Book Box' },
    notes: null,
    fee: { amount: 750.0, currency: 'LKR' },
    failureReason: null,
    createdAt: '2026-11-13T12:00:00Z',
    updatedAt: '2026-11-13T12:00:00Z',
  },
  {
    id: 'ord-1045',
    trackingNo: 'SBX-8821039',
    externalRef: 'TEST-SANDBOX',
    status: 'CREATED',
    sandbox: true,
    source: 'API',
    trackingUrl: null,
    recipient: { name: 'Demo Recipient', phone: '+94770000000' },
    pickup: { addressLine: 'Sandbox Store', city: 'Colombo', postalCode: null },
    dropoff: { addressLine: 'Sandbox Customer', city: 'Kandy', postalCode: null },
    parcel: { weightKg: 1.0, size: 'SMALL', description: 'Test package' },
    notes: null,
    fee: { amount: 450.0, currency: 'LKR' },
    failureReason: null,
    createdAt: '2026-11-13T12:30:00Z',
    updatedAt: '2026-11-13T12:30:00Z',
  },
];

let mockKeys: ApiKey[] = [
  {
    id: 'key-1',
    label: 'Production E-Commerce Key',
    environment: 'LIVE',
    keyPrefix: 'sd_live_apex_prod',
    status: 'ACTIVE',
    expiresAt: null,
    lastUsedAt: '2026-11-13T14:00:00Z',
    createdAt: '2026-11-10T10:00:00Z',
    revokedAt: null,
  },
  {
    id: 'key-2',
    label: 'Sandbox Staging Key',
    environment: 'TEST',
    keyPrefix: 'sd_test_apex_sbx',
    status: 'ACTIVE',
    expiresAt: null,
    lastUsedAt: '2026-11-13T12:30:00Z',
    createdAt: '2026-11-10T10:05:00Z',
    revokedAt: null,
  },
];

let mockClients: OAuthClient[] = [
  {
    id: 'client-1',
    label: 'Shopify Store Integration',
    clientId: 'mc_live_apex_shopify',
    environment: 'LIVE',
    scopes: ['orders:write', 'orders:read'],
    status: 'ACTIVE',
    lastUsedAt: '2026-11-13T12:00:00Z',
    createdAt: '2026-11-10T10:00:00Z',
    revokedAt: null,
  },
];

let mockWebhookConfig: WebhookConfig = {
  configured: true,
  url: 'https://webhook.site/demo-swiftdrop-channel',
  active: true,
  events: ['*'],
  secretHint: 'whsec_••••••••3a9b',
  lastTestAt: '2026-11-13T14:00:00Z',
  lastTestStatus: 'HTTP 200 OK',
};

/** Mock API Interceptor providing smooth in-browser UI testing without requiring backend services */
export function mockApiInterceptor(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  if (!req.url.includes('/api/')) {
    return next(req);
  }

  const mockResponse = getMockResponse(req);
  if (mockResponse) {
    return of(mockResponse).pipe(delay(50));
  }

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && (error.status === 0 || error.status === 504 || error.status === 503)) {
        const fallback = getMockResponse(req);
        if (fallback) {
          return of(fallback);
        }
      }
      return throwError(() => error);
    }),
  );
}

function getMockResponse(req: HttpRequest<unknown>): HttpResponse<unknown> | null {
  const url = req.url;
  const method = req.method;

  // 1. Auth endpoints
  if (url.includes('/auth/login') || url.includes('/auth/signup') || url.includes('/dev/tokens')) {
    const session: AuthSession = {
      accessToken: 'demo-jwt-token-swiftdrop-merchant',
      refreshToken: 'demo-refresh-token',
      expiresIn: 3600,
      user: {
        id: 'usr-demo-merchant',
        email: 'dhanushkamg1234@gmail.com',
        fullName: 'Dhanushka Madhushanka',
        role: 'MERCHANT',
      },
    };
    return new HttpResponse({ status: 200, body: session });
  }

  // 2. Current merchant profile
  if (url.endsWith('/merchants/me') && method === 'GET') {
    if (mockStatus === 'NEW') {
      return new HttpResponse({ status: 404, body: { code: 'MERCHANT_NOT_REGISTERED', message: 'Not registered' } });
    }
    const merchant: Merchant = {
      ...MOCK_MERCHANT,
      status: mockStatus,
      decisionReason: mockStatus === 'REJECTED' ? 'Business registration document could not be verified with ROC.' : null,
    };
    return new HttpResponse({ status: 200, body: merchant });
  }

  // 3. Register or update merchant profile
  if ((url.endsWith('/merchants/register') && method === 'POST') || (url.endsWith('/merchants/me') && method === 'PUT')) {
    mockStatus = 'PENDING';
    const body = (req.body ?? {}) as Partial<Merchant>;
    const updated: Merchant = {
      ...MOCK_MERCHANT,
      ...body,
      status: 'PENDING',
    };
    return new HttpResponse({ status: 200, body: updated });
  }

  // 4. Order statistics
  if (url.endsWith('/merchants/me/orders/stats')) {
    const stats: OrderStats = {
      total: 48,
      byStatus: {
        CREATED: 5,
        IN_TRANSIT: 12,
        OUT_FOR_DELIVERY: 6,
        DELIVERED: 23,
        FAILED: 2,
      },
      delivered: 23,
      failed: 2,
      inProgress: 18,
      last7Days: [
        { date: '2026-11-07', count: 6 },
        { date: '2026-11-08', count: 8 },
        { date: '2026-11-09', count: 5 },
        { date: '2026-11-10', count: 9 },
        { date: '2026-11-11', count: 7 },
        { date: '2026-11-12', count: 11 },
        { date: '2026-11-13', count: 8 },
      ],
    };
    return new HttpResponse({ status: 200, body: stats });
  }

  // 5. Orders list (paginated)
  if (url.includes('/merchants/me/orders') && method === 'GET') {
    // Check if single order detail request: /orders/{id}
    const parts = url.split('/');
    const lastPart = parts[parts.length - 1].split('?')[0];
    if (lastPart && lastPart !== 'orders' && lastPart !== 'stats' && lastPart !== 'bulk' && lastPart !== 'template') {
      const found = MOCK_ORDERS.find((o) => o.id === lastPart || o.trackingNo === lastPart) ?? MOCK_ORDERS[0];
      const detail: OrderDetail = {
        ...found,
        history: [
          { fromStatus: null, toStatus: 'CREATED', occurredAt: found.createdAt, note: 'Order created' },
          { fromStatus: 'CREATED', toStatus: 'IN_TRANSIT', occurredAt: found.updatedAt, note: 'Dispatched to courier' },
        ],
      };
      return new HttpResponse({ status: 200, body: detail });
    }

    const page: PageResponse<Order> = {
      content: MOCK_ORDERS,
      page: 0,
      size: 20,
      totalElements: MOCK_ORDERS.length,
      totalPages: 1,
    };
    return new HttpResponse({ status: 200, body: page });
  }

  // 6. Create order
  if ((url.includes('/merchants/me/orders') || url.includes('/merchant-api/orders')) && method === 'POST') {
    const newOrder: Order = {
      id: `ord-${Date.now()}`,
      trackingNo: `SD-2026-${Math.floor(1000 + Math.random() * 9000)}`,
      externalRef: 'WEB-NEW',
      status: 'CREATED',
      sandbox: false,
      source: 'PORTAL',
      trackingUrl: 'http://localhost:4200/track/SD-2026-NEW',
      recipient: { name: 'Customer Recipient', phone: '+94771234567' },
      pickup: { addressLine: '123 Galle Road', city: 'Colombo 03', postalCode: '00300' },
      dropoff: { addressLine: '45 Temple Road', city: 'Kandy', postalCode: '20000' },
      parcel: { weightKg: 1.5, size: 'MEDIUM', description: 'Assorted Gifts' },
      notes: null,
      fee: { amount: 550.0, currency: 'LKR' },
      failureReason: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    return new HttpResponse({ status: 201, body: newOrder });
  }

  // 7. Bulk CSV upload
  if (url.includes('/merchants/me/orders/bulk') && method === 'POST') {
    const bulkResult: BulkResult = {
      totalRows: 25,
      created: 25,
      failed: 0,
      results: [
        { row: 2, status: 'CREATED', trackingNo: 'SD-2026-9001', orderId: 'ord-b-01' },
        { row: 3, status: 'CREATED', trackingNo: 'SD-2026-9002', orderId: 'ord-b-02' },
      ],
    };
    return new HttpResponse({ status: 200, body: bulkResult });
  }

  // 8. API Keys list
  if (url.endsWith('/merchants/me/api-keys') && method === 'GET') {
    return new HttpResponse({ status: 200, body: mockKeys });
  }

  // 9. Create API Key
  if (url.endsWith('/merchants/me/api-keys') && method === 'POST') {
    const body = (req.body ?? {}) as { label?: string; environment?: CredentialEnvironment };
    const env: CredentialEnvironment = body.environment === 'TEST' ? 'TEST' : 'LIVE';
    const randomSuffix = Math.random().toString(36).substring(2, 10);
    const prefix = `sd_${env.toLowerCase()}_${randomSuffix}`;
    const newKeyCreated: ApiKeyCreated = {
      id: `key-${Date.now()}`,
      label: body.label ?? 'New API Key',
      environment: env,
      keyPrefix: prefix,
      status: 'ACTIVE',
      expiresAt: null,
      lastUsedAt: null,
      createdAt: new Date().toISOString(),
      revokedAt: null,
      apiKey: `${prefix}_SecKey${Math.random().toString(36).substring(2, 18)}XY`,
    };
    mockKeys = [newKeyCreated, ...mockKeys];
    return new HttpResponse({ status: 201, body: newKeyCreated });
  }

  // 10. Rotate API Key
  if (url.includes('/api-keys/') && url.endsWith('/rotate')) {
    const newKey: ApiKeyCreated = {
      id: `key-${Date.now()}`,
      label: 'Rotated Key',
      environment: 'LIVE',
      keyPrefix: `sd_live_${Math.random().toString(36).substring(2, 10)}`,
      status: 'ACTIVE',
      expiresAt: null,
      lastUsedAt: null,
      createdAt: new Date().toISOString(),
      revokedAt: null,
      apiKey: `sd_live_rot_${Math.random().toString(36).substring(2, 20)}`,
    };
    return new HttpResponse({ status: 200, body: newKey });
  }

  // 11. Revoke API Key
  if (url.includes('/api-keys/') && method === 'DELETE') {
    return new HttpResponse({ status: 204, body: null });
  }

  // 12. OAuth Clients
  if (url.endsWith('/merchants/me/oauth-clients') && method === 'GET') {
    return new HttpResponse({ status: 200, body: mockClients });
  }

  if (url.endsWith('/merchants/me/oauth-clients') && method === 'POST') {
    const body = (req.body ?? {}) as { label?: string; environment?: CredentialEnvironment };
    const env: CredentialEnvironment = body.environment === 'TEST' ? 'TEST' : 'LIVE';
    const created: OAuthClientCreated = {
      id: `client-${Date.now()}`,
      label: body.label ?? 'Store Integration',
      clientId: `mc_${env.toLowerCase()}_${Math.random().toString(36).substring(2, 10)}`,
      environment: env,
      scopes: ['orders:write', 'orders:read'],
      status: 'ACTIVE',
      lastUsedAt: null,
      createdAt: new Date().toISOString(),
      revokedAt: null,
      clientSecret: `cs_${Math.random().toString(36).substring(2, 22)}`,
    };
    mockClients = [created, ...mockClients];
    return new HttpResponse({ status: 201, body: created });
  }

  // 13. Webhook config
  if (url.endsWith('/merchants/me/webhook') && method === 'GET') {
    return new HttpResponse({ status: 200, body: mockWebhookConfig });
  }

  if (url.endsWith('/merchants/me/webhook') && method === 'PUT') {
    const body = (req.body ?? {}) as Partial<WebhookConfig>;
    mockWebhookConfig = { ...mockWebhookConfig, ...body } as WebhookConfig;
    const saved: SavedWebhook = {
      configured: true,
      url: (body as { url?: string }).url ?? 'https://webhook.site/demo-swiftdrop-channel',
      active: true,
      events: ['*'],
      secretHint: 'whsec_••••••••3a9b',
      lastTestAt: new Date().toISOString(),
      lastTestStatus: 'HTTP 200 OK',
      secret: 'whsec_demo_signing_secret_998124',
    };
    return new HttpResponse({ status: 200, body: saved });
  }

  // 14. Webhook test ping
  if (url.endsWith('/merchants/me/webhook/test')) {
    const testResult: WebhookTestResult = {
      delivered: true,
      httpStatus: 200,
      durationMs: 135,
      error: null,
    };
    return new HttpResponse({ status: 200, body: testResult });
  }

  // 15. Webhook delivery log
  if (url.includes('/merchants/me/webhook/deliveries')) {
    const deliveries: WebhookDeliveryPage = {
      available: true,
      content: [
        {
          id: 'del-101',
          eventId: 'evt-out-for-delivery-101',
          eventType: 'parcel.status.out_for_delivery',
          status: 'DELIVERED',
          attempts: 1,
          lastHttpStatus: 200,
          durationMs: 120,
          createdAt: '2026-11-13T13:20:00Z',
          lastAttemptAt: '2026-11-13T13:20:01Z',
          error: null,
        },
        {
          id: 'del-100',
          eventId: 'evt-in-transit-100',
          eventType: 'parcel.status.in_transit',
          status: 'DELIVERED',
          attempts: 1,
          lastHttpStatus: 200,
          durationMs: 95,
          createdAt: '2026-11-13T12:45:00Z',
          lastAttemptAt: '2026-11-13T12:45:01Z',
          error: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    };
    return new HttpResponse({ status: 200, body: deliveries });
  }

  return null;
}
