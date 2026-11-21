import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { AuditLog, AuditQuery, ChainVerification } from '../models/audit.model';
import { PageResponse } from '../models/page.model';
import { toHttpParams } from './query-params';

/** Read-only admin endpoints of the Audit Log Service (member 1). */
@Injectable({ providedIn: 'root' })
export class AuditAdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/audit-logs`;

  list(query: AuditQuery): Observable<PageResponse<AuditLog>> {
    return this.http.get<PageResponse<AuditLog>>(this.base, { params: toHttpParams(query) });
  }

  /** Re-computes the whole hash chain on the server. */
  verify(): Observable<ChainVerification> {
    return this.http.get<ChainVerification>(`${this.base}/verify`);
  }
}
