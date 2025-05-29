import os
from pathlib import Path

# Base directory
BASE_DIR = Path(__file__).parent

# Server settings
HOST = "0.0.0.0"
PORT = 8000
DEBUG = True
RELOAD = True

# Firebase settings
FIREBASE_CONFIG = {
    "project_id": "auraframefx",
    "storage_bucket": "auraframefx.appspot.com",
    "service_account": str(BASE_DIR / "credentials" / "auraframefx-firebase-adminsdk-fbsvc-9c493ac034.json")
}

# CORS settings
CORS_ORIGINS = ["*"]
ALLOW_CREDENTIALS = True

# Logging settings
LOGGING_CONFIG = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "standard": {
            "format": "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "standard",
            "level": "INFO",
            "stream": "ext://sys.stdout"
        },
        "file": {
            "class": "logging.handlers.RotatingFileHandler",
            "formatter": "standard",
            "filename": str(BASE_DIR / "logs" / "server.log"),
            "maxBytes": 10485760,  # 10MB
            "backupCount": 3,
            "encoding": "utf8"
        }
    },
    "loggers": {
        "": {
            "handlers": ["console", "file"],
            "level": "INFO",
            "propagate": True
        },
        "uvicorn": {
            "handlers": ["console", "file"],
            "level": "INFO",
            "propagate": False
        },
        "uvicorn.error": {
            "level": "INFO",
            "handlers": ["console", "file"],
            "propagate": False
        },
        "uvicorn.access": {
            "handlers": ["console", "file"],
            "level": "INFO",
            "propagate": False
        }
    }
}

# Create logs directory if it doesn't exist
os.makedirs(BASE_DIR / "logs", exist_ok=True)
