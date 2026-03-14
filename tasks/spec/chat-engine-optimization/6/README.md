# Task 6: Implement unified response structure

## What was implemented

Added `_build_success_response()` and `_build_error_response()` to `index.py`. The Lambda handler now uses these builders for all agent responses — completion text is parsed as a JSON object (not a string), `data_collected` is always included in successful responses, and error responses null out completion/data_collected/invocations.

## Tests

- 20 unit tests: `tests/test_response_structure.py`
- 7 property tests (Properties 10, 11, 12): `tests/test_response_structure_property.py`
- All 80 Lambda tests passing
