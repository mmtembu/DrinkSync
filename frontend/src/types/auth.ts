export interface AuthResponse {
  token: string;
  expiresAt: string;
  stationId: number;
}

export interface VendorLoginRequest {
  stationId: number;
  accessCode: string;
}
