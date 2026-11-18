import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CustomerHeader } from './layout/customer-header';
import { CustomerFooter } from './layout/customer-footer';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CustomerHeader, CustomerFooter],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly year = new Date().getFullYear();
}
