import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, Observable, switchMap, throwError } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { AuthService } from './auth.service';

const RETRY_HEADER = 'X-Auth-Retry';

/**
 * Attaches the JWT Bearer token to protected API calls.
 * On HTTP 401 Unauthorized, automatically triggers a single token refresh and replays the request.
 * Excludes public tracking and auth endpoints.
 */
export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> => {
  const auth = inject(AuthService);
  const authApi = inject(AuthApi);

  // Do not intercept or attach tokens to auth endpoints or public tracking
  if (authApi.isAuthUrl(req.url) || req.url.includes('/tracking/')) {
    return next(req);
  }

  const token = auth.token();
  const authorizedReq = token && !req.headers.has('Authorization')
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !req.headers.has(RETRY_HEADER)) {
        // Attempt a single refresh and retry
        return auth.refresh().pipe(
          switchMap((newToken) => {
            if (newToken) {
              const retriedReq = req.clone({
                setHeaders: {
                  Authorization: `Bearer ${newToken}`,
                  [RETRY_HEADER]: '1',
                },
              });
              return next(retriedReq);
            }
            auth.logout({ expired: true });
            return throwError(() => error);
          }),
          catchError((refreshErr) => {
            auth.logout({ expired: true });
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
