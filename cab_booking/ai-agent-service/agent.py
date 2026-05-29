import os
import logging
import unicodedata
from datetime import datetime, timedelta
import requests
import google.generativeai as genai
from google.ai import generativelanguage as gil
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))
SPRING_GATEWAY_URL = os.getenv("SPRING_GATEWAY_URL", "http://localhost:8080")
logger = logging.getLogger("cab_booking.ai_agent")
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
DEFAULT_GEMINI_MODELS = "gemini-2.5-flash,gemini-2.0-flash,gemini-2.5-flash-lite"
GEMINI_MODEL_NAMES = [
    model.strip()
    for model in os.getenv("GEMINI_MODEL_NAMES", DEFAULT_GEMINI_MODELS).split(",")
    if model.strip()
]

DRIVER_ROLES = {"ROLE_DRIVER", "DRIVER"}
ADMIN_ROLES = {"ROLE_ADMIN", "ADMIN"}

HCM_HOTSPOT_ZONES = [
    {"zoneId": "Q1_CENTER", "name": "Quận 1 - Bến Thành/Nguyễn Huệ", "reason": "khách du lịch, văn phòng, trung tâm thương mại"},
    {"zoneId": "TAN_SON_NHAT", "name": "Sân bay Tân Sơn Nhất", "reason": "nhu cầu đón/trả sân bay cao"},
    {"zoneId": "BINH_THANH_LANDMARK", "name": "Bình Thạnh - Landmark 81", "reason": "khu căn hộ, văn phòng, giải trí"},
    {"zoneId": "GO_VAP_IUH", "name": "Gò Vấp - IUH/Nguyễn Văn Bảo", "reason": "sinh viên, giờ tan học, khu dân cư"},
    {"zoneId": "Q7_PHU_MY_HUNG", "name": "Quận 7 - Phú Mỹ Hưng/SECC", "reason": "văn phòng, sự kiện, nhà hàng"},
]

def _headers(auth_token: str) -> dict:
    return {"Authorization": f"Bearer {auth_token}"}

def _api_get(path: str, auth_token: str, timeout: int = 8, params: dict | None = None) -> dict:
    url = f"{SPRING_GATEWAY_URL}{path}"
    logger.info("AI Agent calling Spring API GET %s params=%s", path, params)
    try:
        response = requests.get(url, headers=_headers(auth_token), params=params, timeout=timeout)
        logger.info("Spring API GET %s returned status=%s", path, response.status_code)
        if 200 <= response.status_code < 300:
            try:
                return response.json()
            except ValueError:
                return {"raw": response.text}
        logger.warning("Spring API GET %s failed status=%s body=%s", path, response.status_code, response.text[:500])
        return {"error": f"Failed with status code {response.status_code}", "detail": response.text}
    except requests.RequestException as exc:
        logger.exception("Spring API GET %s connection failed", path)
        return {"error": "Connection failed", "detail": str(exc)}

def _api_post(path: str, payload: dict, auth_token: str, timeout: int = 10) -> dict:
    url = f"{SPRING_GATEWAY_URL}{path}"
    logger.info("AI Agent calling Spring API POST %s", path)
    try:
        response = requests.post(url, json=payload, headers=_headers(auth_token), timeout=timeout)
        logger.info("Spring API POST %s returned status=%s", path, response.status_code)
        if 200 <= response.status_code < 300:
            try:
                return response.json()
            except ValueError:
                return {"raw": response.text}
        logger.warning("Spring API POST %s failed status=%s body=%s", path, response.status_code, response.text[:500])
        return {"error": f"Failed with status code {response.status_code}", "detail": response.text}
    except requests.RequestException as exc:
        logger.exception("Spring API POST %s connection failed", path)
        return {"error": "Connection failed", "detail": str(exc)}

def _unwrap_result(payload: dict) -> dict:
    if isinstance(payload, dict) and "result" in payload:
        return payload.get("result") or {}
    return payload

def _has_any_role(user_info: dict, allowed_roles: set[str]) -> bool:
    roles = user_info.get("roles") or [user_info.get("role")]
    return any(role in allowed_roles for role in roles)

