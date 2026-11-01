import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ApiKeysSection } from '../api-keys/api-keys-section';
import { CredentialTestHelper } from '../api-keys/credential-test-helper';
import { OAuthClientsSection } from '../api-keys/oauth-clients-section';

/** Credentials for the merchant API: API keys, OAuth clients and a way to check them. */
@Component({
  selector: 'app-api-keys-page',
  imports: [ApiKeysSection, OAuthClientsSection, CredentialTestHelper],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <div class="page__header">
        <div>
          <h1>API keys</h1>
          <p class="page__lead">
            Credentials your server uses to create and track orders through the merchant API.
          </p>
        </div>
      </div>
      <app-api-keys-section />
      <app-oauth-clients-section />
      <app-credential-test-helper />
    </div>
  `,
})
export class ApiKeysPage {}
