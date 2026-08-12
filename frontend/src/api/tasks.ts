import type { CreateTaskRequest, TaskResponse } from '../types'
import { request } from './http'

export function createTask(body: CreateTaskRequest) {
  return request<TaskResponse>('/api/tasks', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function listTasks() {
  return request<TaskResponse[]>('/api/tasks')
}

export function getTask(id: number) {
  return request<TaskResponse>(`/api/tasks/${id}`)
}