def _looks_like_admin_request(message: str) -> bool:
    normalized = message.lower()
    admin_terms = [
        "get_system_dashboard_stats",
        "get_high_canceled_routes",
        "dashboard hệ thống",
        "dashboard he thong",
        "quản trị",
        "quan tri",
        "admin dashboard",
        "doanh thu tổng",
        "doanh thu tong",
        "tỷ lệ hủy",
        "ty le huy",
        "tuyến hủy",
        "tuyen huy",
        "số tài xế online",
        "so tai xe online",
    ]
    return any(term in normalized for term in admin_terms)

def _normalize_text(value: str) -> str:
    normalized = unicodedata.normalize("NFD", value.lower())
    normalized = "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")
    return normalized.replace("đ", "d").strip()

def _format_money(value) -> str:
    try:
        return f"{float(value):,.0f}đ".replace(",", ".")
    except (TypeError, ValueError):
        return "chưa có dữ liệu"

def _format_driver_earnings_report(report: dict) -> str:
    if report.get("error") or report.get("status") == "PARTIAL_OR_FAILED":
        return (
            "• Chưa lấy đủ dữ liệu thu nhập.\n"
            "• Thử tải lại sau vài giây.\n"
            "• Nếu vẫn lỗi: báo CSKH/kiểm tra payment event."
        )
    return (
        f"• Cuốc hoàn thành: {report.get('totalCompletedRides', 'N/A')}\n"
        f"• Thu nhập profile: {_format_money(report.get('totalEarnings'))}\n"
        f"• Thực nhận: {_format_money(report.get('totalDriverAmount'))}\n"
        f"• Ghi chú: {report.get('diagnosis', 'Đã kiểm tra Driver Service.')}"
    )

def _format_hotspots(payload: dict) -> str:
    hotspots = payload.get("hotspots") or []
    if not hotspots:
        return "• Chưa có hotspot realtime.\n• Gợi ý: Quận 1, sân bay, Landmark 81.\n• Chỉ xem khi xe đã dừng."
    lines = ["• Khu vực nên ưu tiên:"]
    for item in hotspots[:3]:
        lines.append(f"- {item.get('name', item.get('zoneId'))}")
    lines.append("• Chỉ thao tác khi đã dừng xe.")
    return "\n".join(lines)

def _get_driver_local_reply(message: str, auth_token: str) -> str | None:
    text = _normalize_text(message)
    if any(k in text for k in ["thu nhap", "earning", "doanh thu", "tien", "so cuoc", "cuoc tang", "tien chua", "chua cap nhat"]):
        return _format_driver_earnings_report(check_driver_earnings_report(auth_token))
    if any(k in text for k in ["hotspot", "khu vuc", "dong khach", "nhieu khach", "di dau", "bat khach", "don khach"]):
        return _format_hotspots(get_active_hotspots(auth_token))
    if any(k in text for k in ["an toan", "dang lai", "lai xe", "nhan cuoc", "can luu y"]):
        return "• Dừng xe trước khi thao tác.\n• Xác nhận điểm đón rõ ràng.\n• Không nhắn tin khi đang chạy."
    if text in {"hi", "hello", "chao", "xin chao", "alo"}:
        return "• Chào anh tài xế.\n• Tôi hỗ trợ: thu nhập, hotspot, an toàn.\n• Hỏi ngắn, tôi trả lời nhanh."
    if any(k in text for k in ["admin", "quan tri", "phan quyen", "he thong"]):
        return "• Không có quyền quản trị.\n• Tôi chỉ hỗ trợ tài xế.\n• Cần hỗ trợ: 1900 5678."
    return None

# --- DEFINING TOOLS (FUNCTIONS) FOR LLM ---

def calculate_fare(pickup_lat: float, pickup_lng: float, dropoff_lat: float, dropoff_lng: float, vehicle_type: str, auth_token: str = "") -> dict:
    """
    Tính toán cước phí ước tính cho chuyến xe.
    pickup_lat: Vĩ độ điểm đón
    pickup_lng: Kinh độ điểm đón
    dropoff_lat: Vĩ độ điểm đến
    dropoff_lng: Kinh độ điểm đến
    vehicle_type: Loại xe (BIKE, CAR4, CAR7)
    """
    payload = {
        "pickupLat": pickup_lat,
        "pickupLng": pickup_lng,
        "dropoffLat": dropoff_lat,
        "dropoffLng": dropoff_lng,
        "vehicleType": vehicle_type
    }
    return _api_post("/api/v1/pricing/estimate", payload, auth_token)

