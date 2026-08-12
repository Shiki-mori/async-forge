<script setup lang="ts">
import { ref } from 'vue'
import { login, register } from '../api/auth'
import { ApiError } from '../api/http'
import { saveSession } from '../auth/session'
import type { AuthSession } from '../types'

const emit = defineEmits<{
  authenticated: [session: AuthSession]
}>()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const data =
      mode.value === 'login'
        ? await login(username.value.trim(), password.value)
        : await register(username.value.trim(), password.value)
    const session: AuthSession = {
      token: data.token,
      userId: data.userId,
      username: data.username,
    }
    saveSession(session)
    emit('authenticated', session)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '请求失败，请检查后端是否已启动'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth">
    <header class="auth__brand">
      <p class="eyebrow">Async Forge</p>
      <h1>{{ mode === 'login' ? '登录控制台' : '注册账号' }}</h1>
      <p class="lede">提交任务、观察状态流转与重试死信。</p>
    </header>

    <form class="auth__form" @submit.prevent="submit">
      <label>
        <span>用户名</span>
        <input
          v-model="username"
          type="text"
          autocomplete="username"
          minlength="3"
          maxlength="64"
          required
          placeholder="3–64 字符"
        />
      </label>
      <label>
        <span>密码</span>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          minlength="6"
          maxlength="64"
          required
          placeholder="至少 6 位"
        />
      </label>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <button type="submit" class="btn btn--primary" :disabled="loading">
        {{ loading ? '提交中…' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>
    </form>

    <p class="auth__switch">
      <template v-if="mode === 'login'">
        没有账号？
        <button type="button" class="link" @click="mode = 'register'">去注册</button>
      </template>
      <template v-else>
        已有账号？
        <button type="button" class="link" @click="mode = 'login'">去登录</button>
      </template>
    </p>
  </section>
</template>
