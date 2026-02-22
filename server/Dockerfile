FROM golang:1.22-alpine AS builder

RUN apk add --no-cache gcc musl-dev sqlite-dev

WORKDIR /build
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=1 go build -o spela-server ./cmd/server
RUN CGO_ENABLED=1 go build -o spela-seed ./cmd/seed

FROM alpine:3.20

RUN apk add --no-cache sqlite-libs ca-certificates

RUN adduser -D -h /app spela
USER spela
WORKDIR /app

COPY --from=builder /build/spela-server .
COPY --from=builder /build/spela-seed .
COPY --from=builder /build/entrypoint.sh .

RUN mkdir -p /app/data /app/games /app/saves /app/cores /app/images /app/dats
COPY --from=builder /build/dats/ /app/dats/

ENV SPELA_PORT=8080
ENV SPELA_DB_PATH=/app/data/spela.db
ENV SPELA_GAME_DIRS=/app/games
ENV SPELA_SAVE_DIR=/app/saves
ENV SPELA_CORE_DIR=/app/cores
ENV SPELA_DAT_DIR=/app/dats
ENV GIN_MODE=release

EXPOSE 8080

ENTRYPOINT ["./entrypoint.sh"]
