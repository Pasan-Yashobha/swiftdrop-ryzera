import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { aSession } from './test-data';

/** Signs a merchant user in through the (mocked) login endpoint, as a page under test would find it. */
export function signInMerchantUser(http: HttpTestingController): void {
  TestBed.inject(AuthService).login({ email: 'a@b.lk', password: 'x' }).subscribe();
  http.expectOne('/api/v1/auth/login').flush(aSession());
}
