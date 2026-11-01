/** Pagination envelope used by every list endpoint (CONTRACT section 5). `page` is zero-based. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** The paging fields of a PageResponse; any PageResponse satisfies it. */
export type PageInfo = Pick<
  PageResponse<unknown>,
  'page' | 'size' | 'totalElements' | 'totalPages'
>;
