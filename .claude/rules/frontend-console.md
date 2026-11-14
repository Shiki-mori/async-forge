---
description: Vue 控制台边界：只走 /api/tasks，不直连 Agent
paths:
  - "frontend/**/*.ts"
  - "frontend/**/*.vue"
---

# 前端控制台

- 所有请求走 `/api/tasks`（`frontend/src/api/tasks.ts`）。禁止浏览器请求 `AGENT_BASE_URL`。
- 新任务类型沿用 `ConsoleView.vue` 现有 `v-if` 表单分支，`types.ts` 的 `TaskType` 与后端枚举保持一致。详情复用 Payload / Result / Error 面板。