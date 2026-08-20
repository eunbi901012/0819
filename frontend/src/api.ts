export type ApiResponse<T> = {
  success: true;
  meta: Record<string, unknown>;
  data: T;
};

export type ApiError = {
  success: false;
  meta: Record<string, unknown>;
  error: {
    code: string;
    message: string;
    fieldErrors?: Record<string, string>;
  };
};

export class ApiRequestError extends Error {
  status: number;
  code?: string;
  fieldErrors: Record<string, string>;

  constructor(
    message: string,
    status: number,
    code?: string,
    fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith("/api/")) {
    throw new Error("API path must be relative /api/...");
  }

  const response = await fetch(path, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
    ...init,
  });

  const body = (await response.json()) as ApiResponse<T> | ApiError;
  if (!response.ok || body.success === false) {
    const errorBody = body as ApiError;
    throw new ApiRequestError(
      errorBody.error?.message ?? "요청 처리 중 오류가 발생했습니다.",
      response.status,
      errorBody.error?.code,
      errorBody.error?.fieldErrors ?? {},
    );
  }

  return (body as ApiResponse<T>).data;
}
