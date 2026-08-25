export interface ApiErrorPayload {
  code: string;
  message: string;
  meta?: Record<string, unknown>;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiErrorPayload;
}

export interface PageResult<T = Record<string, unknown>> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith("/api/")) {
    throw new Error("API path must be relative /api/...");
  }
  const response = await fetch(path, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
    ...init,
  });
  const envelope = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !envelope.success) {
    throw envelope.error ?? { code: "API_ERROR", message: "API 요청 실패" };
  }
  return envelope.data as T;
}

export function query(
  path: string,
  params: Record<string, string | undefined>,
) {
  const queryString = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) queryString.set(key, value);
  });
  return queryString.size > 0 ? `${path}?${queryString.toString()}` : path;
}
