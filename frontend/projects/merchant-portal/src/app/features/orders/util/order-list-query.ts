import { ParamMap, Params } from '@angular/router';
import {
  ORDER_STATUSES,
  OrderQuery,
  OrderStatus,
  SandboxFilter,
} from '../../../core/models/order.model';

/** Page size of the orders list. */
export const ORDERS_PAGE_SIZE = 20;

/** PENDING rows are short-lived placeholders the merchant never needs to filter by. */
export const FILTERABLE_STATUSES: readonly OrderStatus[] = ORDER_STATUSES.filter(
  (status) => status !== 'PENDING',
);

export const SANDBOX_FILTERS: readonly { value: SandboxFilter; label: string }[] = [
  { value: 'live', label: 'Live orders' },
  { value: 'sandbox', label: 'Sandbox orders' },
  { value: 'all', label: 'Live and sandbox' },
];

/** What the filter bar shows. Empty strings mean "no filter". */
export interface OrderFilters {
  status: OrderStatus | '';
  search: string;
  sandbox: SandboxFilter;
  /** yyyy-MM-dd or empty. */
  from: string;
  /** yyyy-MM-dd or empty. */
  to: string;
}

export const NO_FILTERS: OrderFilters = {
  status: '',
  search: '',
  sandbox: 'live',
  from: '',
  to: '',
};

export interface OrdersListState {
  filters: OrderFilters;
  /** Zero-based. The URL carries it one-based. */
  page: number;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const FILTER_KEYS = Object.keys(NO_FILTERS) as (keyof OrderFilters)[];

/** Reads the list state from URL query parameters, ignoring values that are not valid. */
export function stateFromParams(params: ParamMap): OrdersListState {
  const status = params.get('status');
  const sandbox = params.get('sandbox');
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const page = Number(params.get('page'));

  return {
    filters: {
      status: FILTERABLE_STATUSES.find((candidate) => candidate === status) ?? '',
      search: params.get('search')?.trim() ?? '',
      sandbox: SANDBOX_FILTERS.find((candidate) => candidate.value === sandbox)?.value ?? 'live',
      from: ISO_DATE.test(from) ? from : '',
      to: ISO_DATE.test(to) ? to : '',
    },
    page: Number.isInteger(page) && page > 1 ? page - 1 : 0,
  };
}

/** Query parameters for the URL. Defaults are left out so the plain list keeps a clean address. */
export function paramsFromState({ filters, page }: OrdersListState): Params {
  return {
    status: filters.status || null,
    search: filters.search.trim() || null,
    sandbox: filters.sandbox === NO_FILTERS.sandbox ? null : filters.sandbox,
    from: filters.from || null,
    to: filters.to || null,
    page: page > 0 ? page + 1 : null,
  };
}

export function toOrderQuery({ filters, page }: OrdersListState): OrderQuery {
  return {
    status: filters.status || undefined,
    search: filters.search || undefined,
    sandbox: filters.sandbox,
    from: filters.from || undefined,
    to: filters.to || undefined,
    page,
    size: ORDERS_PAGE_SIZE,
  };
}

export function sameFilters(a: OrderFilters, b: OrderFilters): boolean {
  return FILTER_KEYS.every((key) => a[key] === b[key]);
}

export function hasActiveFilters(filters: OrderFilters): boolean {
  return !sameFilters(filters, NO_FILTERS);
}
