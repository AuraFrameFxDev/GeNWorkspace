# Use official Python image as base
FROM python:3.13-slim

# Create a non-root user
RUN adduser --disabled-password --gecos '' appuser

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONFAULTHANDLER=1 \
    PIP_NO_CACHE_DIR=off \
    PIP_DISABLE_PIP_VERSION_CHECK=on \
    PIP_DEFAULT_TIMEOUT=100 \
    POETRY_VERSION=1.6.1

# Set work directory
WORKDIR /app

# Install system dependencies (Moved this section and corrected its syntax)
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Poetry (Moved this section to before its use)
RUN pip install "poetry==$POETRY_VERSION"

# Copy only requirements to cache them in docker layer (Moved this to after Poetry installation)
# Ensure these files (poetry.lock and pyproject.toml) are in your GenComm1 directory (the build context root)
COPY poetry.lock pyproject.toml ./

# Install Python dependencies (Now properly positioned after Poetry is installed and files are copied)
# Added -vvv for verbose output as discussed for debugging
RUN poetry config virtualenvs.create false \
    && poetry install --no-interaction --no-ansi --no-root -vvv

# Copy the rest of the application
COPY --chown=appuser:appuser . .

# Switch to non-root user
USER appuser

# Expose the port the app runs on
EXPOSE 8000

# Command to run the application - THIS IS THE CRITICAL CHANGE
CMD ["uvicorn", "GenComm1.server.app:app", "--host", "0.0.0.0", "--port", "8000"]