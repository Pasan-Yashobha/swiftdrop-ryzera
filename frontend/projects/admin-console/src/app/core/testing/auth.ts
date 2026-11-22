import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { UserRole } from '../models/auth.model';
import { aSession } from './test-data';

/** Signs a user in through the (mocked) login endpoint, as a page under test would find it. */
export function signIn(http: HttpTestingController, role: UserRole = 'ADMIN'): void {
  TestBed.inject(AuthService).login({ email: 'ops@swiftdrop.example', password: 'x' }).subscribe();
  http.expectOne('/api/v1/auth/login').flush(aSession({}, role));
}
