import { InjectionToken } from '@angular/core';

/** Where map tiles come from. An empty `url` draws the route without a base map (used by specs). */
export interface MapTiles {
  /** Leaflet tile URL template, for example `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`. */
  url: string;
  /** HTML credit the tile provider requires on the map. */
  attribution: string;
  maxZoom: number;
}

/**
 * Street map under the live tracking map: OpenStreetMap's standard tiles. Free and keyless, with the attribution shown
 * on the map, for normal interactive viewing (see https://operations.osmfoundation.org/policies/tiles/). Do not set
 * `Referrer-Policy: no-referrer` on the portal: the tile servers expect the browser's normal Referer.
 * (CARTO's free tiles were used first; since 25 September 2026 they need an API key.)
 * To switch provider, provide this token with another value and allow its host in the nginx `img-src` policy.
 */
export const MAP_TILES = new InjectionToken<MapTiles>('MAP_TILES', {
  providedIn: 'root',
  factory: () => ({
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
  }),
});
