import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AssistantApi } from '../../core/api/assistant.api';
import { toAppError } from '../../core/http/app-error';
import { ToolTrace } from '../../core/models/assistant.model';
import { plainText } from './plain-text';

/** The Assistant service rejects longer messages. */
export const MAX_MESSAGE_CHARS = 1000;

export interface ChatLine {
  from: 'user' | 'assistant';
  text: string;
  tools: ToolTrace[];
}

/**
 * Floating AI assistant for customers and merchants. The server keeps the conversation memory and calls
 * the MCP tools with the user's own token; this widget only keeps the conversation id it was given.
 * Replies are rendered with interpolation (plain text), never as HTML.
 */
@Component({
  selector: 'app-chat-widget',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-widget.html',
  styleUrls: ['../route-board.css', './chat-widget.css'],
})
export class ChatWidget {
  private readonly api = inject(AssistantApi);
  private readonly log = viewChild<ElementRef<HTMLElement>>('log');

  protected readonly maxChars = MAX_MESSAGE_CHARS;
  protected readonly examples = [
    'How much to send 2 kg from Colombo to Kandy?',
    'Where is my parcel SD-4821-0937?',
    'Show my recent deliveries',
  ];
  protected readonly open = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly lines = signal<ChatLine[]>([]);
  protected readonly draft = signal('');
  private conversationId: string | null = null;

  protected toggle(): void {
    this.open.update((open) => !open);
  }

  protected send(text: string): void {
    const message = text.trim();
    if (!message || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.draft.set('');
    this.add({ from: 'user', text: message, tools: [] });
    this.api.chat(message, this.conversationId).subscribe({
      next: (answer) => {
        this.conversationId = answer.conversationId;
        this.add({
          from: 'assistant',
          text: plainText(answer.reply),
          tools: answer.toolCalls ?? [],
        });
        this.busy.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error).message);
        this.draft.set(message);
        this.busy.set(false);
      },
    });
  }

  /** Clears the screen and asks the server to forget the conversation, so an old route cannot leak in. */
  protected newChat(): void {
    const id = this.conversationId;
    this.conversationId = null;
    this.lines.set([]);
    this.error.set('');
    if (id) {
      // The server forgets idle conversations after 30 minutes anyway, so a failure needs no message.
      this.api.forget(id).subscribe({ error: () => undefined });
    }
  }

  /** Enter sends; Shift+Enter starts a new line. */
  protected onEnter(event: Event): void {
    if ((event as KeyboardEvent).shiftKey) {
      return;
    }
    event.preventDefault();
    this.send(this.draft());
  }

  protected describe(call: ToolTrace): string {
    if (call.failed) {
      return 'could not complete';
    }
    const args = Object.entries(call.arguments ?? {})
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join(', ');
    return args || 'no inputs';
  }

  private add(line: ChatLine): void {
    this.lines.update((all) => [...all, line]);
    setTimeout(() => {
      const element = this.log()?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    });
  }
}
