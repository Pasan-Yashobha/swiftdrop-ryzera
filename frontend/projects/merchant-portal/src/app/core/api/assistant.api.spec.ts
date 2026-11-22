import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AssistantApi } from './assistant.api';

describe('AssistantApi', () => {
  let api: AssistantApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AssistantApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts a conversation without an id', () => {
    api.chat('hello', null).subscribe();
    expect(http.expectOne('/api/v1/assistant/chat').request.body).toEqual({ message: 'hello' });
  });

  it('continues a conversation with its id', () => {
    api.chat('and Express?', 'c-1').subscribe();
    expect(http.expectOne('/api/v1/assistant/chat').request.body).toEqual({
      message: 'and Express?',
      conversationId: 'c-1',
    });
  });

  it('forgets a conversation with DELETE', () => {
    api.forget('c-1').subscribe();
    expect(http.expectOne('/api/v1/assistant/conversations/c-1').request.method).toBe('DELETE');
  });
});
