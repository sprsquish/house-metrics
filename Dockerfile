FROM --platform=$BUILDPLATFORM golang:1.18 as base

WORKDIR /src

COPY ./src/go.mod ./src/go.sum ./
RUN go mod download

FROM --platform=$BUILDPLATFORM base AS build
COPY ./src/ .

ARG TARGETOS
ARG TARGETARCH

RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
  go build -o /app ./cmd

FROM --platform=$BUILDPLATFORM gcr.io/distroless/static

COPY --from=build /app /app

CMD ["/app"]
