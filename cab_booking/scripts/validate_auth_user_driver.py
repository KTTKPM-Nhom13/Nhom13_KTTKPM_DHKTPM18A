#!/usr/bin/env python3
import json
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


BASE_URL = "http://localhost:8080"
DRIVER_INTERNAL_URL = "http://localhost:8089"
AUTH_DELAY_SECONDS = 1.3
GENERAL_DELAY_SECONDS = 0.3
OUTPUT_DIR = Path("docs") / "validation-artifacts"


class ValidationError(RuntimeError):
    pass


@dataclass
class StepResult:
    name: str
    method: str
    url: str
    expected_status: int
    actual_status: int
    passed: bool
    message: str
    response_excerpt: dict[str, Any] | list[Any] | str | None = None


@dataclass
class ValidationRun:
    started_at: str
    base_url: str
    driver_internal_url: str
    steps: list[StepResult] = field(default_factory=list)

    def add(self, step: StepResult) -> None:
        self.steps.append(step)

    @property
    def passed(self) -> bool:
        return all(step.passed for step in self.steps)

    def summary(self) -> dict[str, Any]:
        return {
            "startedAt": self.started_at,
            "baseUrl": self.base_url,
            "driverInternalUrl": self.driver_internal_url,
            "passed": self.passed,
            "totalSteps": len(self.steps),
            "passedSteps": sum(1 for step in self.steps if step.passed),
            "failedSteps": sum(1 for step in self.steps if not step.passed),
            "steps": [step.__dict__ for step in self.steps],
        }


def sleep_for(url: str) -> None:
    time.sleep(AUTH_DELAY_SECONDS if "/api/auth/" in url else GENERAL_DELAY_SECONDS)


def extract_excerpt(payload: Any) -> dict[str, Any] | list[Any] | str | None:
    if isinstance(payload, dict):
        excerpt: dict[str, Any] = {}
        for key in ("code", "message", "timestamp", "error", "path", "requestId"):
            if key in payload:
                excerpt[key] = payload[key]
        result = payload.get("result")
        if isinstance(result, dict):
            brief_result = {}
            for key in (
                "user",
                "userId",
                "email",
                "externalUserId",
                "fullName",
                "accountStatus",
                "availabilityStatus",
                "verificationStatus",
                "currentRideId",
                "rideId",
                "rideStatus",
                "status",
                "id",
                "restoreEligible",
                "totalCompletedRides",
                "totalEarnings",
                "activeForBooking",
            ):
                if key in result:
                    brief_result[key] = result[key]
            excerpt["result"] = brief_result or result
        elif result is not None:
            excerpt["result"] = result
        return excerpt or payload
    if isinstance(payload, list):
        return payload[:2]
    return payload


def request_json(
    method: str,
    url: str,
    body: dict[str, Any] | None = None,
    token: str | None = None,
    expected_status: int = 200,
) -> tuple[int, Any]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            status = resp.getcode()
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as exc:
        raise ValidationError(f"Network error for {method} {url}: {exc}") from exc

    try:
        payload = json.loads(raw) if raw else None
    except json.JSONDecodeError:
        payload = raw

    sleep_for(url)

    if status != expected_status:
        raise ValidationError(
            f"Expected HTTP {expected_status} for {method} {url} but got {status}. Payload: {payload}"
        )
    return status, payload


def run_step(
    run: ValidationRun,
    name: str,
    method: str,
    url: str,
    body: dict[str, Any] | None = None,
    token: str | None = None,
    expected_status: int = 200,
) -> Any:
    try:
        status, payload = request_json(method, url, body=body, token=token, expected_status=expected_status)
        run.add(
            StepResult(
                name=name,
                method=method,
                url=url,
                expected_status=expected_status,
                actual_status=status,
                passed=True,
                message="OK",
                response_excerpt=extract_excerpt(payload),
            )
        )
        return payload
    except Exception as exc:
        run.add(
            StepResult(
                name=name,
                method=method,
                url=url,
                expected_status=expected_status,
                actual_status=getattr(exc, "code", 0),
                passed=False,
                message=str(exc),
            )
        )
        raise


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def result_of(payload: dict[str, Any]) -> Any:
    return payload.get("result")


