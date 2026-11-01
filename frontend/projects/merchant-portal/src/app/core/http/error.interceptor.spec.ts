import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MerchantStore } from '../merchant/merchant.store';
import { AppError } from './app-error';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => http.verify());

  function failWith(status: number, body: object | string | null): AppError {
    let failure: unknown;
    client.get('/api/v1/anything').subscribe({ error: (error) => (failure = error) });
    http.expectOne('/api/v1/anything').flush(body, { status, statusText: 'x' });
    return failure as AppError;
  }

  it('maps a contract error body to an AppError', () => {
    const error = failWith(409, {
      code: 'DUPLICATE_EXTERNAL_REF',
      message: 'External reference already used',
      correlationId: 'c-1',
    });

    expect(error).toBeInstanceOf(AppError);
    expect(error.status).toBe(409);
    expect(error.code).toBe('DUPLICATE_EXTERNAL_REF');
    expect(error.message).toBe('External reference already used');
    expect(error.correlationId).toBe('c-1');
  });

  it('replaces server text on 5xx with a generic message', () => {
    const error = failWith(500, {
      code: 'INTERNAL_ERROR',
      message: 'org.postgresql.util.PSQLException: relation "x"',
    });

    expect(error.message).toBe('Something went wrong on our side. Please try again.');
  });

  it('lets successful responses through unchanged', () => {
    let body: unknown;
    client.get('/api/v1/anything').subscribe((value) => (body = value));
    http.expectOne('/api/v1/anything').flush({ ok: 1 });

    expect(body).toEqual({ ok: 1 });
  });

  it('sends a merchant who lost approval to the status page after forgetting the cached merchant', () => {
    const store = TestBed.inject(MerchantStore);
    spyOn(store, 'invalidate');

    failWith(403, { code: 'MERCHANT_NOT_APPROVED', message: 'Merchant is not approved' });

    expect(store.invalidate).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/status');
  });

  it('does not redirect for other forbidden errors', () => {
    failWith(403, { code: 'FORBIDDEN', message: 'no' });

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
