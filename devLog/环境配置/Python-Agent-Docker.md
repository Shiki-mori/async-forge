# Python Agent Docker

骨架设计见 [Python-Agent骨架搭建.md](Python-Agent骨架搭建.md)。本文只记 **镜像怎么起、端口怎么对**。

## 服务

| 项 | 值 |
|----|----|
| 镜像 | `async-forge-agent:local` |
| 容器 | `async-forge-agent` |
| Compose 服务名 | `agent`（容器网络内访问 `http://agent:8081`） |
| 进程 | `uvicorn app.main:app --host 0.0.0.0 --port 8081` |

容器内端口固定 **8081**。宿主机端口由 `deploy/.env` 的 `AGENT_PORT` 决定。

本机 nginx（`gacha.conf`）已占用 **8081**，因此 `.env` 里 `AGENT_PORT=8082`。宿主机核验用 `http://localhost:8082`。Compose 里 Java 仍写 `AGENT_BASE_URL=http://agent:8081`，不要改成 `localhost`。

## 启动 / 停止

在 `deploy/` 下（需已有 `docker-compose.yml` 与 `.env`，可从 example 复制）：

```bash
cd deploy
docker compose up -d agent
docker compose ps agent
docker compose logs -f agent
docker compose stop agent
```

首次或代码变更后重建镜像：

```bash
cd deploy
docker compose build agent
docker compose up -d agent
```

拉 `python:3.12-slim` 若超时，在**同一终端**先导出代理（daemon 配了代理不够）：

```bash
export HTTP_PROXY=http://127.0.0.1:8966
export HTTPS_PROXY=http://127.0.0.1:8966
export NO_PROXY=localhost,127.0.0.1
export BUILDKIT_NO_CLIENT_TOKEN=1
```

## 核验

```bash
curl -s http://localhost:8082/health
# {"status":"ok"}

curl -s http://localhost:8082/.well-known/agent-card.json
```

healthcheck 探的是容器内 `GET http://127.0.0.1:8081/health`，与宿主机映射端口无关。

步骤 A 骨架无需 `AI_API_KEY`。密钥不要写进镜像，只放 `deploy/.env` / `agent/.env`（已 gitignore）。

当前 **Java 尚未接入 `AGENT_TASK`**，控制台不能提交 Agent 任务。
