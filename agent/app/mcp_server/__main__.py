"""stdio MCP server: python -m app.mcp_server"""

import logging
import sys

from app.mcp_server.tools import mcp

logging.basicConfig(stream=sys.stderr, level=logging.WARNING)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logging.getLogger("mcp").setLevel(logging.WARNING)


def main() -> None:
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
