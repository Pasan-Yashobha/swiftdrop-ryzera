import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { ServiceHealth } from '../../core/models/system.model';
import { buttonByText, element } from '../../core/testing/dom';
import { aServiceHealth } from '../../core/testing/platform-test-data';
import { SystemStatusPage } from './system-status.page';

const URL = '/api/v1/admin/system/health';

describe('SystemStatusPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const healthRequest = () => http.expectOne((r) => r.url === URL && r.method === 'GET');

  function create(services: ServiceHealth[]): ComponentFixture<SystemStatusPage> {
    const fixture = TestBed.createComponent(SystemStatusPage);
    fixture.detectChanges();
    healthRequest().flush(services);
    fixture.detectChanges();
    return fixture;
  }

  function tiles(fixture: ComponentFixture<SystemStatusPage>): HTMLElement[] {
    return Array.from(element(fixture).querySelectorAll<HTMLElement>('.tile'));
  }

  it('shows one tile per service with its status and response time', () => {
    const fixture = create([
      aServiceHealth({ service: 'audit', status: 'UP', responseMs: 8 }),
      aServiceHealth({ service: 'identity', status: 'UP', responseMs: 12 }),
    ]);

    const items = tiles(fixture);
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('audit');
    expect(items[0].textContent).toContain('Up');
    expect(items[0].textContent).toContain('8 ms');
    expect(element(fixture).textContent).toContain('2 of 2 services are up');
  });

  it('highlights a service that is down and counts only the ones that are up', () => {
    const fixture = create([
      aServiceHealth({ service: 'audit', status: 'DOWN', responseMs: 2000 }),
      aServiceHealth({ service: 'identity' }),
      aServiceHealth({ service: 'merchant' }),
    ]);

    const items = tiles(fixture);
    expect(items[0].classList).toContain('tile--down');
    expect(items[0].textContent).toContain('Down');
    expect(items[1].classList).not.toContain('tile--down');
    expect(element(fixture).textContent).toContain('2 of 3 services are up');
    expect(element(fixture).querySelector('.summary--bad')).not.toBeNull();
  });

  it('says so when the gateway reports no services', () => {
    const fixture = create([]);

    expect(element(fixture).textContent).toContain('No services reported');
    expect(tiles(fixture).length).toBe(0);
  });

  it('checks again when asked', () => {
    const fixture = create([aServiceHealth()]);

    buttonByText(fixture, 'Check now').click();
    healthRequest().flush([aServiceHealth({ service: 'identity', status: 'DOWN' })]);
    fixture.detectChanges();

    expect(tiles(fixture)[0].textContent).toContain('Down');
  });

  it('shows a message and a Try again button when the gateway cannot be reached', () => {
    const fixture = TestBed.createComponent(SystemStatusPage);
    fixture.detectChanges();
    healthRequest().flush({ message: 'Boom' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(element(fixture).querySelector('app-error-alert [role="alert"]')).not.toBeNull();

    buttonByText(fixture, 'Try again').click();
    healthRequest().flush([aServiceHealth()]);
    fixture.detectChanges();

    expect(tiles(fixture).length).toBe(1);
  });
});
