#!/bin/bash
set -eux

# redirect all output for easier debugging
exec > /var/log/user-data.log 2>&1

# 1) Install Docker, Python3-pip, Java
apt-get update
apt-get install -y docker.io awscli openjdk-17-jre-headless

systemctl enable docker
systemctl start docker

# allow the ubuntu user to run docker without sudo
usermod -aG docker ubuntu

# ensure the logs dir exists and is writable
mkdir -p /home/ubuntu/p3-logs
chown ubuntu:ubuntu /home/ubuntu/p3-logs
chmod 755 /home/ubuntu/p3-logs