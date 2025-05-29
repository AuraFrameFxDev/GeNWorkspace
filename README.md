# Genesis AI - Collaborative Communication Platform

A FastAPI backend with Firebase integration for the Genesis AI application.

## Features

- 🔐 Firebase Authentication
- 💬 Real-time messaging
- 📁 File uploads with Firebase Storage
- 🔄 Task synchronization
- 📱 Mobile-optimized API
- 🛡️ Secure endpoints with JWT

## Prerequisites

- Python 3.8+
- Firebase project with Authentication and Storage enabled
- Firebase Admin SDK credentials

## Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd GCollebComm-main
   ```

2. **Set up a virtual environment**
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies**
   ```bash
   pip install -r requirements.txt
   ```

4. **Configure Firebase**
   - Place your Firebase Admin SDK credentials in `server/credentials/`
   - Make sure the credentials file is named: `auraframefx-firebase-adminsdk-fbsvc-9c493ac034.json`

5. **Start the server**
   ```bash
   # Linux/macOS
   chmod +x scripts/server_control.sh
   ./scripts/server_control.sh start

   # Windows
   .\scripts\server_control.bat start
   ```

6. **Access the API**
   - API Documentation: http://localhost:8000/docs
   - Interactive API: http://localhost:8000/redoc

## Project Structure

```
GCollebComm-main/
├── app/                    # Android application
├── server/                 # FastAPI backend
│   ├── __init__.py
│   ├── app.py             # Main application
│   ├── config.py          # Configuration settings
│   └── credentials/       # Firebase credentials
├── scripts/               # Utility scripts
│   ├── server_control.sh  # Server control (Linux/macOS)
│   └── server_control.bat # Server control (Windows)
├── requirements.txt       # Python dependencies
└── README.md             # This file
```

## API Endpoints

- `GET /` - API status and documentation
- `GET /health` - Health check
- `POST /sendMessage` - Send a chat message
- `POST /importFile` - Upload a file
- `POST /toggleRoot` - Toggle root access
- `GET /getAiQuestions` - Get suggested AI questions
- `POST /syncTasks` - Synchronize tasks

## Development

### Environment Setup

1. Install development dependencies:
   ```bash
   pip install -r requirements-dev.txt
   ```

2. Set up pre-commit hooks:
   ```bash
   pre-commit install
   ```

### Running Tests

```bash
pytest
```

## Deployment

### Production

1. Set `DEBUG=False` in `server/config.py`
2. Configure a production WSGI server (Gunicorn, uWSGI, etc.)
3. Set up a reverse proxy (Nginx, Apache)
4. Configure SSL/TLS

### Docker (Optional)

```bash
docker-compose up --build
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support, please open an issue in the repository.
