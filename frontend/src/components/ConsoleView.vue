<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ApiError } from '../api/http'
import { createTask, getTask, listTasks } from '../api/tasks'
import type { AuthSession, TaskResponse, TaskType } from '../types'

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

const taskType = ref<TaskType>('DELAY_DEMO')
const delaySeconds = ref(2)
const delayFail = ref(false)
const httpUrl = ref('https://httpbin.org/get')

let pollTimer: ReturnType<typeof setInterval> | null = null

const hasActive = computed(() =>
  tasks.value.some((t) => t.status === 'PENDING' || t.status === 'RUNNING'),
)

async function refreshList() {
  loadingList.value = true
  listError.value = ''
  try {
    tasks.value = await listTasks()
    if (selectedId.value != null) {
      const found = tasks.value.find((t) => t.id === selectedId.value)
      if (found) selected.value = found
    }
  } catch (e) {
    listError.value = e instanceof ApiError ? e.message : '加载任务列表失败'
    if (e instanceof ApiError && e.code === 40100) {
      emit('logout')
    }
  } finally {
    loadingList.value = false
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
    const payload =
      taskType.value === 'DELAY_DEMO'
        ? { seconds: Number(delaySeconds.value), fail: delayFail.value }
        : { url: httpUrl.value.trim() }

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

function syncPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (hasActive.value) {
    pollTimer = setInterval(async () => {
      await refreshList()
      if (selectedId.value != null) {
        try {
          selected.value = await getTask(selectedId.value)
        } catch {
          /* ignore transient poll errors */
        }
      }
    }, 2000)
  }
}

watch(hasActive, syncPoll)

onMounted(async () => {
  await refreshList()
  syncPoll()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="console">
    <header class="topbar">
      <div>
        <p class="eyebrow">Async Forge</p>
        <h1>任务控制台</h1>
      </div>
      <div class="topbar__user">
        <span>{{ props.session.username }}</span>
        <button type="button" class="btn btn--ghost" @click="emit('logout')">退出</button>
      </div>
    </header>

    <div class="console__grid">
      <section class="panel">
        <h2>创建任务</h2>
        <form class="create-form" @submit.prevent="submitTask">
          <label>
            <span>任务类型</span>
            <select v-model="taskType">
              <option value="DELAY_DEMO">DELAY_DEMO</option>
              <option value="HTTP_CALL">HTTP_CALL</option>
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

          <template v-else>
            <label>
              <span>URL（仅 GET）</span>
              <input v-model="httpUrl" type="url" required placeholder="https://..." />
            </label>
          </template>

          <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

          <button type="submit" class="btn btn--primary" :disabled="creating">
            {{ creating ? '提交中…' : '提交到队列' }}
          </button>
        </form>
      </section>

      <section class="panel panel--list">
        <div class="panel__head">
          <h2>我的任务</h2>
          <button type="button" class="btn btn--ghost" :disabled="loadingList" @click="refreshList">
            刷新
          </button>
        </div>
        <p v-if="listError" class="form-error">{{ listError }}</p>
        <p v-else-if="!tasks.length" class="muted">暂无任务，先提交一个试试。</p>
        <ul v-else class="task-list">
          <li
            v-for="task in tasks"
            :key="task.id"
            :class="{ 'is-active': selectedId === task.id }"
          >
            <button type="button" class="task-row" @click="selectTask(task.id)">
              <span class="task-row__id">#{{ task.id }}</span>
              <span class="task-row__type">{{ task.taskType }}</span>
              <span :class="statusClass(task.status)">{{ task.status }}</span>
              <span class="task-row__meta">重试 {{ task.retryCount }}/{{ task.maxRetry }}</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="panel panel--detail">
        <h2>任务详情</h2>
        <template v-if="selected">
          <dl class="detail">
            <div>
              <dt>ID</dt>
              <dd>{{ selected.id }}</dd>
            </div>
            <div>
              <dt>类型</dt>
              <dd>{{ selected.taskType }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd><span :class="statusClass(selected.status)">{{ selected.status }}</span></dd>
            </div>
            <div>
              <dt>重试</dt>
              <dd>{{ selected.retryCount }} / {{ selected.maxRetry }}</dd>
            </div>
            <div>
              <dt>创建</dt>
              <dd>{{ formatTime(selected.createdAt) }}</dd>
            </div>
            <div>
              <dt>更新</dt>
              <dd>{{ formatTime(selected.updatedAt) }}</dd>
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
        </template>
        <p v-else class="muted">从左侧列表选择一个任务。</p>
      </section>
    </div>
  </div>
</template>
