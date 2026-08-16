FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends git python3 unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew

ENTRYPOINT ["bash", "scripts/local-gitlab-ci-job.sh"]
