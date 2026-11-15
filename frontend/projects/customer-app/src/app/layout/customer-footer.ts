import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-customer-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-footer.html',
  styleUrl: './customer-footer.css',
})
export class CustomerFooter {
  readonly year = input<number>(new Date().getFullYear());
}
