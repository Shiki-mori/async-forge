<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ApiError } from '../api/http'
import { createTask, getTask, listTasks } from '../api/tasks'
import type { AuthSession, TaskResponse, TaskType } from '../types'
import ThemeToggle from './ThemeToggle.vue'

const props = defineProps<{
  session: AuthSession
}>()

const emit = defineEmits<{
  logout: []
}>()

const tasks = ref<TaskResponse[]>([])
const selectedId = ref<number | null>(null)
const selected = ref<TaskResponse | null>(null)
const listError = ref('')
const formError = ref('')
const creating = ref(false)
const loadingList = ref(false)

const DEFAULT_AGENT_INSTRUCTION =
  'GET https://httpbin.org/json ，告诉我 HTTP 状态码以及返回 JSON 的顶层字段名。'

const taskType = ref<TaskType>('DELAY_DEMO')
const delaySeconds = ref(2)
const delayFail = ref(false)
const httpUrl = ref('https://httpbin.org/get')
const instruction = ref(DEFAULT_AGENT_INSTRUCTION)
const forceFail = ref(false)

const POLL_INTERVAL_MS = 1000
const POLL_MAX_INTERVAL_MS = 30000

let pollTimer: ReturnType<typeof setTimeout> | null = null
let polling = false
let disposed = false
let failureCount = 0
let refreshSeq = 0

const hasActive = computed(() =>
  tasks.value.some((t) => t.status === 'PENDING' || t.status === 'RUNNING' || t.status === 'FAILED'),
)

async function refreshList() {
  const seq = ++refreshSeq
  try {
    const list = await listTasks()
    // 手动刷新与轮询可能并发，只采用最后一次发起的请求，避免旧响应覆盖新状态
    if (disposed || seq !== refreshSeq) return
    failureCount = 0
    tasks.value = list
    listError.value = ''
    if (selectedId.value != null) {
      const found = tasks.value.find((t) => t.id === selectedId.value)
      if (found) selected.value = found
    }
  } catch (e) {
    if (disposed || seq !== refreshSeq) return
    failureCount++
    listError.value = e instanceof ApiError ? e.message : '加载任务列表失败'
    if (e instanceof ApiError && e.code === 40100) {
      emit('logout')
    }
  }
}

// 加载状态只由手动刷新控制，避免轮询时按钮反复禁用而闪烁
async function manualRefresh() {
  loadingList.value = true
  try {
    await refreshList()
  } finally {
    loadingList.value = false
    schedulePoll()
  }
}

async function selectTask(id: number) {
  selectedId.value = id
  formError.value = ''
  try {
    selected.value = await getTask(id)
  } catch (e) {
    listError.value = e instanceof ApiError ? e.message : '加载任务详情失败'
    if (e instanceof ApiError && e.code === 40100) {
      emit('logout')
    }
  }
}

async function submitTask() {
  formError.value = ''
  creating.value = true
  try {
    const payload = buildPayload()
    const task = await createTask({ taskType: taskType.value, payload })
    await refreshList()
    await selectTask(task.id)
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '创建任务失败'
    if (e instanceof ApiError && e.code === 40100) {
      emit('logout')
    }
  } finally {
    creating.value = false
  }
}

function buildPayload(): Record<string, unknown> {
  if (taskType.value === 'DELAY_DEMO') {
    return { seconds: Number(delaySeconds.value), fail: delayFail.value }
  }
  if (taskType.value === 'HTTP_CALL') {
    return { url: httpUrl.value.trim() }
  }
  return { instruction: instruction.value.trim(), forceFail: forceFail.value }
}

function statusClass(status: string) {
  return `status status--${status.toLowerCase()}`
}

function prettyJson(value: unknown) {
  if (value == null) return '—'
  return JSON.stringify(value, null, 2)
}

function formatTime(value: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function clearPollTimer() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function canPoll() {
  return !disposed && !polling && hasActive.value && !document.hidden
}

// 上一次请求结束后才安排下一次；连续失败时间隔翻倍，成功后恢复
function schedulePoll() {
  clearPollTimer()
  if (!canPoll()) return
  const delay = Math.min(POLL_INTERVAL_MS * 2 ** failureCount, POLL_MAX_INTERVAL_MS)
  pollTimer = setTimeout(poll, delay)
}

async function poll() {
  clearPollTimer()
  if (!canPoll()) return
  polling = true
  try {
    await refreshList()
  } finally {
    polling = false
    schedulePoll()
  }
}

// 页面不可见时暂停，恢复可见时立即刷新一次
function onVisibilityChange() {
  if (document.hidden) {
    clearPollTimer()
  } else {
    void poll()
  }
}

watch(hasActive, schedulePoll)

onMounted(async () => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  await refreshList()
  schedulePoll()
})