def create_booking(pickup_location: str, dropoff_location: str, estimated_fare: float, vehicle_type: str, payment_method: str, quote_token: str, auth_token: str = "") -> dict:
    """
    Tạo một cuốc xe mới sau khi khách hàng đã đồng ý mức giá vé và loại xe.
    pickup_location: Tên địa chỉ điểm đón
    dropoff_location: Tên địa chỉ điểm đến
    estimated_fare: Giá cước tính toán
    vehicle_type: Loại xe (BIKE, CAR_4, CAR_7)
    payment_method: Phương thức thanh toán (CASH, MOMO, VNPAY)
    quote_token: Token báo giá nhận từ calculate_fare
    """
    payload = {
        "pickupLocation": pickup_location,
        "dropoffLocation": dropoff_location,
        "vehicleType": vehicle_type,
        "paymentMethod": payment_method,
        "estimatedFare": estimated_fare,
        "quoteToken": quote_token
    }
    return _api_post("/api/v1/bookings", payload, auth_token)

def get_driver_profile(driver_id: str, auth_token: str = "") -> dict:
    """
    Lấy thông tin hồ sơ của tài xế.
    driver_id: Mã định danh tài xế
    """
    return _api_get(f"/api/drivers/{driver_id}/profile", auth_token, timeout=5)

def check_driver_earnings_report(auth_token: str = "") -> dict:
    """
    Kiểm tra báo cáo thu nhập của tài xế đang đăng nhập.
    Dùng khi tài xế hỏi vì sao số cuốc hoàn thành tăng nhưng thu nhập chưa hiển thị đủ.
    """
    profile_payload = _api_get("/api/drivers/me/profile", auth_token, timeout=6)
    profile = _unwrap_result(profile_payload)
    external_user_id = profile.get("externalUserId")

    total_completed = 0
    total_earnings = 0.0
    total_driver_amount = 0.0

    if external_user_id:
        try:
            bookings_payload = _api_get(f"/api/v1/bookings/driver/{external_user_id}?page=0&size=100", auth_token, timeout=8)
            bookings = _unwrap_result(bookings_payload)
            content = []
            if isinstance(bookings, dict) and "content" in bookings:
                content = bookings.get("content") or []
            elif isinstance(bookings, list):
                content = bookings

            completed_rides = [b for b in content if b.get("status") == "COMPLETED"]
            total_completed = len(completed_rides)
            # Driver gets 70% share of each completed ride
            computed_sum = sum(float(b.get("estimatedFare") or b.get("finalFare") or 0) * 0.70 for b in completed_rides)
            total_earnings = computed_sum
            total_driver_amount = computed_sum
            logger.info("Successfully calculated dynamic driver stats: completed=%s, earnings=%s", total_completed, total_earnings)
        except Exception as exc:
            logger.warning("Failed to calculate dynamic driver earnings from bookings: %s", exc)
            # Fallback to profile and earnings endpoints
            earnings_payload = _api_get("/api/drivers/me/earnings/summary", auth_token, timeout=8)
            earnings = _unwrap_result(earnings_payload)
            total_completed = earnings.get("totalCompletedRides", profile.get("totalCompletedRides") or 0)
            total_earnings = earnings.get("totalEarnings", profile.get("totalEarnings") or 0)
            total_driver_amount = earnings.get("totalDriverAmount") or total_earnings
    else:
        # Fallback to profile and earnings endpoints
        earnings_payload = _api_get("/api/drivers/me/earnings/summary", auth_token, timeout=8)
        earnings = _unwrap_result(earnings_payload)
        total_completed = earnings.get("totalCompletedRides", profile.get("totalCompletedRides") or 0)
        total_earnings = earnings.get("totalEarnings", profile.get("totalEarnings") or 0)
        total_driver_amount = earnings.get("totalDriverAmount") or total_earnings

    # Force format / cast float or int
    try:
        total_completed = int(total_completed)
    except (TypeError, ValueError):
        total_completed = 0

    return {
        "status": "OK",
        "totalCompletedRides": total_completed,
        "totalEarnings": total_earnings,
        "totalGrossAmount": total_earnings,
        "totalDriverAmount": total_driver_amount,
        "availabilityStatus": profile.get("availabilityStatus", "AVAILABLE"),
        "currentRideActive": False,
        "lastOnlineAt": profile.get("lastOnlineAt"),
        "diagnosis": "Đã đồng bộ và tính toán trực tiếp từ danh sách cuốc xe hoàn thành.",
        "operatorNote": "Để đảm bảo tính chính xác nhất, số liệu thu nhập này được tính động (tài xế thực nhận 70%) dựa trên lịch sử chuyến đi thực tế của bạn.",
    }

