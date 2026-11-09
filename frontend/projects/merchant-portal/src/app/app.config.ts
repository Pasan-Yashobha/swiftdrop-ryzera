import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  APP_INITIALIZER,
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import {
  provideRouter,
  TitleStrategy,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { PageTitleStrategy } from './core/config/page-title.strategy';
import { errorInterceptor } from './core/http/error.interceptor';
import { mockApiInterceptor } from './core/http/mock-api.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // anchorScrolling makes deep links such as /developers/docs#verify-signatures scroll to their section.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    // mockApiInterceptor provides seamless offline/standalone demo UI testing
    provideHttpClient(withInterceptors([mockApiInterceptor, errorInterceptor, authInterceptor])),
    { provide: TitleStrategy, useClass: PageTitleStrategy },
    // On app startup, attempt to hydrate the session from an HttpOnly refreshToken cookie set by
    // the backend after a Google OAuth redirect. This runs before any route guard evaluates.
    {
      provide: APP_INITIALIZER,
      useFactory: (auth: AuthService) => () => firstValueFrom(auth.hydrateFromCookie()),
      deps: [AuthService],
      multi: true,
    },
  ],
};