onUnmounted(() => {
  disposed = true
  clearPollTimer()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="console">
    <header class="topbar">
      <div class="topbar__brand">
        <span class="logo" aria-hidden="true">AF</span>
        <span>async-forge</span>
        <span class="topbar__sep">/</span>
        <span class="topbar__page">任务控制台</span>
      </div>
      <div class="topbar__user">
        <span>{{ props.session.username }}</span>
        <ThemeToggle />
        <button type="button" class="btn btn--ghost btn--sm" @click="emit('logout')">退出</button>
      </div>
    </header>

    <div class="console__grid">
      <section class="panel">
        <div class="panel__head">
          <h2>新建任务</h2>
        </div>
        <form class="create-form panel__body" @submit.prevent="submitTask">
          <label>
            <span>任务类型</span>
            <select v-model="taskType">
              <option value="DELAY_DEMO">DELAY_DEMO</option>
              <option value="HTTP_CALL">HTTP_CALL</option>
              <option value="AGENT_TASK">AGENT_TASK</option>
            </select>
          </label>

          <template v-if="taskType === 'DELAY_DEMO'">
            <label>
              <span>延迟秒数</span>
              <input v-model.number="delaySeconds" type="number" min="0" max="60" required />
            </label>
            <label class="check">
              <input v-model="delayFail" type="checkbox" />
              <span>强制失败（演示重试 / 死信）</span>
            </label>
          </template>

          <template v-else-if="taskType === 'HTTP_CALL'">
            <label>
              <span>URL</span>
              <input v-model="httpUrl" type="url" required placeholder="https://..." />
            </label>
            <p class="field-hint">仅支持 GET 请求。</p>
          </template>

          <template v-else>
            <label>
              <span>指令</span>
              <textarea v-model="instruction" rows="4" required maxlength="2000"></textarea>
            </label>
            <label class="check">
              <input v-model="forceFail" type="checkbox" />
              <span>强制失败（演示重试 / 死信）</span>
            </label>
          </template>

          <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

          <button type="submit" class="btn btn--primary" :disabled="creating">
            {{ creating ? '提交中…' : '提交' }}
          </button>
        </form>
      </section>

      <section class="panel panel--list">
        <div class="panel__head">
          <h2>任务列表<span v-if="tasks.length" class="panel__count">{{ tasks.length }}</span></h2>
          <button
            type="button"
            class="btn btn--ghost btn--sm"
            :disabled="loadingList"
            @click="manualRefresh"
          >
            刷新
          </button>
        </div>
        <div class="panel__body panel__body--flush">
          <p v-if="listError" class="form-error">{{ listError }}</p>
          <p v-else-if="!tasks.length" class="panel__empty">还没有任务。</p>
          <div v-else class="task-table__scroll">
            <table class="task-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th class="num">重试</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="task in tasks"
                  :key="task.id"
                  :class="{ 'is-active': selectedId === task.id }"
                  tabindex="0"
                  @click="selectTask(task.id)"
                  @keydown.enter="selectTask(task.id)"
                >
                  <td class="id">#{{ task.id }}</td>
                  <td class="mono">{{ task.taskType }}</td>
                  <td><span :class="statusClass(task.status)">{{ task.status }}</span></td>
                  <td class="num">{{ task.retryCount }}/{{ task.maxRetry }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="panel panel--detail">
        <div class="panel__head">
          <h2>{{ selected ? `任务 #${selected.id}` : '任务详情' }}</h2>
          <span v-if="selected" :class="statusClass(selected.status)">{{ selected.status }}</span>
        </div>
        <div v-if="selected" class="panel__body">
          <dl class="detail">
            <div>
              <dt>类型</dt>
              <dd class="mono">{{ selected.taskType }}</dd>
            </div>
            <div>
              <dt>重试次数</dt>
              <dd>{{ selected.retryCount }} / {{ selected.maxRetry }}</dd>
            </div>
            <div>
              <dt>ID</dt>
              <dd class="mono">{{ selected.id }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd class="mono">{{ formatTime(selected.createdAt) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd class="mono">{{ formatTime(selected.updatedAt) }}</dd>
            </div>
          </dl>

          <div class="block">
            <h3>Payload</h3>
            <pre>{{ prettyJson(selected.payload) }}</pre>
          </div>
          <div class="block">
            <h3>Result</h3>
            <pre>{{ prettyJson(selected.result) }}</pre>
          </div>
          <div v-if="selected.errorMessage" class="block block--error">
            <h3>Error</h3>
            <pre>{{ selected.errorMessage }}</pre>
          </div>
        </div>
        <p v-else class="panel__empty">在任务列表中选择一项查看详情。</p>
      </section>
    </div>
  </div>
</template>
