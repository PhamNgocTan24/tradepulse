// API request payload for user login
export interface LoginRequestBody {
  email: string;
  password: string;
  totpCode?: string; // Optional field for 2FA verification
}

// Authentication response returned by the backend auth-service
export interface AuthResponse {
  userId: string;
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number; // Expiry duration in seconds
  tokenType: string; // Typically "Bearer"
}

// Standard wrapper response shape returned by all backend endpoints
export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T | null;
}

// API request payload for user registration
export interface RegisterRequestBody {
  email: string;
  password: string;
  displayName: string;
}

// Registration response returned by the backend auth-service
export interface RegisterResponse {
  userId: string;
  email: string;
}

export interface BackendFieldError {
  field: string;
  message: string;
}

export interface BackendErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: BackendFieldError[];
}


