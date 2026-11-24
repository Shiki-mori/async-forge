import { ref } from 'vue'

export type Theme = 'light' | 'dark'

// 与 index.html 首屏内联脚本使用同一个 key
const STORAGE_KEY = 'async-forge.theme'

// 初始主题由 index.html 的内联脚本在首屏渲染前写入 data-theme
export const theme = ref<Theme>(
  document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light',
)

export function toggleTheme(): void {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.dataset.theme = theme.value
  try {
    localStorage.setItem(STORAGE_KEY, theme.value)
  } catch {
    /* 存储不可用时仅本次页面生效 */
  }
}
