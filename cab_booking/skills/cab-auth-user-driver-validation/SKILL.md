---
name: cab-auth-user-driver-validation
description: Validate the CAB Booking auth, user, and driver flows against the repo's fixed grading rubric and payload contracts. Use when Codex needs to smoke test `auth-service`, `user-service`, and `driver-service`, confirm gateway + internal driver behavior, or regenerate the shared validation report for this repo.
---

# CAB Auth User Driver Validation

Use the fixed rubric in `final_PROJECT_grading-factor.pdf` as a constraint. Do not edit that PDF.

## Quick Start

- Read `API_PAYLOADS.md` for the current gateway payload baseline.
- Read `docs/AUTH_USER_DRIVER_VALIDATION.md` for the shared validation scope.
- Run `python scripts/validate_auth_user_driver.py` from the repo root.

## Workflow

1. Confirm Docker stack health with `docker ps`.
2. Confirm service discovery with `http://localhost:8761/eureka/apps`.
3. Run the validation script from the repo root.
4. Inspect the newest JSON artifact in `docs/validation-artifacts/`.
5. If a step fails, fix the affected service, then rerun the script.

## Guardrails

- Keep `final_PROJECT_grading-factor.pdf` unchanged.
- Test auth through the gateway at `http://localhost:8080/api/auth/**`.
- Test user through the gateway at `http://localhost:8080/api/users/**`.
- Test driver through the gateway at `http://localhost:8080/api/drivers/**`.
- Test the internal driver check at `http://localhost:8089/internal/drivers/{driverId}/availability`.
- Respect auth route rate limiting; the script already spaces out auth requests.

## Coverage

- `auth`: register, login, verify, refresh, logout
- `user`: profile, account lifecycle, device sessions
- `driver`: profile/KYC, availability, ride assignment, ride progress, completion, earnings

## References

- `docs/AUTH_USER_DRIVER_VALIDATION.md`
- `scripts/validate_auth_user_driver.py`
