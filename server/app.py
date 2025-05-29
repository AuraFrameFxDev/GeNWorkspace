import os
import uuid
import json
from datetime import datetime
from typing import List, Optional, Dict, Any
from fastapi import FastAPI, HTTPException, Depends, status, UploadFile, File, Form, Header, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, FileResponse
from pydantic import BaseModel, Field
import firebase_admin
from firebase_admin import credentials, firestore, auth, storage
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
SERVICE_ACCOUNT_KEY_PATH = os.path.join(os.path.dirname(__file__), "credentials", "auraframefx-firebase-adminsdk-fbsvc-9c493ac034.json")

# Initialize Firebase Admin SDK
try:
    # Check if Firebase app is already initialized to avoid error on hot reload
    if not firebase_admin._apps:
        cred = credentials.Certificate(SERVICE_ACCOUNT_KEY_PATH)
        firebase_admin.initialize_app(cred, {
            'databaseURL': 'https://auraframefx.firebaseio.com',
            'storageBucket': 'auraframefx.appspot.com'
        })
    
    # Initialize Firestore and Storage
    db = firestore.client()
    bucket = storage.bucket()
    logger.info("Firebase Admin SDK initialized successfully")
except Exception as e:
    logger.error(f"Error initializing Firebase Admin SDK: {e}")
    raise

# Request/Response Models
class MessageRequest(BaseModel):
    message: str
    userId: str
    timestamp: Optional[datetime] = None

class MessageResponse(BaseModel):
    id: str
    message: str
    userId: str
    timestamp: datetime
    status: str = "success"

class ImportResponse(BaseModel):
    status: str = "success"
    message: Optional[str] = None

class RootToggleRequest(BaseModel):
    enabled: bool

class RootToggleResponse(BaseModel):
    status: str = "success"
    enabled: bool

class AskQuestion(BaseModel):
    question: str
    id: str

class AskResponse(BaseModel):
    questions: List[AskQuestion]
    status: str = "success"

class Task(BaseModel):
    id: str
    title: str
    description: Optional[str] = None
    is_completed: bool = False
    created_at: int = Field(default_factory=lambda: int(datetime.utcnow().timestamp() * 1000))
    updated_at: int = Field(default_factory=lambda: int(datetime.utcnow().timestamp() * 1000))
    is_deleted: bool = False

class SyncRequest(BaseModel):
    user_id: str
    last_sync_time: Optional[int] = None
    tasks: List[Task] = Field(default_factory=list)

class SyncResponse(BaseModel):
    status: str = "success"
    message: str = ""
    synced_tasks: List[Task] = Field(default_factory=list)
    server_time: int = Field(default_factory=lambda: int(datetime.utcnow().timestamp() * 1000))

# Initialize FastAPI
app = FastAPI(
    title="Genesis AI API",
    description="Backend API for Genesis AI application",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json"
)

# CORS Middleware Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, replace with specific origins
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
    expose_headers=["Content-Range", "X-Total-Count"],
    max_age=600
)

# Dependency to verify Firebase token and get user context
async def get_current_user(
    authorization: str = Header(..., description="Bearer token")
):
    """
    Verify the Firebase ID token and return the user's UID and additional claims.
    The token should be passed in the Authorization header as: 'Bearer <token>'
    """
    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication scheme",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    token = authorization.split(" ")[1]
    
    try:
        # Verify the token and get the user's Firebase UID and claims
        decoded_token = auth.verify_id_token(token)
        user_id = decoded_token["uid"]
        
        # Get additional user data from Firestore
        user_doc = db.collection("users").document(user_id).get()
        user_data = user_doc.to_dict() if user_doc.exists else {}
        
        return {
            "uid": user_id,
            "email": decoded_token.get("email"),
            "email_verified": decoded_token.get("email_verified", False),
            "is_admin": user_data.get("is_admin", False),
            "permissions": user_data.get("permissions", []),
            "metadata": user_data
        }
        
    except auth.ExpiredIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except auth.InvalidIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except Exception as e:
        logger.error(f"Error verifying token: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )

# Root Endpoint
@app.get(
    "/",
    status_code=status.HTTP_200_OK,
    tags=["Root"]
)
async def root():
    """
    Root endpoint that returns a welcome message and API information.
    
    Returns:
        dict: Welcome message and API status
    """
    return {
        "message": "Welcome to the Genesis AI API",
        "status": "running",
        "version": "1.0.0",
        "timestamp": datetime.utcnow().isoformat(),
        "documentation": "/docs"
    }

# Message Endpoints
@app.post(
    "/sendMessage", 
    response_model=MessageResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["Messages"]
)
async def send_message(
    message: MessageRequest, 
    user: dict = Depends(get_current_user)
):
    """
    Send a new message.
    
    - **message**: The message content
    - **userId**: Sender's user ID (automatically set from auth token)
    - **timestamp**: Server timestamp (automatically set)
    """
    try:
        # Validate message content
        if not message.message or len(message.message.strip()) == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Message cannot be empty"
            )
            
        # Prepare message data
        message_data = message.dict()
        message_data.update({
            "timestamp": firestore.SERVER_TIMESTAMP,
            "userId": user["uid"],
            "status": "sent"
        })
        
        # Add message to Firestore in a transaction
        @firestore.transactional
        def create_message(transaction, message_data):
            # Create a new document reference with auto-generated ID
            message_ref = db.collection("messages").document()
            
            # Set the document data
            transaction.set(message_ref, message_data)
            
            # Update the message data with the generated ID
            message_data["id"] = message_ref.id
            return message_data
            
        # Run the transaction
        transaction = db.transaction()
        message_data = create_message(transaction, message_data)
        
        # Log the successful message sending
        logger.info(f"Message sent by user {user['uid']} with ID: {message_data['id']}")
        
        # Return the created message with ID and status
        return MessageResponse(**message_data)
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error sending message: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An error occurred while sending the message"
        )

