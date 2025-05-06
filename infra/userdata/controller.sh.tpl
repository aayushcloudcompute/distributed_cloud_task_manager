#!/bin/bash
set -eux

# redirect all output for easier debugging
exec > /var/log/user-data.log 2>&1

# 1) Install Docker, Python3-pip, Java
apt-get update
apt-get install -y docker.io awscli openjdk-17-jre-headless

systemctl enable docker
systemctl start docker

# 3) Fetch your JAR from S3
aws s3 cp s3://${orchestrator_bucket}/orchestrator.jar /home/ubuntu/orchestrator.jar

# 4) Launch your app
docker run -d \
  --name p3-orchestrator-app \
  -p 8080:8080 \
  -v /home/ubuntu/orchestrator.jar:/app/orchestrator.jar:ro \
  -w /app \
  openjdk:17-jdk-slim \
  java -jar orchestrator.jar

