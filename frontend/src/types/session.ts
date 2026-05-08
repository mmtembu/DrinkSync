export interface Session {
  sessionId: string;
  stationId: number;
  stationName: string;
  lastActivityAt: string;
  expired: boolean;
  createdAt: string;
}
