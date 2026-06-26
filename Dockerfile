# Unified multi-stage Dockerfile: builds both frontend and backend into a
# single container. The Go server serves the Vite-built frontend via
# SPELA_FRONTEND_DIR.

# --- Stage 1: Build frontend ---
FROM node:22-alpine AS frontend-builder

WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci

COPY web/ .
RUN npx vite build

# --- Stage 2: Build backend ---
FROM golang:1.25-alpine AS backend-builder

RUN apk add --no-cache gcc musl-dev sqlite-dev

WORKDIR /build
COPY server/go.mod server/go.sum ./
RUN go mod download

COPY server/ .

# No-Intro / MAME DAT files (CRC verification + name resolution) are bundled in
# server/dats/ and copied in with the source above — no build-time download.
# (download-dats.sh is a bash helper for refreshing that bundle locally.)

ARG SPELA_VERSION=dev
RUN CGO_ENABLED=1 go build -ldflags "-X main.version=${SPELA_VERSION}" -o spela-server ./cmd/server
RUN CGO_ENABLED=1 go build -o spela-seed ./cmd/seed

# --- Stage 3: Runtime ---
FROM alpine:3.20

RUN apk add --no-cache sqlite-libs ca-certificates su-exec

RUN adduser -D -h /app spela
WORKDIR /app

COPY --from=backend-builder /build/spela-server .
COPY --from=backend-builder /build/spela-seed .
COPY --from=backend-builder /build/entrypoint.sh .

RUN mkdir -p /app/data /app/games /app/saves /app/cores /app/images /app/dats /app/frontend
COPY --from=backend-builder /build/dats/ /app/dats/
COPY --from=frontend-builder /app/web/dist/ /app/frontend/
RUN chown -R spela:spela /app/data /app/saves /app/cores /app/images /app/dats

ENV SPELA_PORT=8080
ENV SPELA_DB_PATH=/app/data/spela.db
ENV SPELA_GAME_DIRS=/app/games
ENV SPELA_SAVE_DIR=/app/saves
ENV SPELA_CORE_DIR=/app/cores
ENV SPELA_DAT_DIR=/app/dats
ENV SPELA_FRONTEND_DIR=/app/frontend
ENV GIN_MODE=release

EXPOSE 8080

ENTRYPOINT ["./entrypoint.sh"]
