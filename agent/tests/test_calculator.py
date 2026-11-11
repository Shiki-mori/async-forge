import pytest

from app.mcp_server.tools import calculator


@pytest.mark.parametrize(
    ("expression", "expected"),
    [
        ("2 + 3 * 4", "14"),
        ("(2 + 3) * 4", "20"),
        ("-3.5 + 1", "-2.5"),
    ],
)
def test_calculator_returns_expected_result(expression, expected):
    assert calculator(expression) == expected


@pytest.mark.parametrize(
    ("expression", "reason"),
    [
        ("abs(1)", "illegal characters"),
        ("2 ** 3", "unsupported expression"),
        ("5 // 2", "unsupported expression"),
        ("1 / 0", "division by zero"),
    ],
)
def test_calculator_rejects_disallowed_expressions(expression, reason):
    with pytest.raises(ValueError, match=reason):
        calculator(expression)