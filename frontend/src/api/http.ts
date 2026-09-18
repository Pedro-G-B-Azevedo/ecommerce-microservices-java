/** Corpo de erro no formato RFC 7807 que todo serviço devolve. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: { field: string; message: string }[]
  productIds?: string[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? `A requisição falhou com o status ${status}`)
    this.status = status
    this.problem = problem
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  token?: string | null
}

export async function apiFetch<T>(baseUrl: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.token) headers.Authorization = `Bearer ${options.token}`

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  const text = await response.text()
  const data = text.length > 0 ? JSON.parse(text) : undefined

  if (!response.ok) {
    throw new ApiError(response.status, data as ProblemDetail | undefined)
  }

  return data as T
}

/** Envelope de paginação comum aos quatro serviços. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}
