import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { element } from '../../../core/testing/dom';
import { anApiKey, anOAuthClient } from '../testing/developer-spec-helpers';
import { ApiKeysPage } from './api-keys.page';

describe('ApiKeysPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows API keys, OAuth clients and the credential test helper under clear headings', () => {
    const fixture = TestBed.createComponent(ApiKeysPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/merchants/me/api-keys').flush([anApiKey()]);
    http.expectOne('/api/v1/merchants/me/oauth-clients').flush([anOAuthClient()]);
    fixture.detectChanges();

    const headings = Array.from(element(fixture).querySelectorAll('h1, h2')).map((heading) =>
      heading.textContent?.trim(),
    );
    expect(headings).toEqual(['API keys', 'API keys', 'OAuth clients', 'Test your credentials']);
    expect(element(fixture).querySelectorAll('tbody tr').length).toBe(2);
  });
});
