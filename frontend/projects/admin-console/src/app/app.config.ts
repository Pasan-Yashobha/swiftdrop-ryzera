import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, TitleStrategy, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { PageTitleStrategy } from './core/config/page-title.strategy';
import { errorInterceptor } from './core/http/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // errorInterceptor wraps authInterceptor, so it sees the outcome after a token refresh was attempted.
    provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
    { provide: TitleStrategy, useClass: PageTitleStrategy },
  ],
};