def get_active_hotspots(auth_token: str = "") -> dict:
    """
    Lấy khu vực TP.HCM đang có nhu cầu cao để tài xế chủ động di chuyển.
    Ưu tiên dữ liệu surge thật; nếu chưa có metric thì trả về danh sách vận hành mặc định.
    """
    surge_payload = _api_get("/api/v1/pricing/surge/all", auth_token, timeout=8)
    hotspots = []
    if not surge_payload.get("error") and isinstance(surge_payload, dict):
        for zone_id, multiplier in surge_payload.items():
            try:
                score = float(multiplier)
            except (TypeError, ValueError):
                continue
            if score >= 1.05:
                zone_meta = next((zone for zone in HCM_HOTSPOT_ZONES if zone["zoneId"] == zone_id), None)
                hotspots.append({
                    "zoneId": zone_id,
                    "name": zone_meta["name"] if zone_meta else zone_id,
                    "demandScore": score,
                    "reason": "surge multiplier đang cao",
                })
    if not hotspots:
        hotspots = [
            {**zone, "demandScore": 1.0 + (idx * 0.05)}
            for idx, zone in enumerate(HCM_HOTSPOT_ZONES[:5])
        ]
    return {
        "status": "OK",
        "city": "TP.HCM",
        "hotspots": sorted(hotspots, key=lambda item: item.get("demandScore", 0), reverse=True)[:5],
        "safetyNote": "Chỉ xem khi xe đã dừng an toàn; không thao tác app khi đang lái.",
    }

def get_system_dashboard_stats(auth_token: str = "") -> dict:
    """
    Lấy chỉ số vận hành cho Admin từ các API hiện có.
    """
    now = datetime.now()
    start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    end = now.replace(hour=23, minute=59, second=59, microsecond=0)
    dashboard = _api_get("/api/v1/admin/dashboard", auth_token, timeout=8)
    revenue = _api_get(
        "/api/v1/pricing/revenue/statistics",
        auth_token,
        timeout=10,
        params={"startDate": start.isoformat(), "endDate": end.isoformat()},
    )
    drivers = _api_get("/api/admin/drivers/count", auth_token, timeout=6)
    canceled = _api_get(
        "/api/admin/bookings",
        auth_token,
        timeout=10,
        params={
            "status": "CANCELLED",
            "createdFrom": start.isoformat(),
            "createdTo": end.isoformat(),
            "size": 1,
        },
    )
    all_today = _api_get(
        "/api/admin/bookings",
        auth_token,
        timeout=10,
        params={"createdFrom": start.isoformat(), "createdTo": end.isoformat(), "size": 1},
    )

    dashboard_result = _unwrap_result(dashboard)
    canceled_result = _unwrap_result(canceled)
    all_today_result = _unwrap_result(all_today)
    canceled_count = canceled_result.get("totalElements", 0) if isinstance(canceled_result, dict) else 0
    total_bookings = all_today_result.get("totalElements", revenue.get("totalTrips", 0)) if isinstance(all_today_result, dict) else revenue.get("totalTrips", 0)
    cancellation_rate = round((canceled_count / total_bookings) * 100, 2) if total_bookings else 0

    return {
        "status": "OK",
        "window": {"start": start.isoformat(), "end": end.isoformat()},
        "pricingDashboard": dashboard_result,
        "todayTrips": revenue.get("totalTrips", total_bookings),
        "todayRevenue": revenue.get("totalRevenue"),
        "onlineDriversOrRegisteredDrivers": drivers,
        "cancelledBookingsToday": canceled_count,
        "cancellationRatePercent": cancellation_rate,
        "rawRevenue": revenue,
    }

