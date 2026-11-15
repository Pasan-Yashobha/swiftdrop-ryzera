export interface ApiFieldError {
  field: string;
  message: string;
}

/** Standard JSON envelope for error responses returned by SwiftDrop backend services. */
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
