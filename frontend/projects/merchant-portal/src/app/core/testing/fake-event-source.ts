import { PublicTracking } from '../models/tracking.model';

type Listener = (event: Event) => void;

/** Stand-in for the browser EventSource so specs can open, feed and break the live stream. */
export class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readyState = 0;
  closed = false;
  private readonly listeners = new Map<string, Listener[]>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  static reset(): void {
    FakeEventSource.instances = [];
  }

  static latest(): FakeEventSource {
    const latest = FakeEventSource.instances[FakeEventSource.instances.length - 1];
    if (!latest) {
      throw new Error('No EventSource was opened');
    }
    return latest;
  }

  /** The factory to provide as EVENT_SOURCE_FACTORY. */
  static factory(url: string): EventSource {
    return new FakeEventSource(url) as unknown as EventSource;
  }

  addEventListener(type: string, listener: Listener): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  open(): void {
    this.readyState = 1;
    this.dispatch('open', new Event('open'));
  }

  send(parcel: PublicTracking): void {
    this.sendRaw(JSON.stringify(parcel));
  }

  sendRaw(data: string): void {
    this.dispatch('tracking', new MessageEvent('tracking', { data }));
  }

  /** `closed` = the browser gave up (HTTP error); otherwise it is retrying by itself. */
  fail(closed: boolean): void {
    this.readyState = closed ? 2 : 0;
    this.dispatch('error', new Event('error'));
  }

  private dispatch(type: string, event: Event): void {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event);
    }
  }
}
