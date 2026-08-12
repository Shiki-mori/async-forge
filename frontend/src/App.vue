<script setup lang="ts">
import { ref } from 'vue'
import { clearSession, getSession } from './auth/session'
import type { AuthSession } from './types'
import AuthView from './components/AuthView.vue'
import ConsoleView from './components/ConsoleView.vue'

const session = ref<AuthSession | null>(getSession())

function onAuthenticated(next: AuthSession) {
  session.value = next
}

function onLogout() {
  clearSession()
  session.value = null
}
</script>

<template>
  <div class="app-shell">
    <AuthView v-if="!session" @authenticated="onAuthenticated" />
    <ConsoleView v-else :session="session" @logout="onLogout" />
  </div>
</template>
