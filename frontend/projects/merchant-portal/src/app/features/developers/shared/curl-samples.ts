/** Placeholder replaced by the public API base URL (see `withBaseUrl`). */
export const BASE_URL = '{{BASE_URL}}';

/** Checks that an API key works. Only placeholders appear here, never a real secret. */
export const PING_WITH_API_KEY_CURL = `curl -i ${BASE_URL}/merchant-api/ping \\
  -H "X-API-Key: YOUR_API_KEY"`;

/** Exchanges OAuth client credentials for a one-hour access token. */
export const TOKEN_REQUEST_CURL = `curl -X POST ${BASE_URL}/oauth/token \\
  -u "YOUR_CLIENT_ID:YOUR_CLIENT_SECRET" \\
  -d "grant_type=client_credentials" \\
  -d "scope=orders:write orders:read"`;

export const PING_WITH_ACCESS_TOKEN_CURL = `curl -i ${BASE_URL}/merchant-api/ping \\
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"`;

/** Fills in the API base URL, for example `https://portal.example.com/api/v1`. */
export function withBaseUrl(sample: string, baseUrl: string): string {
  return sample.replaceAll(BASE_URL, baseUrl);
}
