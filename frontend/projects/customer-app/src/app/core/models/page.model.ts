/**
 * Standard pagination response envelope returned by backend list endpoints.
 * `page` is zero-based.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
