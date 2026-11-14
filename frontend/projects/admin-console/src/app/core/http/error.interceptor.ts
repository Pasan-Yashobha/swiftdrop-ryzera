import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { fromHttpError } from './app-error';

/** Turns every HTTP failure into an AppError so components show a safe message (the server's for 4xx). */
export const errorInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: unknown) =>
      throwError(() => (error instanceof HttpErrorResponse ? fromHttpError(error) : error)),
    ),
  );