# File Endpoints
@app.post(
    "/importFile", 
    response_model=ImportResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["Files"]
)
async def import_file(
    file: UploadFile = File(..., description="File to upload"), 
    user: dict = Depends(get_current_user)
):
    """
    Upload and import a file to Firebase Storage.
    
    - **file**: The file to upload (supports any file type)
    - Returns: Public URL of the uploaded file
    """
    try:
        # Validate file
        if not file.filename or not file.content_type:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid file"
            )
            
        # Generate a secure filename
        file_extension = os.path.splitext(file.filename)[1].lower()
        if not file_extension:
            file_extension = ".bin"
            
        # Generate a unique filename with timestamp
        timestamp = datetime.utcnow().strftime("%Y%m%d-%H%M%S")
        filename = f"{timestamp}_{uuid.uuid4().hex}{file_extension}"
        
        # Define storage path
        user_dir = f"users/{user['uid']}/uploads"
        storage_path = f"{user_dir}/{filename}"
        
        try:
            # Read file content
            content = await file.read()
            
            # Validate file size (e.g., 10MB max)
            max_size = 10 * 1024 * 1024  # 10MB
            if len(content) > max_size:
                raise HTTPException(
                    status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                    detail=f"File size exceeds maximum limit of {max_size} bytes"
                )
                
            # Upload to Firebase Storage
            blob = bucket.blob(storage_path)
            blob.upload_from_string(
                content,
                content_type=file.content_type or 'application/octet-stream'
            )
            
            # Set metadata
            metadata = {
                'originalName': file.filename,
                'contentType': file.content_type,
                'size': len(content),
                'uploadedBy': user['uid'],
                'uploadedAt': datetime.utcnow().isoformat()
            }
            blob.metadata = metadata
            blob.patch()
            
            # Make the file publicly accessible (or implement signed URLs for private access)
            blob.make_public()
            
            # Log the successful upload
            logger.info(f"File uploaded by user {user['uid']} to {storage_path}")
            
            return {
                "status": "success",
                "message": "File uploaded successfully",
                "url": blob.public_url,
                "path": storage_path,
                "metadata": metadata
            }
            
        except HTTPException:
            raise
            
        except Exception as upload_error:
            logger.error(f"Error uploading file: {str(upload_error)}", exc_info=True)
            # Attempt to clean up if the blob was partially created
            try:
                if 'blob' in locals() and blob.exists():
                    blob.delete()
            except Exception as cleanup_error:
                logger.error(f"Error cleaning up failed upload: {str(cleanup_error)}")
                
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to upload file to storage"
            )
            
    except HTTPException:
        raise
        
    except Exception as e:
        logger.error(f"Unexpected error in import_file: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An unexpected error occurred while processing the file"
        )

# Root Toggle Endpoint
@app.post(
    "/toggleRoot", 
    response_model=RootToggleResponse,
    status_code=status.HTTP_200_OK,
    tags=["Admin"]
)
async def toggle_root(
    request: RootToggleRequest,
    user: dict = Depends(get_current_user)
):
    """
    Toggle root access for the application.
    
    - **enabled**: Boolean to enable/disable root access
    - Requires admin privileges
    """
    try:
        # Verify user has admin privileges
        user_doc = db.collection("users").document(user["uid"]).get()
        if not user_doc.exists or not user_doc.to_dict().get("is_admin", False):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Insufficient permissions. Admin access required."
            )
            
        # Update the root access setting (in a real app, this would be more secure)
        # Here we're just logging for demonstration
        logger.warning(f"Root access {'enabled' if request.enabled else 'disabled'} by admin {user['uid']}")
        
        # In a real implementation, you might update a Firestore document or use Firebase Remote Config
        return RootToggleResponse(
            status="success",
            enabled=request.enabled,
            message=f"Root access has been {'enabled' if request.enabled else 'disabled'}"
        )
        
    except HTTPException:
        raise
        
    except Exception as e:
        logger.error(f"Error toggling root access: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An error occurred while updating root access"
        )

        # Update root status in Firestore
        db.collection("settings").document("root").set({
            "enabled": request.enabled,
            "updatedBy": user["uid"],
            "updatedAt": firestore.SERVER_TIMESTAMP
        }, merge=True)

        return RootToggleResponse(status="success")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error toggling root access: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to toggle root access: {str(e)}")

