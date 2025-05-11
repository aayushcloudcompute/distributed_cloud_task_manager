# Dockerfile
FROM openjdk:17-jdk-slim

# install Docker CLI so your orchestrator (running inside this container)
# can call `docker run` on the host socket:
RUN apt-get update && \
    apt-get install -y docker.io && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# copy in your fat JAR from the Maven build
COPY target/p3-0.0.1-SNAPSHOT.jar orchestrator.jar

# expose logs dir and Docker socket as volumes at runtime
VOLUME /app/logs
VOLUME /var/run/docker.sock

ENTRYPOINT ["java","-jar","/app/orchestrator.jar"]
