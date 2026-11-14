import { HttpParams } from '@angular/common/http';

type ParamValue = string | number | boolean | null | undefined;

/** Builds query parameters, leaving out null, undefined and blank values so the server sees only real filters. */
export function toHttpParams(values: object): HttpParams {
  let params = new HttpParams();
  for (const [key, raw] of Object.entries(values) as [string, ParamValue][]) {
    if (raw === null || raw === undefined) {
      continue;
    }
    const value = String(raw).trim();
    if (value !== '') {
      params = params.set(key, value);
    }
  }
  return params;
}
