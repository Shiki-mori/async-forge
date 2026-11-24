# syntax=docker/dockerfile:1
FROM python:3.12-slim

WORKDIR /app

# 只装 pyproject.toml 声明的依赖，改源码不会让这一层失效
COPY pyproject.toml .
RUN --mount=type=cache,target=/root/.cache/pip \
    python -c "import tomllib; print(*tomllib.load(open('pyproject.toml', 'rb'))['project']['dependencies'], sep='\n')" > /tmp/requirements.txt \
    && pip install -r /tmp/requirements.txt \
    && rm /tmp/requirements.txt

# 源码直接从 /app 运行，无需安装成包
COPY app ./app

EXPOSE 8081

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8081"]
