"""MCP tools: http_get + calculator. Invoked only via MCP Client, not by the graph."""

from __future__ import annotations

import ast
import ipaddress
import json
import logging
import operator
import socket
from typing import Any
from urllib.parse import urlparse

import httpx
from mcp.server.fastmcp import FastMCP

logger = logging.getLogger(__name__)
logging.getLogger("httpx").setLevel(logging.WARNING)

mcp = FastMCP("async-forge-tools")

_HTTP_TIMEOUT_SECONDS = 10.0
_BODY_SNIPPET_LIMIT = 1024
_ALLOWED_EXPR_CHARS = frozenset("0123456789+-*/(). \t\n\r")
_BIN_OPS: dict[type[ast.operator], Any] = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
}
_UNARY_OPS: dict[type[ast.unaryop], Any] = {
    ast.UAdd: operator.pos,
    ast.USub: operator.neg,
}
_BLOCKED_HOSTS = frozenset(
    {
        "localhost",
        "metadata.google.internal",
        "169.254.169.254",
    }
)


def _assert_public_http_url(url: str) -> None:
    parsed = urlparse(url)
    scheme = (parsed.scheme or "").lower()
    if scheme not in ("http", "https"):
        raise ValueError("only http/https is allowed")

    host = parsed.hostname
    if host is None or not host.strip():
        raise ValueError("invalid host")

    lower_host = host.lower().rstrip(".")
    if lower_host in _BLOCKED_HOSTS or lower_host.endswith(".local"):
        raise ValueError("private or local addresses are blocked")

    try:
        addr_infos = socket.getaddrinfo(host, parsed.port, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise ValueError("invalid host") from exc

    if not addr_infos:
        raise ValueError("invalid host")

    for info in addr_infos:
        ip = ipaddress.ip_address(info[4][0])
        if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
            ip = ip.ipv4_mapped
        if (
            ip.is_unspecified
            or ip.is_loopback
            or ip.is_link_local
            or ip.is_private
            or ip.is_reserved
            or ip.is_multicast
        ):
            raise ValueError("private or local addresses are blocked")


def _eval_ast(node: ast.AST) -> float:
    if isinstance(node, ast.Expression):
        return _eval_ast(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return float(node.value)
    if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARY_OPS:
        return float(_UNARY_OPS[type(node.op)](_eval_ast(node.operand)))
    if isinstance(node, ast.BinOp) and type(node.op) in _BIN_OPS:
        left = _eval_ast(node.left)
        right = _eval_ast(node.right)
        if isinstance(node.op, ast.Div) and right == 0:
            raise ValueError("division by zero")
        return float(_BIN_OPS[type(node.op)](left, right))
    raise ValueError("unsupported expression")


def _eval_expression(expression: str) -> str:
    if not expression or not expression.strip():
        raise ValueError("expression must be non-empty")
    if any(ch not in _ALLOWED_EXPR_CHARS for ch in expression):
        raise ValueError("expression contains illegal characters")
    try:
        tree = ast.parse(expression, mode="eval")
    except SyntaxError as exc:
        raise ValueError("invalid expression") from exc
    value = _eval_ast(tree)
    if not isinstance(value, float) or value != value or value in (float("inf"), float("-inf")):
        raise ValueError("invalid expression result")
    if value.is_integer() and abs(value) < 1e15:
        return str(int(value))
    return repr(value)


@mcp.tool()
def http_get(url: str) -> str:
    """HTTP GET a public URL. Returns JSON {statusCode, bodySnippet} (body truncated to 1024 chars).
    Only http/https. Localhost, private, link-local, and cloud metadata addresses are blocked.
    """
    _assert_public_http_url(url)
    timeout = httpx.Timeout(_HTTP_TIMEOUT_SECONDS)
    try:
        with httpx.Client(
            timeout=timeout,
            follow_redirects=False,
            trust_env=False,
        ) as client:
            with client.stream("GET", url) as response:
                raw = bytearray()
                for chunk in response.iter_bytes():
                    raw.extend(chunk)
                    if len(raw) >= _BODY_SNIPPET_LIMIT * 2:
                        break
                body = bytes(raw).decode("utf-8", errors="replace")[:_BODY_SNIPPET_LIMIT]
                payload = {"statusCode": response.status_code, "bodySnippet": body}
    except httpx.HTTPError as exc:
        logger.info("http_get failed host_ok=1 err=%s", type(exc).__name__)
        raise ValueError(f"http_get failed: {type(exc).__name__}") from exc
    return json.dumps(payload, ensure_ascii=False)


@mcp.tool()
def calculator(expression: str) -> str:
    """Evaluate a numeric expression using only digits and + - * / ( ) . and whitespace.
    Returns the result as a number string. Does not execute Python.
    """
    return _eval_expression(expression)
