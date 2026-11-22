export interface ApiFieldError {
  field: string;
  message: string;
}

/** Error body of the SwiftDrop services, e.g. `{status:409, code:"INVALID_STATE", message, correlationId}`. */
export interface ApiError {
  status: number;
  code: string;
  message: string;
  correlationId: string;
  details?: ApiFieldError[];
}