def write_outputs(run: ValidationRun) -> Path:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_path = OUTPUT_DIR / f"auth-user-driver-validation-{stamp}.json"
    output_path.write_text(json.dumps(run.summary(), indent=2, ensure_ascii=False), encoding="utf-8")
    return output_path


def main() -> int:
    started_at = datetime.now(timezone.utc).isoformat()
    run = ValidationRun(
        started_at=started_at,
        base_url=BASE_URL,
        driver_internal_url=DRIVER_INTERNAL_URL,
    )

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")

    rider_register = {
        "fullName": "QA Rider",
        "email": f"qa.rider.{stamp}@cab.local",
        "password": "12345678",
        "role": "USER",
        "phoneNumber": "0901234567",
        "avatarUrl": "https://cdn.example.com/avatars/qa-rider.png",
        "deviceId": f"qa-rider-web-{stamp}",
        "platform": "WEB",
        "userAgent": "Codex Validation Rider",
        "appVersion": "1.0.0",
    }
    driver_register = {
        "fullName": "QA Driver",
        "email": f"qa.driver.{stamp}@cab.local",
        "password": "12345678",
        "role": "DRIVER",
        "phoneNumber": "0911222333",
        "avatarUrl": "https://cdn.example.com/avatars/qa-driver.png",
        "deviceId": f"qa-driver-ios-{stamp}",
        "platform": "IOS",
        "userAgent": "Codex Validation Driver",
        "appVersion": "1.0.0",
    }

    rider_auth = run_step(run, "auth.register_rider", "POST", f"{BASE_URL}/api/auth/register", rider_register)
    rider_auth_result = result_of(rider_auth)
    rider_access_token = rider_auth_result["accessToken"]
    rider_refresh_token = rider_auth_result["refreshToken"]
    rider_user = rider_auth_result["user"]
    rider_user_id = rider_user["userId"]
    expect(rider_user["role"] == "USER", "Registered rider should have USER role")

    rider_login = run_step(
        run,
        "auth.login_rider",
        "POST",
        f"{BASE_URL}/api/auth/login",
        {
            "email": rider_register["email"],
            "password": rider_register["password"],
            "deviceId": rider_register["deviceId"],
            "platform": rider_register["platform"],
            "userAgent": rider_register["userAgent"],
            "appVersion": rider_register["appVersion"],
        },
    )
    rider_login_result = result_of(rider_login)
    rider_access_token = rider_login_result["accessToken"]
    rider_refresh_token = rider_login_result["refreshToken"]

    verify_rider = run_step(
        run,
        "auth.verify_rider_token",
        "POST",
        f"{BASE_URL}/api/auth/verify",
        {"token": rider_access_token},
    )
    verify_rider_result = result_of(verify_rider)
    expect(verify_rider_result["status"] == "success", "Rider access token should verify successfully")
    expect(str(verify_rider_result["userId"]) == str(rider_user_id), "Verified rider token should map to the rider user id")

    rider_refresh = run_step(
        run,
        "auth.refresh_rider_token",
        "POST",
        f"{BASE_URL}/api/auth/refresh",
        {"refreshToken": rider_refresh_token},
    )
    rider_refresh_result = result_of(rider_refresh)
    rider_access_token = rider_refresh_result["accessToken"]
    rider_refresh_token = rider_refresh_result["refreshToken"]

    user_profile_before = run_step(
        run,
        "user.get_profile_initial",
        "GET",
        f"{BASE_URL}/api/users/me/profile",
        token=rider_access_token,
    )
    user_profile_before_result = result_of(user_profile_before)
    expect(user_profile_before_result["externalUserId"] == rider_user_id, "Initial user profile should match rider user id")

    updated_profile = run_step(
        run,
        "user.upsert_profile",
        "PUT",
        f"{BASE_URL}/api/users/me/profile",
        {
            "fullName": "QA Rider Updated",
            "phoneNumber": "0901234567",
            "avatarUrl": "https://cdn.example.com/avatars/qa-rider-updated.png",
            "gender": "MALE",
            "dateOfBirth": "1999-09-09",
            "defaultPickupNote": "Cho o cong toa nha A",
        },
        token=rider_access_token,
    )
    updated_profile_result = result_of(updated_profile)
    expect(updated_profile_result["fullName"] == "QA Rider Updated", "User profile fullName should update")
    expect(updated_profile_result["accountStatus"] == "ACTIVE", "User account should remain ACTIVE after profile update")

    user_account_initial = run_step(
        run,
        "user.get_account_initial",
        "GET",
        f"{BASE_URL}/api/users/me/account",
        token=rider_access_token,
    )
    expect(result_of(user_account_initial)["accountStatus"] == "ACTIVE", "Initial account status should be ACTIVE")

    registered_device = run_step(
        run,
        "user.register_device",
        "POST",
        f"{BASE_URL}/api/users/me/devices",
        {
            "deviceIdentifier": f"device-web-{stamp}",
            "deviceName": "Chrome on Windows",
            "deviceType": "WEB",
            "platform": "WEB",
            "osVersion": "Windows 11",
            "appVersion": "1.0.1",
            "pushToken": f"push-token-{stamp}",
            "userAgent": "Codex Validation Browser",
            "trustedSession": True,
        },
        token=rider_access_token,
    )
    device_id = result_of(registered_device)["id"]

    user_devices = run_step(
        run,
        "user.get_devices",
        "GET",
        f"{BASE_URL}/api/users/me/devices",
        token=rider_access_token,
    )
    expect(len(result_of(user_devices)) >= 1, "User should have at least one registered device")

    run_step(
        run,
        "user.update_device",
        "PATCH",
        f"{BASE_URL}/api/users/me/devices/{device_id}",
        {
            "deviceName": "QA Rider Device",
            "deviceType": "WEB",
            "platform": "WEB",
            "osVersion": "Windows 11",
            "appVersion": "1.0.2",
            "pushToken": f"push-token-updated-{stamp}",
            "ipAddress": "10.10.10.21",
            "userAgent": "Codex Validation Browser Updated",
            "trustedSession": True,
            "status": "ACTIVE",
        },
        token=rider_access_token,
    )

    run_step(
        run,
        "user.device_heartbeat",
        "POST",
        f"{BASE_URL}/api/users/me/devices/{device_id}/heartbeat",
        token=rider_access_token,
    )

    run_step(
        run,
        "user.device_revoke",
        "POST",
        f"{BASE_URL}/api/users/me/devices/{device_id}/revoke",
        token=rider_access_token,
    )

    deletion_response = run_step(
        run,
        "user.request_delete_account",
        "POST",
        f"{BASE_URL}/api/users/me/account/delete-request",
        {"reason": "Smoke test account lifecycle"},
        token=rider_access_token,
    )
    expect(result_of(deletion_response)["accountStatus"] == "PENDING_DELETION", "Account should move to PENDING_DELETION")

    run_step(
        run,
        "auth.login_blocked_while_pending_deletion",
        "POST",
        f"{BASE_URL}/api/auth/login",
        {
            "email": rider_register["email"],
            "password": rider_register["password"],
            "deviceId": rider_register["deviceId"],
            "platform": rider_register["platform"],
            "userAgent": rider_register["userAgent"],
            "appVersion": rider_register["appVersion"],
        },
        expected_status=403,
    )

    restore_response = run_step(
        run,
        "user.restore_account",
        "POST",
        f"{BASE_URL}/api/users/me/account/restore",
        {},
        token=rider_access_token,
    )
    expect(result_of(restore_response)["accountStatus"] == "ACTIVE", "Account should restore to ACTIVE")

    rider_login_after_restore = run_step(
        run,
        "auth.login_rider_after_restore",
        "POST",
        f"{BASE_URL}/api/auth/login",
        {
            "email": rider_register["email"],
            "password": rider_register["password"],
            "deviceId": rider_register["deviceId"],
            "platform": rider_register["platform"],
            "userAgent": rider_register["userAgent"],
            "appVersion": rider_register["appVersion"],
        },
    )
    rider_login_after_restore_result = result_of(rider_login_after_restore)
    rider_access_token = rider_login_after_restore_result["accessToken"]
    rider_refresh_token = rider_login_after_restore_result["refreshToken"]

    driver_auth = run_step(run, "auth.register_driver", "POST", f"{BASE_URL}/api/auth/register", driver_register)
    driver_auth_result = result_of(driver_auth)
    driver_access_token = driver_auth_result["accessToken"]
    driver_refresh_token = driver_auth_result["refreshToken"]
    driver_user = driver_auth_result["user"]
    driver_user_id = driver_user["userId"]
    expect(driver_user["role"] == "DRIVER", "Registered driver should have DRIVER role")

    driver_login = run_step(
        run,
        "auth.login_driver",
        "POST",
        f"{BASE_URL}/api/auth/login",
        {
            "email": driver_register["email"],
            "password": driver_register["password"],
            "deviceId": driver_register["deviceId"],
            "platform": driver_register["platform"],
            "userAgent": driver_register["userAgent"],
            "appVersion": driver_register["appVersion"],
        },
    )
    driver_login_result = result_of(driver_login)
    driver_access_token = driver_login_result["accessToken"]
    driver_refresh_token = driver_login_result["refreshToken"]

    verify_driver = run_step(
        run,
        "auth.verify_driver_token",
        "POST",
        f"{BASE_URL}/api/auth/verify",
        {"token": driver_access_token},
    )
    verify_driver_result = result_of(verify_driver)
    expect(verify_driver_result["status"] == "success", "Driver access token should verify successfully")
    expect(str(verify_driver_result["userId"]) == str(driver_user_id), "Verified driver token should map to the driver user id")

    driver_refresh = run_step(
        run,
        "auth.refresh_driver_token",
        "POST",
        f"{BASE_URL}/api/auth/refresh",
        {"refreshToken": driver_refresh_token},
    )
    driver_refresh_result = result_of(driver_refresh)
    driver_access_token = driver_refresh_result["accessToken"]
    driver_refresh_token = driver_refresh_result["refreshToken"]

    driver_profile_initial = run_step(
        run,
        "driver.get_profile_initial",
        "GET",
        f"{BASE_URL}/api/drivers/me/profile",
        token=driver_access_token,
    )
    expect(
        result_of(driver_profile_initial)["externalUserId"] == driver_user_id,
        "Initial driver profile should match driver user id",
    )

    driver_profile = run_step(
        run,
        "driver.upsert_profile",
        "PUT",
        f"{BASE_URL}/api/drivers/me/profile",
        {
            "fullName": "QA Driver Updated",
            "phoneNumber": "0911222333",
            "avatarUrl": "https://cdn.example.com/avatars/qa-driver-updated.png",
            "licenseNumber": f"79A-{stamp}",
            "vehicleType": "CAR4",
            "vehiclePlate": f"51H{stamp[-5:]}",
            "vehicleModel": "Toyota Vios",
            "vehicleColor": "Black",
            "serviceArea": "District 1, Ho Chi Minh City",
        },
        token=driver_access_token,
    )
    driver_profile_result = result_of(driver_profile)
    expect(driver_profile_result["verificationStatus"] == "APPROVED", "Driver should be auto-approved after KYC upsert")

    availability = run_step(
        run,
        "driver.go_online",
        "PATCH",
        f"{BASE_URL}/api/drivers/me/availability",
        {
            "availabilityStatus": "ONLINE",
            "currentLatitude": 10.7769,
            "currentLongitude": 106.7009,
        },
        token=driver_access_token,
    )
    expect(result_of(availability)["availabilityStatus"] == "ONLINE", "Driver should switch to ONLINE")

    internal_status = run_step(
        run,
        "driver.internal_availability_check",
        "GET",
        f"{DRIVER_INTERNAL_URL}/internal/drivers/{driver_user_id}/availability",
    )
    internal_status_result = result_of(internal_status)
    expect(internal_status_result["activeForBooking"] is True, "Driver should be active for booking while ONLINE and idle")

    assignment = run_step(
        run,
        "driver.accept_assignment",
        "POST",
        f"{BASE_URL}/api/drivers/me/rides/assignment",
        {
            "rideId": f"RIDE-{stamp}",
            "action": "ACCEPT",
            "pickupAddress": "12 Nguyen Hue, District 1",
            "destinationAddress": "42 Le Loi, District 1",
        },
        token=driver_access_token,
    )
    assignment_result = result_of(assignment)
    expect(assignment_result["rideStatus"] == "ACCEPTED", "Driver ride should move to ACCEPTED")

    run_step(
        run,
        "driver.get_current_ride",
        "GET",
        f"{BASE_URL}/api/drivers/me/current-ride",
        token=driver_access_token,
    )

    en_route = run_step(
        run,
        "driver.progress_en_route",
        "PATCH",
        f"{BASE_URL}/api/drivers/me/rides/current",
        {
            "rideStatus": "EN_ROUTE_PICKUP",
            "currentLatitude": 10.7771,
            "currentLongitude": 106.7011,
        },
        token=driver_access_token,
    )
    expect(result_of(en_route)["rideStatus"] == "EN_ROUTE_PICKUP", "Ride should move to EN_ROUTE_PICKUP")

    in_progress = run_step(
        run,
        "driver.progress_in_progress",
        "PATCH",
        f"{BASE_URL}/api/drivers/me/rides/current",
        {
            "rideStatus": "IN_PROGRESS",
            "currentLatitude": 10.7780,
            "currentLongitude": 106.7020,
        },
        token=driver_access_token,
    )
    expect(result_of(in_progress)["rideStatus"] == "IN_PROGRESS", "Ride should move to IN_PROGRESS")

    completed = run_step(
        run,
        "driver.complete_ride",
        "POST",
        f"{BASE_URL}/api/drivers/me/rides/current/complete",
        {
            "fareAmount": 132500,
            "distanceKm": 8.4,
        },
        token=driver_access_token,
    )
    completed_result = result_of(completed)
    expect(completed_result["rideId"] is None, "Current ride should clear after completion")

    earnings = run_step(
        run,
        "driver.get_earnings_summary",
        "GET",
        f"{BASE_URL}/api/drivers/me/earnings/summary",
        token=driver_access_token,
    )
    earnings_result = result_of(earnings)
    expect(earnings_result["totalCompletedRides"] >= 1, "Completed rides should increase after ride completion")
    expect(float(earnings_result["totalEarnings"]) >= 132500, "Total earnings should increase after completion")

    run_step(
        run,
        "auth.logout_rider",
        "POST",
        f"{BASE_URL}/api/auth/logout",
        {"refreshToken": rider_refresh_token},
    )
    run_step(
        run,
        "auth.logout_driver",
        "POST",
        f"{BASE_URL}/api/auth/logout",
        {"refreshToken": driver_refresh_token},
    )

    output_path = write_outputs(run)
    print(json.dumps({"passed": run.passed, "report": str(output_path), "steps": len(run.steps)}, ensure_ascii=False))
    return 0 if run.passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        raise
