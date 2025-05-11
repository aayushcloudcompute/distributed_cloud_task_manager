FROM openjdk:17-jdk-slim

RUN apt-get update && \
    apt-get install -y python3 && \
    apt-get clean

WORKDIR /app
ENTRYPOINT ["bash", "-c"]