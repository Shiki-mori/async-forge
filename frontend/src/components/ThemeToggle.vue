<script setup lang="ts">
import { theme, toggleTheme } from '../theme'
</script>

<template>
  <button
    type="button"
    class="btn btn--ghost btn--sm"
    :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
    :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
    @click="toggleTheme"
  >
    {{ theme === 'dark' ? '浅色' : '深色' }}
  </button>
</template>
