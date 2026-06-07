FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && apt-get install -y \
    python3 \
    ffmpeg \
    curl \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY src/ /app/src/

RUN javac -d out src/*.java

EXPOSE 8080
CMD ["java", "-cp", "out", "ServidorDescargas"]