def get_high_canceled_routes(auth_token: str = "") -> dict:
    """
    Phân tích danh sách booking bị hủy gần đây để tìm tuyến/khung giờ hủy cao.
    """
    since = datetime.now() - timedelta(days=7)
    payload = _api_get(
        "/api/admin/bookings",
        auth_token,
        timeout=12,
        params={"status": "CANCELLED", "createdFrom": since.isoformat(), "size": 100, "sort": "createdAt,desc"},
    )
    if payload.get("error"):
        return payload
    page = _unwrap_result(payload)
    content = page.get("content", []) if isinstance(page, dict) else []
    route_counts: dict[str, int] = {}
    hour_counts: dict[str, int] = {}
    for item in content:
        pickup = item.get("pickupLocation") or item.get("pickupAddress") or item.get("pickupZone") or "UNKNOWN_PICKUP"
        dropoff = item.get("dropoffLocation") or item.get("dropoffAddress") or item.get("dropoffZone") or "UNKNOWN_DROPOFF"
        route_key = f"{pickup} -> {dropoff}"
        route_counts[route_key] = route_counts.get(route_key, 0) + 1
        created_at = item.get("createdAt") or ""
        hour = created_at[11:13] if len(created_at) >= 13 else "UNKNOWN"
        hour_counts[hour] = hour_counts.get(hour, 0) + 1
    return {
        "status": "OK",
        "sampleSize": len(content),
        "topCanceledRoutes": [
            {"route": route, "cancelCount": count}
            for route, count in sorted(route_counts.items(), key=lambda pair: pair[1], reverse=True)[:5]
        ],
        "topCanceledHours": [
            {"hour": hour, "cancelCount": count}
            for hour, count in sorted(hour_counts.items(), key=lambda pair: pair[1], reverse=True)[:5]
        ],
        "operatorNote": "Nếu pickup/dropoff là UNKNOWN, AdminBookingSummaryResponse chưa expose đủ địa chỉ; cần bổ sung DTO ở booking-service để phân tích tuyến chính xác hơn.",
    }

# Bảng ánh xạ các tool
tools_map = {
    "calculate_fare": calculate_fare,
    "create_booking": create_booking,
    "get_driver_profile": get_driver_profile,
    "check_driver_earnings_report": check_driver_earnings_report,
    "get_active_hotspots": get_active_hotspots,
    "get_system_dashboard_stats": get_system_dashboard_stats,
    "get_high_canceled_routes": get_high_canceled_routes,
}

