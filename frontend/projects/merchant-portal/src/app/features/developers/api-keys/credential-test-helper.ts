import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CodeBlock } from '../shared/code-block';
import {
  PING_WITH_ACCESS_TOKEN_CURL,
  PING_WITH_API_KEY_CURL,
  TOKEN_REQUEST_CURL,
  withBaseUrl,
} from '../shared/curl-samples';
import { injectPublicApiUrl } from '../shared/public-api-url';

/** Copy-paste cURL commands to check a credential. They contain placeholders only, never a real secret. */
@Component({
  selector: 'app-credential-test-helper',
  imports: [RouterLink, CodeBlock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card stack" aria-labelledby="test-credentials-heading">
      <div>
        <h2 id="test-credentials-heading">Test your credentials</h2>
        <p class="page__lead small">
          Replace the placeholders with your own values and run the commands in a terminal. A
          successful call returns your merchant name, the environment and the scopes of the
          credential. Never paste a real secret into a chat, ticket or public repository.
        </p>
      </div>
      <h3>With an API key</h3>
      <app-code-block title="cURL: ping with an API key" [code]="pingWithApiKey" />
      <h3>With an OAuth client</h3>
      <p class="muted small">Request an access token, then send it as a Bearer token.</p>
      <app-code-block title="cURL: request an access token" [code]="tokenRequest" />
      <app-code-block title="cURL: ping with the access token" [code]="pingWithAccessToken" />
      <p class="small">
        Next steps: read the <a routerLink="/developers/docs">API documentation</a> to create your
        first order.
      </p>
    </section>
  `,
})
export class CredentialTestHelper {
  private readonly baseUrl = injectPublicApiUrl();

  protected readonly pingWithApiKey = withBaseUrl(PING_WITH_API_KEY_CURL, this.baseUrl);
  protected readonly tokenRequest = withBaseUrl(TOKEN_REQUEST_CURL, this.baseUrl);
  protected readonly pingWithAccessToken = withBaseUrl(PING_WITH_ACCESS_TOKEN_CURL, this.baseUrl);
}
