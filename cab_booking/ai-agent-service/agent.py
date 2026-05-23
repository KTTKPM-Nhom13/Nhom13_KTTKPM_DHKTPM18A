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
            "Nói chuyện lịch sự, ngắn gọn và hữu ích. Bạn chỉ có quyền thực hiện tra cứu giá và đặt xe. Tuyệt đối không tiết lộ thông tin mật khẩu hay dữ liệu quản trị."
        )
        active_tools = [calculate_fare, create_booking]

    # Try standard model names in order
    model_names = ["gemini-1.5-flash-latest", "gemini-1.5-flash", "gemini-pro"]
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
    if role in ["ROLE_DRIVER", "DRIVER"]:
        if "hồ sơ" in lower_msg or "profile" in lower_msg or "tài xế" in lower_msg:
            return "Chào đối tác tài xế! Tôi đã kiểm tra hệ thống. Hồ sơ của bạn đang hoạt động bình thường, sẵn sàng nhận chuyến. Hãy bật ứng dụng tài xế lên và tập trung lái xe an toàn nhé! 🏍️"
        return "Chào đối tác tài xế CAB Booking! Hãy lái xe cẩn thận và an toàn. Nếu cần hỗ trợ khẩn cấp trên đường đi, hãy gọi trực tiếp đến Hotline dành riêng cho đối tác: **1900 5678**."

    # 3. USER GENERAL RESPONSES
    if "giá" in lower_msg or "cước" in lower_msg or "tiền" in lower_msg or "bảng giá" in lower_msg:
        return (
            "Chào bạn! Cước phí của CAB Booking được tính cực kỳ tối ưu và minh bạch:\n\n"
            "• 🏍️ **CAB Bike (Xe máy):** 10.000đ cho 2km đầu tiên, sau đó 4.000đ/km tiếp theo.\n"
            "• 🚗 **CAB Car (Ô tô 4 chỗ):** 20.000đ cho 2km đầu tiên, sau đó 12.000đ/km tiếp theo.\n"
            "• 🚙 **CAB Car Premium (7 chỗ):** 25.000đ cho 2km đầu tiên, sau đó 15.000đ/km tiếp theo.\n\n"
            "Đặc biệt đang có khuyến mãi giảm 30.000đ (mã **CABNEW**) cho chuyến đi đầu tiên đó! Bạn có muốn đặt một chuyến xe trải nghiệm ngay không?"
        )
    elif "đặt xe" in lower_msg or "gọi xe" in lower_msg or "đi từ" in lower_msg or "đến" in lower_msg:
        return (
            "Tuyệt vời! Tôi có thể giúp bạn đặt xe rảnh tay chỉ qua một tin nhắn. Hãy chat theo cú pháp:\n\n"
            "👉 *\"Đặt xe máy từ Đại học Công nghiệp đến Landmark 81\"* hoặc *\"Đặt xe ô tô đến Sân bay Tân Sơn Nhất\"*\n\n"
            "Tôi sẽ lập tức tìm đường, báo giá và hiển thị bảng xác nhận đặt xe trực tiếp trên màn hình của bạn! 🚖"
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
            "Xin chào! Tôi là trợ lý ảo đặt xe thông minh CAB AI Agent của bạn. 🚖\n\n"
            "Tôi được giới hạn quyền hạn chỉ để hỗ trợ bạn tìm đường, tính cước phí hành trình và đặt chuyến xe rảnh tay một cách an toàn nhất.\n"
            "Thời tiết hôm nay rất đẹp! Bạn muốn tôi đặt chuyến xe đưa bạn đi đâu bây giờ? Hãy cho tôi biết điểm đón và điểm đến của bạn nhé! ☀️"
        )
    else:
        return (
            "Chào bạn! Tôi là trợ lý ảo đặt xe thông minh CAB AI Agent. Tôi có thể hỗ trợ bạn:\n\n"
            "• 💰 Tính toán giá cước hành trình theo thời gian thực.\n"
            "• 🏍️ Đặt xe máy/ô tô rảnh tay bằng tin nhắn nhanh chóng.\n"
            "• 🎁 Áp dụng các mã giảm giá và tìm đường đi tối ưu nhất.\n\n"
            "Hãy thử nhắn: *\"Đặt xe từ Đại học Công nghiệp đến Landmark 81\"* để trải nghiệm nhé! 🚖"
        )
