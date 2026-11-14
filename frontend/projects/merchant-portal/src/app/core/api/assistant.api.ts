import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { ChatReply } from '../models/assistant.model';

/**
 * Assistant service (Member 5). Customers and merchants only. The server keeps the conversation
 * memory; the caller only passes back the id it received.
 */
@Injectable({ providedIn: 'root' })
export class AssistantApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);

  chat(message: string, conversationId: string | null): Observable<ChatReply> {
    return this.http.post<ChatReply>(
      `${this.api}/assistant/chat`,
      conversationId ? { message, conversationId } : { message },
    );
  }

  /** "New chat": the server forgets the conversation. Unknown ids are ignored by the server. */
  forget(conversationId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.api}/assistant/conversations/${encodeURIComponent(conversationId)}`,
    );
  }
}
