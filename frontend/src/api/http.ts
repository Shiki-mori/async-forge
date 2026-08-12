import type { ApiResult } from '../types'
import { clearSession, getToken } from '../auth/session'

export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }

  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...options, headers })

  let body: ApiResult<T> | null = null
  try {
    body = (await response.json()) as ApiResult<T>
  } catch {
    body = null
  }

  if (response.status === 401) {
    clearSession()
    throw new ApiError(40100, body?.message ?? '未登录或登录已过期')
  }

  if (!body) {
    throw new ApiError(response.status, `请求失败 (${response.status})`)
  }

  if (body.code !== 0) {
    if (body.code === 40100) {
      clearSession()
    }
    throw new ApiError(body.code, body.message || '请求失败')
  }

  return body.data
}
