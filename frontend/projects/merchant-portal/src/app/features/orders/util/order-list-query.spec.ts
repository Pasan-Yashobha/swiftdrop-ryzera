import { convertToParamMap } from '@angular/router';
import {
  hasActiveFilters,
  NO_FILTERS,
  OrderFilters,
  paramsFromState,
  sameFilters,
  stateFromParams,
  toOrderQuery,
} from './order-list-query';

describe('order list query state', () => {
  it('starts from live orders on the first page when the URL has no parameters', () => {
    const state = stateFromParams(convertToParamMap({}));

    expect(state).toEqual({ filters: NO_FILTERS, page: 0 });
  });

  it('reads every filter and converts the one-based page', () => {
    const state = stateFromParams(
      convertToParamMap({
        status: 'DELIVERED',
        search: '  SD24 ',
        sandbox: 'all',
        from: '2026-10-01',
        to: '2026-10-31',
        page: '3',
      }),
    );

    expect(state).toEqual({
      filters: {
        status: 'DELIVERED',
        search: 'SD24',
        sandbox: 'all',
        from: '2026-10-01',
        to: '2026-10-31',
      },
      page: 2,
    });
  });

  it('ignores values that are not valid instead of passing them to the API', () => {
    const state = stateFromParams(
      convertToParamMap({
        status: 'PENDING',
        sandbox: 'everything',
        from: 'yesterday',
        to: '2026-13',
        page: '-4',
      }),
    );

    expect(state).toEqual({ filters: NO_FILTERS, page: 0 });
    expect(stateFromParams(convertToParamMap({ page: '2.5' })).page).toBe(0);
    expect(stateFromParams(convertToParamMap({ page: 'abc' })).page).toBe(0);
  });

  it('writes only non-default values to the URL', () => {
    expect(paramsFromState({ filters: NO_FILTERS, page: 0 })).toEqual({
      status: null,
      search: null,
      sandbox: null,
      from: null,
      to: null,
      page: null,
    });

    expect(
      paramsFromState({
        filters: {
          status: 'FAILED',
          search: ' a ',
          sandbox: 'sandbox',
          from: '2026-10-01',
          to: '',
        },
        page: 1,
      }),
    ).toEqual({
      status: 'FAILED',
      search: 'a',
      sandbox: 'sandbox',
      from: '2026-10-01',
      to: null,
      page: 2,
    });
  });

  it('round-trips through the URL', () => {
    const state = {
      filters: {
        status: 'IN_TRANSIT',
        search: 'Perera',
        sandbox: 'all',
        from: '',
        to: '2026-10-31',
      },
      page: 4,
    } as const;
    const params = paramsFromState(state);
    const withoutNulls = Object.fromEntries(Object.entries(params).filter(([, v]) => v !== null));

    expect(stateFromParams(convertToParamMap(withoutNulls))).toEqual(state);
  });

  it('builds the API query with the page size', () => {
    const query = toOrderQuery({
      filters: {
        status: 'DELIVERED',
        search: 'abc',
        sandbox: 'sandbox',
        from: '',
        to: '2026-10-31',
      },
      page: 2,
    });

    expect(query).toEqual({
      status: 'DELIVERED',
      search: 'abc',
      sandbox: 'sandbox',
      from: undefined,
      to: '2026-10-31',
      page: 2,
      size: 20,
    });
  });

  it('tells filtered lists from the default one', () => {
    const filtered: OrderFilters = { ...NO_FILTERS, search: 'x' };

    expect(hasActiveFilters(NO_FILTERS)).toBeFalse();
    expect(hasActiveFilters(filtered)).toBeTrue();
    expect(hasActiveFilters({ ...NO_FILTERS, sandbox: 'all' })).toBeTrue();
    expect(sameFilters(filtered, { ...NO_FILTERS, search: 'x' })).toBeTrue();
    expect(sameFilters(filtered, NO_FILTERS)).toBeFalse();
  });
});
