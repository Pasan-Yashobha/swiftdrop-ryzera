import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { ServiceHealth } from '../models/system.model';

/** The gateway's health aggregator (member 1): one call that checks every service. */
@Injectable({ providedIn: 'root' })
export class SystemAdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/system`;

  health(): Observable<ServiceHealth[]> {
    return this.http.get<ServiceHealth[]>(`${this.base}/health`);
  }
}
