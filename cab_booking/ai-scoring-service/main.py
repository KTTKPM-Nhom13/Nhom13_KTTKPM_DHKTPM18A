import logging
import sys
from typing import List, Optional

# pyrefly: ignore [missing-import]
import numpy as np
# pyrefly: ignore [missing-import]
from fastapi import FastAPI, HTTPException
# pyrefly: ignore [missing-import]
from pydantic import BaseModel, Field

# Thiết lập logging chuẩn in ra console để theo dõi trong Docker
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="AI Scoring Service",
    description="Dịch vụ AI đánh giá điểm tài xế dựa trên Distance, ETA, Rating và Price",
    version="2.0.0"
)

# --- Pydantic Models Khớp với DTO của Java ---

class DriverFeature(BaseModel):
    driverId: str
    distance: float
    eta: int
    rating: float
    priceMultiplier: float  # Hệ số giá cước: 1.0 = bình thường, >1.0 = đắt hơn

class DriverScore(BaseModel):
    driverId: str
    score: float
    details: str

class AiScoringResponse(BaseModel):
    bestDriverId: Optional[str] = None
    highestScore: float = 0.0
    ranking: List[DriverScore] = []

# --- Constants cho thuật toán chuẩn hóa ---
MAX_DISTANCE = 5.0   # km
MAX_ETA = 20.0       # phút
MAX_RATING = 5.0
MAX_PRICE_MULTIPLIER = 2.0   # Giả sử multiplier tối đa là x2.0
MIN_PRICE_MULTIPLIER = 1.0   # Giá bình thường

# Trọng số Multi-objective (Case 53):
# Distance (30%), ETA (30%), Rating (20%), Price (20% - Càng rẻ càng tốt)
WEIGHTS = np.array([0.3, 0.3, 0.2, 0.2])

@app.get("/ping")
async def ping():
    """API Health Check cho Docker/Kubernetes"""
    return {"status": "UP", "message": "Pong"}

@app.post("/api/v1/ai/score", response_model=AiScoringResponse)
async def calculate_scores(drivers: List[DriverFeature]):
    """
    API nhận danh sách đặc trưng của tài xế, tính toán tổng điểm bằng Numpy và trả về xếp hạng.
    """
    if not drivers:
        logger.warning("Nhận danh sách tài xế rỗng!")
        return AiScoringResponse()

    logger.info(f"Đang tính điểm cho {len(drivers)} tài xế...")

    try:
        # 1. Chuẩn bị dữ liệu thô để đưa vào Ma Trận
        driver_ids = []
        feature_matrix = []

        for d in drivers:
            driver_ids.append(d.driverId)

            # --- BƯỚC 1: CHUẨN HÓA (MIN-MAX NORMALIZATION) ---

            # Distance: Càng nhỏ càng tốt → Đảo ngược, clip về [0, 1]
            norm_dist = max(0, (MAX_DISTANCE - d.distance) / MAX_DISTANCE)

            # ETA: Càng nhỏ càng tốt → Đảo ngược, clip về [0, 1]
            norm_eta = max(0, (MAX_ETA - float(d.eta)) / MAX_ETA)

            # Rating: Càng lớn càng tốt
            norm_rating = d.rating / MAX_RATING

            # Price: Càng rẻ (multiplier gần 1.0) điểm càng cao
            # Công thức: (MAX - actual) / (MAX - MIN) → 1.0 khi giá bình thường, 0.0 khi giá x2.0
            norm_price = max(0, (MAX_PRICE_MULTIPLIER - d.priceMultiplier) /
                              (MAX_PRICE_MULTIPLIER - MIN_PRICE_MULTIPLIER))

            feature_matrix.append([norm_dist, norm_eta, norm_rating, norm_price])

        # Chuyển đổi List thành Numpy Array (Ma trận kích thước N x 4)
        X = np.array(feature_matrix)

        # --- BƯỚC 2: TÍNH TOÁN MA TRẬN (MATRIX MULTIPLICATION) ---
        # X  shape: (N, 4)  — N tài xế, 4 features đã chuẩn hóa
        # W  shape: (4,)    — Vector trọng số [dist, eta, rating, price]
        # Kết quả: mảng N điểm tổng hợp, nhân 100 để ra thang điểm 0–100
        weighted_scores = np.dot(X, WEIGHTS) * 100

        # --- BƯỚC 3: TẠO KẾT QUẢ VÀ SẮP XẾP ---
        ranking_list = []
        for i in range(len(drivers)):
            current_score = float(round(weighted_scores[i], 2))
            details_str = (
                f"DistNorm={feature_matrix[i][0]:.2f}, "
                f"EtaNorm={feature_matrix[i][1]:.2f}, "
                f"RatNorm={feature_matrix[i][2]:.2f}, "
                f"PriceNorm={feature_matrix[i][3]:.2f}"
            )
            
            ranking_list.append(
                DriverScore(
                    driverId=driver_ids[i],
                    score=current_score,
                    details=details_str
                )
            )

        # Sắp xếp danh sách từ điểm cao xuống thấp
        ranking_list.sort(key=lambda x: x.score, reverse=True)

        # Xác định tài xế tốt nhất
        best_driver = ranking_list[0]
        
        response = AiScoringResponse(
            bestDriverId=best_driver.driverId,
            highestScore=best_driver.score,
            ranking=ranking_list
        )

        logger.info(f"Đã chọn được tài xế tốt nhất: {best_driver.driverId} với điểm số {best_driver.score}")
        return response

    except Exception as e:
        logger.error(f"Lỗi trong quá trình tính toán AI Score: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal AI Matrix Calculation Error")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
