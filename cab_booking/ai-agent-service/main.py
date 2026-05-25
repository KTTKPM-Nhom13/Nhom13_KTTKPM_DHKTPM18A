import os
import uvicorn
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel
from security import verify_and_get_user
from agent import run_agent_session
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(title="CAB Booking AI Agent Service", version="1.0.0")

class ChatRequest(BaseModel):
    message: str

@app.get("/health")
@app.get("/api/v1/ai-agent/health")
def health():
    return {"status": "UP", "service": "cab-booking-ai-agent-service"}

@app.post("/api/v1/ai-agent/chat")
def handle_ai_chat(request: ChatRequest, user_info: dict = Depends(verify_and_get_user)):
    """
    Endpoint nhận tin nhắn chat từ ứng dụng khách, xác thực thông qua JWT của Spring Boot.
    """
    if not request.message or request.message.strip() == "":
        raise HTTPException(status_code=400, detail="Tin nhắn không được để trống")
    try:
        reply = run_agent_session(request.message, user_info)
        return {"reply": reply}
    except PermissionError as e:
        raise HTTPException(status_code=403, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Lỗi xử lý AI Agent: {str(e)}")

if __name__ == "__main__":
    port = int(os.getenv("PORT", 8099))
    reload_opt = os.getenv("RELOAD", "false").lower() == "true"
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=reload_opt)
