import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-book-delivery-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1>Book a Delivery</h1>
          <p class="page__lead">Enter delivery details to schedule courier pickup with upfront pricing.</p>
        </div>
        <a routerLink="/dashboard" class="btn btn-secondary btn-sm">&larr; Back to Dashboard</a>
      </header>

      <div class="grid-2">
        <!-- Booking Form Layout -->
        <section aria-labelledby="form-heading" class="card stack">
          <h2 id="form-heading">Delivery Details</h2>

          <form class="stack" (submit)="$event.preventDefault()">
            <!-- Recipient Section -->
            <fieldset class="stack" style="border: none; padding: 0; margin: 0;">
              <legend><h3>1. Recipient Information</h3></legend>
              <div class="form-group">
                <label class="form-label" for="recipient-name">Recipient Full Name</label>
                <input id="recipient-name" type="text" class="form-input" placeholder="e.g. Kasun Jayawardena" />
              </div>
              <div class="grid-2">
                <div class="form-group">
                  <label class="form-label" for="recipient-phone">Phone Number (Required for SMS & Delivery)</label>
                  <input id="recipient-phone" type="tel" class="form-input" placeholder="+94 77 123 4567" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="recipient-email">Email (Optional, for digital receipt)</label>
                  <input id="recipient-email" type="email" class="form-input" placeholder="kasun@example.com" />
                </div>
              </div>
            </fieldset>

            <hr style="border: 0; border-top: 1px solid var(--color-border); margin-block: var(--space-2);" />

            <!-- Pickup & Dropoff Addresses -->
            <fieldset class="stack" style="border: none; padding: 0; margin: 0;">
              <legend><h3>2. Pickup & Drop-off Addresses</h3></legend>
              <div class="form-group">
                <label class="form-label" for="pickup-address">Pickup Address Line</label>
                <input id="pickup-address" type="text" class="form-input" placeholder="Street Address, Building" />
              </div>
              <div class="grid-2">
                <div class="form-group">
                  <label class="form-label" for="pickup-city">Pickup City</label>
                  <input id="pickup-city" type="text" class="form-input" placeholder="e.g. Colombo 03" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="pickup-postal">Postal Code (Optional)</label>
                  <input id="pickup-postal" type="text" class="form-input" placeholder="00300" />
                </div>
              </div>

              <div class="form-group" style="margin-top: var(--space-3);">
                <label class="form-label" for="dropoff-address">Drop-off Address Line</label>
                <input id="dropoff-address" type="text" class="form-input" placeholder="Destination Address Line, Street" />
              </div>
              <div class="grid-2">
                <div class="form-group">
                  <label class="form-label" for="dropoff-city">Drop-off City</label>
                  <input id="dropoff-city" type="text" class="form-input" placeholder="e.g. Kandy" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="dropoff-postal">Postal Code (Optional)</label>
                  <input id="dropoff-postal" type="text" class="form-input" placeholder="20000" />
                </div>
              </div>
            </fieldset>

            <hr style="border: 0; border-top: 1px solid var(--color-border); margin-block: var(--space-2);" />

            <!-- Parcel Package Specifications -->
            <fieldset class="stack" style="border: none; padding: 0; margin: 0;">
              <legend><h3>3. Package Details</h3></legend>
              <div class="grid-2">
                <div class="form-group">
                  <label class="form-label" for="parcel-weight">Weight in kg (0.01 - 50.00)</label>
                  <input id="parcel-weight" type="number" step="0.1" min="0.1" max="50" class="form-input" placeholder="1.5" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="parcel-size">Package Size Category</label>
                  <select id="parcel-size" class="form-select">
                    <option value="SMALL">Small (Documents, Envelope, Phone)</option>
                    <option value="MEDIUM">Medium (Shoebox, Small Bag)</option>
                    <option value="LARGE">Large (Carton, Appliance)</option>
                  </select>
                </div>
              </div>

              <div class="form-group">
                <label class="form-label" for="delivery-type">Delivery Priority</label>
                <select id="delivery-type" class="form-select">
                  <option value="STANDARD">Standard Delivery</option>
                  <option value="EXPRESS">Express Priority</option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label" for="parcel-description">Package Description</label>
                <input id="parcel-description" type="text" class="form-input" placeholder="e.g. Important documents and passport" />
              </div>

              <div class="form-group">
                <label class="form-label" for="parcel-notes">Handling Instructions / Notes</label>
                <textarea id="parcel-notes" rows="2" class="form-textarea" placeholder="e.g. Call sender before pickup, handle with care"></textarea>
              </div>
            </fieldset>
          </form>
        </section>

        <!-- Upfront Pricing & Submission Sidebar -->
        <aside aria-labelledby="quote-heading" class="stack">
          <div class="card stack" style="position: sticky; top: calc(var(--header-height) + var(--space-4));">
            <h2 id="quote-heading">Pricing & Quote Acceptance</h2>
            <p class="muted small">
              Parcel Service mandates an accepted Pricing Quote (<span class="mono">quoteId</span>) to guarantee upfront rates before dispatching couriers.
            </p>

            <div class="card" style="border-left: 4px solid var(--tone-info-fg); background: var(--tone-info-bg); color: var(--tone-info-fg); padding: var(--space-3);">
              <strong>Pricing Integration Status:</strong>
              <p style="margin-top: var(--space-1); font-size: var(--text-sm);">
                The backend <span class="mono">POST /api/v1/parcels</span> requires a valid <span class="mono">quoteId</span> verified against the Pricing Service.
                Real pricing quote generation will be activated once the Pricing Service endpoint contract is finalized.
              </p>
            </div>

            <button type="button" class="btn btn-primary btn-lg" style="margin-top: var(--space-2); width: 100%;" disabled>
              Awaiting Pricing Quote Integration
            </button>

            <p class="muted small" style="text-align: center;">
              Booking form model is bound to <span class="mono">CreateCustomerParcelRequest</span>.
            </p>
          </div>
        </aside>
      </div>
    </div>
  `,
})
export class BookDeliveryPage {}
