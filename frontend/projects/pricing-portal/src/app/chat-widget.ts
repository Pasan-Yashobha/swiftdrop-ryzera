import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PricingApi, ToolTrace, errorMessage } from './pricing-api';

const ASSISTANT_HINT = 'the Assistant Service is running on port 8088';
const MAX_CHARS = 1000;

/**
 * The model often answers in Markdown. Replies are shown as plain text for safety,
 * so remove the formatting symbols instead of rendering them as HTML.
 */
export function plainText(reply: string): string {
  return reply
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    .replace(/(^|[\s(])\*(?!\s)(.+?)\*(?=[\s.,!?;:)]|$)/gm, '$1$2')
    .replace(/^\s*[*-]\s+/gm, '• ')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/`([^`]+)`/g, '$1');
}

interface ChatLine {
  from: 'user' | 'assistant';
  text: string;
  tools: ToolTrace[];
}

/**
 * Floating AI assistant for Customer and Merchant accounts.
 * The server keeps the conversation memory; this widget only keeps the id it was given.
 * Replies are rendered as plain text (interpolation), never as HTML, so model output cannot inject markup.
 */
@Component({
  selector: 'app-chat-widget',
  imports: [FormsModule],
  template: `
    @if (open()) {
      <section class="chat-panel" role="dialog" aria-label="SwiftDrop assistant">
        <header class="chat-head">
          <div>
            <p class="eyebrow">SWIFTDROP ASSISTANT</p>
            <strong>Ask about fees, tracking or history</strong>
          </div>
          <button class="quiet" [disabled]="busy()" (click)="newChat()">New chat</button>
          <button class="chat-close" aria-label="Close assistant" (click)="open.set(false)">
            ×
          </button>
        </header>
        <div class="chat-log" #log aria-live="polite">
          @for (line of lines(); track $index) {
            <div class="bubble" [class.mine]="line.from === 'user'">
              <p>{{ line.text }}</p>
              @if (line.tools.length) {
                <ul class="tool-list" aria-label="Tools used">
                  @for (call of line.tools; track $index) {
                    <li [class.failed]="call.failed">
                      <span class="mono">{{ call.tool }}</span>
                      <small>{{ describe(call) }}</small>
                    </li>
                  }
                </ul>
              }
            </div>
          } @empty {
            <div class="chat-empty">
              <p>Try one of these:</p>
              @for (example of examples; track example) {
                <button class="secondary" [disabled]="busy()" (click)="send(example)">
                  {{ example }}
                </button>
              }
            </div>
          }
          @if (busy()) {
            <p class="bubble typing" role="status">Thinking…</p>
          }
        </div>
        @if (error()) {
          <p class="error chat-error" role="alert">{{ error() }}</p>
        }
        <form class="chat-input" (ngSubmit)="send(draft)">
          <label class="sr-only" for="chat-message">Message</label>
          <textarea
            id="chat-message"
            name="message"
            rows="2"
            [maxlength]="maxChars"
            placeholder="How much to send 2 kg from Colombo to Kandy?"
            [(ngModel)]="draft"
            (keydown.enter)="onEnter($event)"
          ></textarea>
          <div>
            <small>{{ draft.length }} / {{ maxChars }}</small>
            <button class="primary" [disabled]="busy() || !draft.trim()">Send</button>
          </div>
        </form>
      </section>
    }
    <button class="chat-launcher" [attr.aria-expanded]="open()" (click)="open.set(!open())">
      {{ open() ? 'Close assistant' : 'Ask SwiftDrop' }}
    </button>
  `,
})
export class ChatWidget {
  private readonly api = inject(PricingApi);
  private readonly log = viewChild<ElementRef<HTMLElement>>('log');
  readonly maxChars = MAX_CHARS;
  readonly open = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly lines = signal<ChatLine[]>([]);
  readonly examples = [
    'How much to send 2 kg from Colombo to Kandy?',
    'Where is my parcel SD-4821-0937?',
    'Show my recent deliveries',
  ];
  draft = '';
  private conversationId: string | null = null;

  async send(text: string) {
    const message = text.trim();
    if (!message || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.draft = '';
    this.add({ from: 'user', text: message, tools: [] });
    try {
      const answer = await this.api.chat(message, this.conversationId);
      this.conversationId = answer.conversationId;
      this.add({ from: 'assistant', text: plainText(answer.reply), tools: answer.toolCalls ?? [] });
    } catch (error) {
      this.error.set(errorMessage(error, ASSISTANT_HINT));
      this.draft = message;
    } finally {
      this.busy.set(false);
      this.scrollToEnd();
    }
  }

  /** Forgets the server-side memory too, so an old route or parcel cannot leak into the new chat. */
  async newChat() {
    const id = this.conversationId;
    this.conversationId = null;
    this.lines.set([]);
    this.error.set('');
    if (!id) return;
    try {
      await this.api.forgetConversation(id);
    } catch {
      // The server forgets idle conversations after 30 minutes anyway; nothing to show the user.
    }
  }

  /** Enter sends; Shift+Enter keeps a new line. */
  onEnter(event: Event) {
    if ((event as KeyboardEvent).shiftKey) return;
    event.preventDefault();
    void this.send(this.draft);
  }

  describe(call: ToolTrace): string {
    if (call.failed) return 'could not complete';
    const args = Object.entries(call.arguments ?? {})
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join(', ');
    return args || 'no inputs';
  }

  private add(line: ChatLine) {
    this.lines.update((all) => [...all, line]);
    this.scrollToEnd();
  }

  private scrollToEnd() {
    setTimeout(() => {
      const element = this.log()?.nativeElement;
      if (element) element.scrollTop = element.scrollHeight;
    });
  }
}
