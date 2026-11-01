import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { Merchant } from './core/models/merchant.model';
import { signInMerchantUser } from './core/testing/auth';
import { aMerchant } from './core/testing/test-data';

/** Verifies that the route table and the guards together send each kind of visitor to the right page. */
describe('app routes', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  const signIn = () => signInMerchantUser(http);

  /** Answers the pending GET /merchants/me once the router has issued it. */
  async function answerMerchant(merchant: Merchant | null): Promise<void> {
    for (let attempt = 0; attempt < 100; attempt++) {
      const [request] = http.match('/api/v1/merchants/me');
      if (request) {
        if (merchant) {
          request.flush(merchant);
        } else {
          request.flush(
            { code: 'MERCHANT_NOT_REGISTERED', message: 'none' },
            { status: 404, statusText: 'Not Found' },
          );
        }
        return;
      }
      await new Promise((resolve) => setTimeout(resolve));
    }
    throw new Error('The router never asked for the merchant');
  }

  async function open(url: string, merchant: Merchant | null): Promise<string> {
    const navigation = harness.navigateByUrl(url);
    await answerMerchant(merchant);
    await navigation;
    return TestBed.inject(Router).url;
  }

  it('sends anonymous visitors to the login page and remembers the target', async () => {
    await harness.navigateByUrl('/orders/42');

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Forders%2F42');
  });

  it('shows the login and signup pages to anonymous visitors', async () => {
    await harness.navigateByUrl('/signup');

    expect(TestBed.inject(Router).url).toBe('/signup');
  });

  it('sends a signed-in merchant away from the login page', async () => {
    signIn();

    expect(await open('/login', aMerchant())).toBe('/dashboard');
  });

  it('sends a merchant without a business to onboarding', async () => {
    signIn();

    expect(await open('/dashboard', null)).toBe('/onboarding');
  });

  for (const status of ['PENDING', 'REJECTED', 'SUSPENDED'] as const) {
    it(`keeps a ${status} merchant out of orders and developer pages`, async () => {
      signIn();

      expect(await open('/orders/new', aMerchant({ status }))).toBe('/status');

      await harness.navigateByUrl('/developers/keys'); // the merchant is cached now
      expect(TestBed.inject(Router).url).toBe('/status');
    });
  }

  it('lets an approved merchant open every portal section', async () => {
    signIn();
    expect(await open('/dashboard', aMerchant())).toBe('/dashboard');

    for (const url of [
      '/orders',
      '/orders/new',
      '/orders/bulk',
      '/orders/abc',
      '/developers/keys',
      '/developers/webhooks',
      '/developers/docs',
    ]) {
      await harness.navigateByUrl(url);
      expect(TestBed.inject(Router).url).toBe(url);
    }
  });

  it('redirects /developers to the API keys page', async () => {
    signIn();

    expect(await open('/developers', aMerchant())).toBe('/developers/keys');
  });

  it('does not let an approved merchant register again', async () => {
    signIn();

    expect(await open('/onboarding', aMerchant())).toBe('/dashboard');
  });

  it('shows the status page to a pending merchant and the profile to any merchant', async () => {
    signIn();
    expect(await open('/status', aMerchant({ status: 'PENDING' }))).toBe('/status');

    await harness.navigateByUrl('/profile');
    expect(TestBed.inject(Router).url).toBe('/profile');
  });

  it('shows a not-found page for unknown addresses', async () => {
    await harness.navigateByUrl('/nowhere');

    expect(harness.routeNativeElement?.textContent).toContain('could not find that page');
  });
});
