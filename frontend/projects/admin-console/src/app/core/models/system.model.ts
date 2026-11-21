/** One service in GET /admin/system/health (answered by the API gateway). */
export interface ServiceHealth {
  service: string;
  /** The service's own /actuator/health status: UP, DOWN, OUT_OF_SERVICE or UNKNOWN. DOWN also means "did not answer". */
  status: string;
  responseMs: number;
}
