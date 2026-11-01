import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CredentialTestHelper } from './credential-test-helper';

describe('CredentialTestHelper', () => {
  function render(): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(CredentialTestHelper);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function samples(root: HTMLElement): string[] {
    return Array.from(root.querySelectorAll('pre')).map((pre) => pre.textContent ?? '');
  }

  it('shows the three commands with the real API address', () => {
    const [apiKey, token, bearer] = samples(render());

    expect(apiKey).toContain(`curl -i ${location.origin}/api/v1/merchant-api/ping`);
    expect(apiKey).toContain('-H "X-API-Key: YOUR_API_KEY"');
    expect(token).toContain(`curl -X POST ${location.origin}/api/v1/oauth/token`);
    expect(token).toContain('-u "YOUR_CLIENT_ID:YOUR_CLIENT_SECRET"');
    expect(token).toContain('-d "grant_type=client_credentials"');
    expect(bearer).toContain('-H "Authorization: Bearer YOUR_ACCESS_TOKEN"');
  });

  it('uses placeholders only, never a real looking credential', () => {
    const all = samples(render()).join('\n');

    expect(all).not.toMatch(/sd_(live|test)_[a-z0-9]{8}_/);
    expect(all).not.toMatch(/mc_[a-z0-9]{20}/);
    expect(all).not.toContain('{{BASE_URL}}');
  });

  it('lets the user copy each command', () => {
    const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    const root = render();
    const buttons = root.querySelectorAll<HTMLButtonElement>('app-copy-button button');

    expect(buttons.length).toBe(3);
    buttons[1].click();

    expect(writeText).toHaveBeenCalledWith(samples(root)[1]);
  });

  it('points to the documentation', () => {
    const link = render().querySelector<HTMLAnchorElement>('a[href="/developers/docs"]');

    expect(link?.textContent).toContain('API documentation');
  });
});
