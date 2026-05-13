# Sử dụng image gốc siêu nhẹ tối ưu cho Production
FROM python:3.10-slim

# Thiết lập thư mục làm việc
WORKDIR /app

# Ngăn Python lưu bytecode file (.pyc) và đảm bảo output logging hiển thị ngay lập tức
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Copy requirements vào image trước để tận dụng Docker Cache layer
COPY ai-scoring-service/requirements.txt /app/

# Cài đặt các thư viện, bỏ qua cache để giảm size image
RUN pip install --no-cache-dir -r requirements.txt

# Copy toàn bộ mã nguồn vào thư mục hiện tại (/app)
COPY ai-scoring-service/main.py /app/

# Mở cổng 8000 cho REST API
EXPOSE 8000

# Lệnh khởi chạy Uvicorn ASGI Server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
