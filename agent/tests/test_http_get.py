import socket
from unittest.mock import Mock

import pytest

from app.mcp_server import tools


@pytest.mark.parametrize(
    ("url", "reason"),
    [
        ("file:///etc/passwd", "only http/https is allowed"),
        ("https:///missing-host", "invalid host"),
        ("http://localhost", "private or local addresses are blocked"),
        ("http://printer.local", "private or local addresses are blocked"),
        ("http://169.254.169.254", "private or local addresses are blocked"),
        ("http://metadata.google.internal", "private or local addresses are blocked"),
    ],
)
def test_url_validation_rejects_disallowed_inputs(url, reason, monkeypatch):
    dns_lookup = Mock(side_effect=AssertionError("unexpected DNS lookup"))
    monkeypatch.setattr(tools.socket, "getaddrinfo", dns_lookup)

    with pytest.raises(ValueError, match=reason):
        tools._assert_public_http_url(url)


def ipv4_result(ip):
    return (socket.AF_INET, socket.SOCK_STREAM, socket.IPPROTO_TCP, "", (ip, 443))


def test_url_validation_accepts_public_address(monkeypatch):
    dns_lookup = Mock(return_value=[ipv4_result("8.8.8.8")])
    monkeypatch.setattr(tools.socket, "getaddrinfo", dns_lookup)

    tools._assert_public_http_url("https://example.test:443")


def test_http_get_blocks_if_any_resolved_address_is_private(monkeypatch):
    dns_lookup = Mock(
        return_value=[ipv4_result("8.8.8.8"), ipv4_result("10.0.0.5")]
    )
    monkeypatch.setattr(tools.socket, "getaddrinfo", dns_lookup)

    http_client_factory = Mock()
    monkeypatch.setattr(tools.httpx, "Client", http_client_factory)

    with pytest.raises(ValueError, match="private or local addresses are blocked"):
        tools.http_get("https://example.test:443")

    http_client_factory.assert_not_called()