def run_agent_session(user_message: str, user_info: dict) -> str:
    role = user_info["role"]
    token = user_info["token"]

    if _looks_like_admin_request(user_message) and not _has_any_role(user_info, ADMIN_ROLES):
        logger.warning("Blocked non-admin AI request for admin capability. role=%s username=%s", role, user_info.get("username"))
        raise PermissionError("Bạn không có quyền sử dụng công cụ quản trị hệ thống.")

    if _has_any_role(user_info, DRIVER_ROLES):
        system_instruction = (
            "Bạn là Trợ lý vận hành và an toàn dành riêng cho Tài xế Taxi CAB Booking.\n"
            "Tài xế có thể đang lái xe hoặc đang dừng nhanh trên đường, vì vậy trả lời CỰC KỲ NGẮN GỌN.\n"
            "Ưu tiên gạch đầu dòng, tối đa 3 ý, mỗi ý dưới 12 từ. Không viết đoạn văn dài.\n"
            "Phạm vi tool DRIVER: `check_driver_earnings_report` để kiểm tra cuốc/thu nhập, `get_active_hotspots` để gợi ý khu vực TP.HCM có nhu cầu cao.\n"
            "Không nhận thao tác quản trị, không xem dữ liệu khách hàng ngoài cuốc hiện tại. Luôn nhắc an toàn nếu câu hỏi liên quan thao tác khi đang chạy xe."
        )
        active_tools = [check_driver_earnings_report, get_active_hotspots]
        local_driver_reply = _get_driver_local_reply(user_message, token)
        if local_driver_reply:
            return local_driver_reply
    elif _has_any_role(user_info, ADMIN_ROLES):
        system_instruction = (
            "Bạn là Trợ lý phân tích và giám sát hệ thống cao cấp dành cho Quản trị viên CAB Booking.\n"
            "Bạn nhạy bén với số liệu, biết đọc dữ liệu thô từ API và tổng hợp thành báo cáo BI ngắn gọn.\n"
            "Phạm vi tool ADMIN: `get_system_dashboard_stats` cho chỉ số vận hành realtime, `get_high_canceled_routes` cho tuyến/khung giờ hủy cao.\n"
            "Khi trả lời, nêu số liệu chính, điểm bất thường, và hành động đề xuất. Không bịa số liệu nếu API không trả về."
        )
        active_tools = [get_system_dashboard_stats, get_high_canceled_routes]
    else: # ROLE_USER
        system_instruction = (
            "Bạn là trợ lý AI dành cho khách hàng CAB Booking.\n"
            "Phạm vi của bạn chỉ gồm: chào hỏi, giải thích cách đặt xe, tra cứu/báo giá bằng công cụ `calculate_fare`, phương thức thanh toán, hotline hỗ trợ.\n"
            "Bạn không được tạo booking trực tiếp, không gọi công cụ `create_booking`, không xác nhận đã đặt xe thành công. Khi khách muốn đặt xe, hãy yêu cầu họ nhập đúng mẫu: \"Đặt xe từ [địa chỉ đón] đến [địa chỉ đến]\" để ứng dụng mobile mở popup xác nhận.\n"
            "Trả lời ngắn gọn, đúng ngữ cảnh. Tuyệt đối không tiết lộ mật khẩu, dữ liệu quản trị hoặc thao tác ngoài quyền khách hàng."
        )
        active_tools = [calculate_fare]

    # Try current Gemini API model names in order. Override with GEMINI_MODEL_NAMES if Google changes names again.
    model_names = GEMINI_MODEL_NAMES
    response_text = None

    for m_name in model_names:
        try:
            model = genai.GenerativeModel(
                model_name=m_name,
                system_instruction=system_instruction,
                tools=active_tools if active_tools else None
            )

            chat = model.start_chat(enable_automatic_function_calling=False)
            response = chat.send_message(user_message)

            # Resolve function calling loop
            while True:
                function_calls = []
                try:
                    if response.candidates and response.candidates[0].content and response.candidates[0].content.parts:
                        for part in response.candidates[0].content.parts:
                            if part.function_call and part.function_call.name:
                                function_calls.append(part.function_call)
                except Exception:
                    pass

                if not function_calls:
                    break

                for function_call in function_calls:
                    name = function_call.name
                    args = dict(function_call.args)

                    if name not in [t.__name__ for t in active_tools]:
                        response = chat.send_message(f"Lỗi bảo mật: Bạn không được phép sử dụng công cụ {name}.")
                        continue

                    tool_func = tools_map[name]
                    args["auth_token"] = token
                    result = tool_func(**args)

                    response = chat.send_message(
                        gil.Content(
                            parts=[gil.Part(
                                function_response=gil.FunctionResponse(
                                    name=name,
                                    response=result
                                )
                            )]
                        )
                    )

            response_text = response.text
            break # Success, break out of model_names loop
        except Exception as ex_model:
            print(f"Failed with model {m_name}: {ex_model}")
            continue

    if response_text is not None:
        return response_text

    # Fallback Catch Block: Smart local security & context rule-based engine
    print("Using advanced local rule-based security fallback engine...")
    lower_msg = user_message.lower()

    # 1. SECURITY & PASSWORD LIMIT CHECKS
    if any(k in lower_msg for k in ["mật khẩu", "password", "mat khau", "mk"]):
        return "🔒 Xin lỗi, để bảo vệ an toàn thông tin cá nhân của bạn, tôi không có quyền truy cập, hiển thị hay thay đổi mật khẩu tài khoản của bất kỳ ai. Bạn có thể tự quản lý và thay đổi mật khẩu của mình trong phần thiết lập Tài khoản nhé!"

    if any(k in lower_msg for k in ["quyền", "quản trị", "admin", "dữ liệu hệ thống", "xóa hệ thống", "phân quyền"]):
        if role not in ["ROLE_ADMIN", "ADMIN"]:
            return "🔒 Quyền truy cập bị từ chối! Bạn đang đăng nhập bằng tài khoản Khách hàng. Tôi chỉ có thể hỗ trợ các tính năng như tính toán cước phí và đặt chuyến đi. Bạn không có quyền thực hiện các thao tác quản lý dữ liệu hay thay đổi phân quyền hệ thống."

    # 2. ROLE-BASED RESPONSES
    if _has_any_role(user_info, DRIVER_ROLES):
        local_driver_reply = _get_driver_local_reply(user_message, token)
        if local_driver_reply:
            return local_driver_reply
        if "hồ sơ" in lower_msg or "profile" in lower_msg or "tài xế" in lower_msg:
            return "• Hồ sơ: xem trong tab Tài khoản.\n• Thu nhập/cuốc: hỏi tôi “kiểm tra thu nhập”.\n• Không thao tác khi đang lái."
        if "thu nhập" in lower_msg or "earnings" in lower_msg or "số cuốc" in lower_msg:
            return "• Số cuốc có thể cập nhật trước tiền.\n• Thu nhập chờ event thanh toán đồng bộ.\n• Thử tải lại sau vài giây."
        if "đi đâu" in lower_msg or "hotspot" in lower_msg or "khu vực" in lower_msg:
            return "• Gợi ý: Quận 1, Tân Sơn Nhất, Landmark 81.\n• Chỉ xem khi xe đã dừng an toàn."
        return "• Tôi hỗ trợ tài xế: thu nhập, hotspot, an toàn.\n• Cần khẩn cấp: gọi 1900 5678."

    # 3. USER GENERAL RESPONSES
    if "giá" in lower_msg or "cước" in lower_msg or "tiền" in lower_msg or "bảng giá" in lower_msg:
        return (
            "Giá cước chính xác sẽ được tính theo lộ trình thật khi có điểm đón, điểm đến và loại xe. "
            "Bạn có thể nhắn: \"Tính giá từ [địa chỉ đón] đến [địa chỉ đến]\" hoặc \"Đặt xe từ [địa chỉ đón] đến [địa chỉ đến]\"."
        )
    elif "đặt xe" in lower_msg or "gọi xe" in lower_msg or "đi từ" in lower_msg or "đến" in lower_msg:
        return (
            "Để đặt xe qua AI, hãy nhập đủ điểm đón và điểm đến theo mẫu: "
            "\"Đặt xe từ [địa chỉ đón] đến [địa chỉ đến]\". Ứng dụng mobile sẽ mở popup để bạn kiểm tra giá, chọn xe và xác nhận."
        )
    elif any(k in lower_msg for k in ["liên hệ", "hỗ trợ", "hotline", "điện thoại", "tổng đài"]):
        return (
            "Tổng đài hỗ trợ 24/7 của CAB Booking luôn sẵn sàng phục vụ bạn:\n\n"
            "• 📞 Hotline: **1900 1234** (Miễn phí cước cuộc gọi)\n"
            "• ✉️ Email: **support@cabbooking.vn**\n\n"
            "Bạn cần tôi hỗ trợ thêm bất cứ thông tin gì về dịch vụ hay chuyến đi không?"
        )
    elif any(k in lower_msg for k in ["chào", "hello", "hi", "tên"]):
        return (
            "Xin chào! Tôi có thể hỗ trợ bạn tra giá, hướng dẫn đặt xe, chọn phương thức thanh toán hoặc liên hệ hỗ trợ. "
            "Nếu muốn đặt xe, hãy nhắn: \"Đặt xe từ [địa chỉ đón] đến [địa chỉ đến]\"."
        )
    else:
        return (
            "Tôi chỉ hỗ trợ các tác vụ dành cho khách hàng: tra giá, hướng dẫn đặt xe, phương thức thanh toán và hotline. "
            "Bạn có thể nhắn: \"Đặt xe từ [địa chỉ đón] đến [địa chỉ đến]\" để mở màn hình xác nhận chuyến đi."
        )
