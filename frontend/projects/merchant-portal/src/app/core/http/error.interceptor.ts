import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { MerchantStore } from '../merchant/merchant.store';
import { fromHttpError } from './app-error';

/**
 * Turns every HTTP failure into an AppError so components never see raw server text.
 * It also reacts to a merchant losing approval mid-session by sending them to the status page.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const merchantStore = inject(MerchantStore);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const appError = fromHttpError(error);
      if (appError.code === 'MERCHANT_NOT_APPROVED') {
        merchantStore.invalidate();
        void router.navigateByUrl('/status');
      }
      return throwError(() => appError);
    }),
  );
};
