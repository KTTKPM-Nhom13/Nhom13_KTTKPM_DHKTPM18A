import os
import jwt
from fastapi import HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

security_agent = HTTPBearer()

# Resolve path to public key
CERT_DIR = os.path.dirname(os.path.abspath(__file__))
PUBLIC_KEY_PATH = os.path.join(CERT_DIR, "certs", "public_key.pem")

try:
    with open(PUBLIC_KEY_PATH, "r") as f:
        PUBLIC_KEY_PEM = f.read()
except Exception as e:
    raise RuntimeError(f"Could not load public key PEM: {str(e)}")

def verify_and_get_user(credentials: HTTPAuthorizationCredentials = Security(security_agent)):
    token = credentials.credentials
    try:
        # Decode the token using RS256 with the public key
        payload = jwt.decode(token, PUBLIC_KEY_PEM, algorithms=["RS256"])
        print(f"DEBUG JWT PAYLOAD: {payload}", flush=True)
        
        username = payload.get("sub")
        
        # 1. Trích xuất vai trò từ claim direct 'role' hoặc 'scope' trước tiên (rất phổ biến ở JWT của Spring Boot)
        role = payload.get("role") or payload.get("scope")
        
        # 2. Nếu không tìm thấy, kiểm tra danh sách authorities hoặc dùng giá trị mặc định
        if not role:
            authorities = payload.get("authorities", [])
            print(f"DEBUG AUTHORITIES: {authorities}", flush=True)
            if isinstance(authorities, str):
                authorities = [authorities]
            role = authorities[0] if authorities else "ROLE_USER"
            
        print(f"DEBUG SELECTED ROLE: {role}", flush=True)
        
        return {
            "username": username,
            "role": role,
            "token": token
        }
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token has expired")
    except jwt.InvalidTokenError as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {str(e)}")
