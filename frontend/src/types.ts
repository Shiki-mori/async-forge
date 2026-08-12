export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
}

export type TaskType = 'HTTP_CALL' | 'DELAY_DEMO'
export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'DEAD'

export interface TaskResponse {
  id: number
  taskType: TaskType | string
  payload: Record<string, unknown> | null
  status: TaskStatus | string
  retryCount: number
  maxRetry: number
  errorMessage: string | null
  result: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export interface CreateTaskRequest {
  taskType: TaskType
  payload: Record<string, unknown>
}

export interface AuthSession {
  token: string
  userId: number
  username: string
}
