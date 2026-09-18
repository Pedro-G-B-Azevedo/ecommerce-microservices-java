import { ApiError } from '../api/http'

/** Traduz um erro capturado numa mensagem apresentável, priorizando o corpo RFC 7807. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.problem?.errors?.length) {
      return error.problem.errors.map((field) => `${field.field}: ${field.message}`).join('; ')
    }
    return error.problem?.detail ?? error.message
  }
  if (error instanceof Error) return error.message
  return 'Ocorreu um erro inesperado'
}

export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null
  return <p className="error-message">{describeError(error)}</p>
}