# AI Questions Endpoint
@app.get(
    "/getAIQuestions",
    response_model=AskResponse,
    status_code=status.HTTP_200_OK,
    tags=["AI"]
)
async def get_ai_questions(
    limit: int = Query(5, ge=1, le=20, description="Number of questions to return"),
    user: dict = Depends(get_current_user)
):
    """
    Get a list of AI-generated questions.
    
    - **limit**: Number of questions to return (1-20)
    - Returns: List of question objects with IDs and text
    """
    try:
        # In a real implementation, this would generate or fetch AI questions
        # For now, we'll return some sample questions
        sample_questions = [
            {"id": str(uuid.uuid4()), "question": "What are the key principles of machine learning?"},
            {"id": str(uuid.uuid4()), "question": "How does a neural network work?"},
            {"id": str(uuid.uuid4()), "question": "What are the differences between AI and ML?"},
            {"id": str(uuid.uuid4()), "question": "How can we ensure AI is used ethically?"},
            {"id": str(uuid.uuid4()), "question": "What are some common NLP techniques?"}
        ]
        
        # Return a random subset of questions based on the limit
        selected_questions = sample_questions[:min(limit, len(sample_questions))]
        
        # Log the request
        logger.info(f"Returning {len(selected_questions)} AI questions to user {user['uid']}")
        
        return AskResponse(
            questions=selected_questions,
            status="success"
        )
        
    except Exception as e:
        logger.error(f"Error getting AI questions: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An error occurred while generating AI questions"
        )

# Task Sync Endpoint
@app.post(
    "/syncTasks",
    response_model=SyncResponse,
    status_code=status.HTTP_200_OK,
    tags=["Tasks"]
)
async def sync_tasks(
    request: SyncRequest,
    user: dict = Depends(get_current_user)
):
    """
    Synchronize tasks between the client and server.
    
    - **lastSyncTime**: Timestamp of last sync (optional)
    - **tasks**: List of tasks to sync
    - Returns: List of updated tasks and sync status
    """
    try:
        user_id = user["uid"]
        tasks_ref = db.collection("users").document(user_id).collection("tasks")
        
        # Get server changes since last sync
        query = tasks_ref.order_by("updatedAt", direction=firestore.Query.DESCENDING)
        if request.lastSyncTime:
            query = query.where("updatedAt", ">", request.lastSyncTime)
            
        server_changes = [doc.to_dict() for doc in query.stream()]
        
        # Process client updates
        if request.tasks:
            batch = db.batch()
            for task in request.tasks:
                task_ref = tasks_ref.document(task.id) if hasattr(task, 'id') else tasks_ref.document()
                task_data = task.dict()
                task_data["updatedAt"] = firestore.SERVER_TIMESTAMP
                batch.set(task_ref, task_data, merge=True)
            
            # Commit all task updates in a single batch
            batch.commit()
        
        # Get the latest sync time
        latest_sync = firestore.SERVER_TIMESTAMP
        
        logger.info(f"Synced {len(server_changes)} tasks for user {user_id}")
        

        return SyncResponse(
            tasks=server_changes,
            lastSyncTime=firestore.SERVER_TIMESTAMP,
            status="success"
        )
        
    except HTTPException:
        raise
        
    except Exception as e:
        logger.error(f"Error syncing tasks: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An error occurred while syncing tasks"
        )

# Health Check Endpoint
@app.get(
    "/health",
    status_code=status.HTTP_200_OK,
    tags=["Health"]
)
async def health_check():
    """
    Health check endpoint to verify the server is running.
    
    Returns:
        dict: Status information about the server
    """
    return {
        "status": "ok",
        "timestamp": datetime.utcnow().isoformat(),
        "version": "1.0.0"
    }

# Root Endpoint
@app.get("/")
async def read_root():
    return {
        "status": "success",
        "message": "Genesis AI Backend is running!",
        "version": "1.0.0",
        "endpoints": [
            {"path": "/sendMessage", "method": "POST", "description": "Send a new message"},
            {"path": "/importFile", "method": "POST", "description": "Import a file"},
            {"path": "/toggleRoot", "method": "POST", "description": "Toggle root access"},
            {"path": "/getAiQuestions", "method": "GET", "description": "Get AI questions"},
            {"path": "/syncTasks", "method": "POST", "description": "Synchronize tasks"}
        ]
    }

# Error Handlers
@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc):
    return JSONResponse(
        status_code=exc.status_code,
        content={"status": "error", "message": str(exc.detail)},
    )

# Server Startup
if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8000))
    uvicorn.run("app:app", host="0.0.0.0", port=port, reload=True)