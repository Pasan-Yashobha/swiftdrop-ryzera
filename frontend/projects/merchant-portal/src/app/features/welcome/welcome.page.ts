import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-welcome-page',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './welcome.page.html',
  styleUrl: './welcome.page.css',
})
export class WelcomePage {
  protected readonly auth = inject(AuthService);

  readonly user = computed(() => this.auth.user());
  readonly role = computed(() => this.auth.user()?.role ?? 'CUSTOMER');

  // Customer tracking demo state
  readonly trackingResult = signal<{
    code: string;
    status: string;
    carrier: string;
    eta: string;
    steps: { label: string; done: boolean; current?: boolean }[];
  } | null>({
    code: 'SWD-9042-LK',
    status: 'Out for Delivery',
    carrier: 'SwiftDrop Express Fleet',
    eta: 'Today by 4:30 PM',
    steps: [
      { label: 'Order Confirmed', done: true },
      { label: 'Picked Up by Courier', done: true },
      { label: 'In Transit to Central Hub', done: true },
      { label: 'Out for Delivery', done: true, current: true },
      { label: 'Delivered', done: false },
    ],
  });

  // Courier duty state
  readonly courierDuty = signal<'ONLINE' | 'OFFLINE'>('ONLINE');

  trackPackage(inputCode: string): void {
    const code = (inputCode || 'SWD-9042-LK').trim().toUpperCase();
    this.trackingResult.set({
      code,
      status: 'In Transit to Destination Hub',
      carrier: 'SwiftDrop Express Fleet',
      eta: 'Tomorrow by 11:00 AM',
      steps: [
        { label: 'Order Confirmed', done: true },
        { label: 'Picked Up by Courier', done: true },
        { label: 'In Transit', done: true, current: true },
        { label: 'Out for Delivery', done: false },
        { label: 'Delivered', done: false },
      ],
    });
  }

  toggleCourierDuty(): void {
    this.courierDuty.update((s) => (s === 'ONLINE' ? 'OFFLINE' : 'ONLINE'));
  }

  signOut(): void {
    this.auth.logout();
  }
}