import os
import requests
import google.generativeai as genai
from google.ai import generativelanguage as gil
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))
SPRING_GATEWAY_URL = os.getenv("SPRING_GATEWAY_URL", "http://localhost:8080")

# --- DEFINING TOOLS (FUNCTIONS) FOR LLM ---

def calculate_fare(pickup_lat: float, pickup_lng: float, dropoff_lat: float, dropoff_lng: float, vehicle_type: str, auth_token: str) -> dict:
    """
    Tính toán cước phí ước tính cho chuyến xe.
    pickup_lat: Vĩ độ điểm đón
    pickup_lng: Kinh độ điểm đón
    dropoff_lat: Vĩ độ điểm đến
    dropoff_lng: Kinh độ điểm đến
    vehicle_type: Loại xe (ECONOMY, COMFORT, PREMIUM, BIKE)
    """
    headers = {"Authorization": f"Bearer {auth_token}"}
    payload = {
        "pickupLat": pickup_lat,
        "pickupLng": pickup_lng,
        "dropoffLat": dropoff_lat,
        "dropoffLng": dropoff_lng,
        "vehicleType": vehicle_type
    }
    try:
        response = requests.post(
            f"{SPRING_GATEWAY_URL}/api/pricing/estimate",
            json=payload,
            headers=headers,
            timeout=10
        )
        if response.status_code == 200:
            return response.json()
        return {"error": f"Failed with status code {response.status_code}", "detail": response.text}
    except Exception as e:
        return {"error": "Connection failed", "detail": str(e)}

def create_booking(pickup_location: str, dropoff_location: str, estimated_fare: float, vehicle_type: str, payment_method: str, quote_token: str, auth_token: str) -> dict:
    """
    Tạo một cuốc xe mới sau khi khách hàng đã đồng ý mức giá vé và loại xe.
    pickup_location: Tên địa chỉ điểm đón
    dropoff_location: Tên địa chỉ điểm đến
    estimated_fare: Giá cước tính toán
    vehicle_type: Loại xe (BIKE, CAR_4, CAR_7)
    payment_method: Phương thức thanh toán (CASH, MOMO, VNPAY)
    quote_token: Token báo giá nhận từ calculate_fare
    """
    headers = {"Authorization": f"Bearer {auth_token}"}
    payload = {
        "pickupLocation": pickup_location,
        "dropoffLocation": dropoff_location,
        "vehicleType": vehicle_type,
        "paymentMethod": payment_method,
        "estimatedFare": estimated_fare,
        "quoteToken": quote_token
    }
    try:
        response = requests.post(
            f"{SPRING_GATEWAY_URL}/api/v1/bookings",
            json=payload,
            headers=headers,
            timeout=10
        )
        if response.status_code == 200:
            return response.json()
        return {"error": f"Failed with status code {response.status_code}", "detail": response.text}
    except Exception as e:
        return {"error": "Connection failed", "detail": str(e)}

def get_driver_profile(driver_id: str, auth_token: str) -> dict:
    """
    Lấy thông tin hồ sơ của tài xế.
    driver_id: Mã định danh tài xế
    """
    headers = {"Authorization": f"Bearer {auth_token}"}
    try:
        response = requests.get(
            f"{SPRING_GATEWAY_URL}/api/drivers/{driver_id}",
            headers=headers,
            timeout=5
        )
        if response.status_code == 200:
            return response.json()
        return {"error": f"Failed with status code {response.status_code}", "detail": response.text}
    except Exception as e:
        return {"error": "Connection failed", "detail": str(e)}

# Bảng ánh xạ các tool
tools_map = {
    "calculate_fare": calculate_fare,
    "create_booking": create_booking,
    "get_driver_profile": get_driver_profile
}

def run_agent_session(user_message: str, user_info: dict) -> str:
    role = user_info["role"]
    token = user_info["token"]

    if role in ["ROLE_DRIVER", "DRIVER"]:
        system_instruction = (
            "Bạn là Trợ lý An toàn Lái xe CAB Booking dành cho Tài xế.\n"
            "Hãy trả lời siêu ngắn gọn, đi thẳng vào vấn đề (dưới 2 câu) để tài xế tập trung lái xe an toàn.\n"
            "Bạn có quyền truy cập công cụ get_driver_profile."
        )
        active_tools = [get_driver_profile]
    elif role in ["ROLE_ADMIN", "ADMIN"]:
        system_instruction = (
            "Bạn là Trợ lý Quản trị Admin Portal của CAB Booking.\n"
            "Hãy hỗ trợ admin kiểm tra dữ liệu bằng ngôn ngữ tự nhiên lịch sự, bảo mật thông tin tối đa."
        )
        active_tools = []
    else: # ROLE_USER
        system_instruction = (
            "Bạn là Trợ lý Ảo đặt xe CAB Booking thân thiện phục vụ Khách hàng.\n"
            "Bạn có thể hỗ trợ khách hàng tìm đường, tính cước phí bằng công cụ `calculate_fare` và đặt xe bằng `create_booking` khi khách hàng đồng ý.\n"
            "Nói chuyện lịch sự, ngắn gọn và hữu ích."
        )
        active_tools = [calculate_fare, create_booking]

    model = genai.GenerativeModel(
        model_name="gemini-2.5-flash",
        system_instruction=system_instruction,
        tools=active_tools if active_tools else None
    )

    chat = model.start_chat(enable_automatic_function_calling=False)
    response = chat.send_message(user_message)

    # Giải quyết vòng lặp function calling nếu có
    while True:
        # Trích xuất các function call một cách an toàn từ protobuf parts để tương thích 100% mọi phiên bản SDK
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
            # Đính kèm token xác thực của user/driver để gọi API Java an toàn
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

    return response.text
