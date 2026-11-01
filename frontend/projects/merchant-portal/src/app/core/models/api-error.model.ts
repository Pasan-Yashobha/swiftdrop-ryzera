export interface ApiFieldError {
  field: string;
  message: string;
}

/** Error body returned by every merchant-service endpoint except /oauth/token (CONTRACT section 4). */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  correlationId: string;
  details?: ApiFieldError[];
}
