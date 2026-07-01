#!/bin/bash
# ==============================================================================
# TradePulse — EC2 Deployment Script
# This script automates installing Docker, Java 21, Maven, and running the stack.
# Target OS: Ubuntu 22.04 / 24.04 LTS on AWS EC2 (t3.xlarge recommended)
# ==============================================================================

set -e

echo "=== 1. Updating System Packages ==="
sudo apt-get update -y
sudo apt-get upgrade -y

echo "=== 2. Installing Prerequisites (Git, Java 21, Maven, Curl) ==="
sudo apt-get install -y git curl wget unzip openjdk-21-jdk maven

echo "=== 3. Installing Docker Engine ==="
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    rm get-docker.sh
    echo "Docker installed successfully."
else
    echo "Docker is already installed."
fi

echo "=== 4. Installing Docker Compose ==="
if ! docker compose version &> /dev/null; then
    sudo apt-get install -y docker-compose-plugin
fi
echo "Docker Compose installed: $(docker compose version)"

echo "=== 5. Cloning Project Repository ==="
# Since this script runs on the EC2 instance, we assume the user clones the repo first.
# If running as User Data, uncomment and edit the lines below:
# git clone https://github.com/ PhamNgocTan24/tradepulse.git /home/ubuntu/tradepulse
# cd /home/ubuntu/tradepulse

echo "=== 6. Building Java Microservices ==="
# Build the multi-module Maven project (skipping tests for speed on EC2)
mvn clean package -DskipTests

echo "=== 7. Starting Local Infrastructure (DBs & Kafka) ==="
# Start Postgres, Mongo, Redis, Zookeeper, Kafka, Kafka-UI
cd docker
docker compose down || true
docker compose up -d

echo "=== Setup complete! ==="
echo "You can check running containers using: docker ps"
echo "Kafka UI is running at http://localhost:8989"
echo "Please restart your shell or log out and log back in to apply Docker group changes."
