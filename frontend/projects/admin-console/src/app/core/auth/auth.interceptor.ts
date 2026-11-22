import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, Observable, switchMap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { AuthApiService } from './auth-api.service';
import { AuthService } from './auth.service';

/**
 * Attaches the bearer token to API calls. When the server answers 401 it refreshes the token once and
 * replays the request; if that does not help the session is ended and the user is sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const authApi = inject(AuthApiService);
  const apiBase = inject(API_BASE_URL);

  if (!request.url.startsWith(`${apiBase}/`) || authApi.isAuthUrl(request.url)) {
    return next(request);
  }

  return next(withToken(request, auth.token())).pipe(
    catchError((error: unknown) => {
      if (!isUnauthorized(error)) {
        return throwError(() => error);
      }
      return auth
        .refresh()
        .pipe(
          switchMap((token) =>
            token ? replay(request, next, token, auth) : endSession(error, auth),
          ),
        );
    }),
  );
};

function replay(
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
  token: string,
  auth: AuthService,
): Observable<HttpEvent<unknown>> {
  return next(withToken(request, token)).pipe(
    catchError((error: unknown) =>
      isUnauthorized(error) ? endSession(error, auth) : throwError(() => error),
    ),
  );
}

function endSession(error: unknown, auth: AuthService): Observable<never> {
  auth.logout({ expired: true });
  return throwError(() => error);
}

function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
