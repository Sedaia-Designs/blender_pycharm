FROM eclipse-temurin:21-jdk-jammy

COPY --from=ghcr.io/astral-sh/uv:0.11.7 /uv /uvx /usr/local/bin/

RUN apt-get update \
    && apt-get install --yes --no-install-recommends git unzip perl curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN uv python install 3.11.7

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew

ENTRYPOINT ["bash", "scripts/ci-gates.sh"]
