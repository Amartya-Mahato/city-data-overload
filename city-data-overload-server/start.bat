@echo off
REM City Data Overload Server - Windows Startup Script
REM This script sets up and starts the entire application stack

echo 🏙️  City Data Overload Server - Windows Startup Script
echo =====================================================

REM Check if Docker is installed
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker is not installed. Please install Docker Desktop first.
    pause
    exit /b 1
)

REM Check if Docker Compose is installed
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker Compose is not installed. Please install Docker Compose first.
    pause
    exit /b 1
)

echo ✅ Docker and Docker Compose are installed

REM Check if config directory exists
if not exist "config" (
    echo 📁 Creating config directory...
    mkdir config
    echo ⚠️  Please add your service account JSON files to the config\ directory:
    echo    - config\gcp-service-account.json
    echo    - config\firebase-service-account.json
)

REM Check if .env file exists
if not exist ".env" (
    echo 📄 Creating .env file template...
    (
        echo # Google Cloud Configuration
        echo GCP_PROJECT_ID=your-gcp-project
        echo GCP_LOCATION=us-central1
        echo GOOGLE_APPLICATION_CREDENTIALS=/app/config/gcp-service-account.json
        echo.
        echo # Firebase Configuration
        echo FIREBASE_PROJECT_ID=your-firebase-project
        echo FIREBASE_CREDENTIALS_PATH=/app/config/firebase-service-account.json
        echo FIREBASE_STORAGE_BUCKET=your-bucket.appspot.com
        echo.
        echo # External API Keys
        echo TWITTER_BEARER_TOKEN=your-twitter-token
        echo DATA_GOV_API_KEY=your-data-gov-key
        echo LINKEDIN_ACCESS_TOKEN=your-linkedin-token
        echo.
        echo # Database Configuration
        echo DB_USERNAME=citydata
        echo DB_PASSWORD=citydata123
        echo.
        echo # Redis Configuration
        echo REDIS_PASSWORD=redis123
    ) > .env
    echo ⚠️  Please update the .env file with your actual configuration values
)

echo.
echo 🚀 Starting City Data Overload Server...
echo This may take a few minutes on first run...

REM Start infrastructure services first
echo 📦 Starting infrastructure services...
docker-compose up -d mysql redis kafka zookeeper

REM Wait for infrastructure to be ready
echo ⏳ Waiting for infrastructure services to initialize...
timeout /t 30 /nobreak > nul

REM Start Kafka UI
echo 🖥️  Starting Kafka UI...
docker-compose up -d kafka-ui

REM Start monitoring services
echo 📊 Starting monitoring services...
docker-compose up -d prometheus grafana

REM Build and start the main application
echo 🔨 Building and starting the main application...
docker-compose up --build -d city-data-server

REM Wait for the application to be ready
echo ⏳ Waiting for application to be ready...
:wait_loop
ping -n 5 127.0.0.1 > nul
curl -s -f http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo    Still starting...
    goto wait_loop
)

echo.
echo 🎉 City Data Overload Server is now running!
echo.
echo 📱 Access Points:
echo   🌐 API Server:        http://localhost:8080
echo   📖 API Documentation: http://localhost:8080/swagger-ui.html
echo   🔍 Kafka UI:          http://localhost:8081
echo   📊 Grafana:           http://localhost:3000 (admin/admin123)
echo   📈 Prometheus:        http://localhost:9090
echo.
echo 🧪 Test the API:
echo   curl http://localhost:8080/actuator/health
echo   curl "http://localhost:8080/api/events?lat=12.9716&lon=77.5946&radius=5000"
echo.
echo 📋 To view logs:
echo   docker-compose logs -f city-data-server
echo.
echo 🛑 To stop the services:
echo   docker-compose down
echo.

pause
