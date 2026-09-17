# syntax=docker/dockerfile:1
#
# Build feito a partir da raiz do repositório, porque este é um projeto Maven
# multi-módulo: o serviço depende do POM pai e do módulo contracts.
#
#   docker build -f notification-service/Dockerfile -t ecommerce/notification-service .

# ---------- build ----------
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Os POMs entram antes do código-fonte: enquanto as dependências não mudarem,
# o Docker reaproveita a camada de download mesmo que o código mude.
#
# Todos os módulos do reator precisam estar listados aqui, mesmo os que este
# serviço não usa: o Maven recusa um POM pai cujo módulo declarado não existe.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY contracts/pom.xml contracts/
COPY auth-service/pom.xml auth-service/
COPY orders-service/pom.xml orders-service/
COPY inventory-service/pom.xml inventory-service/
COPY notification-service/pom.xml notification-service/
COPY e2e-tests/pom.xml e2e-tests/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl notification-service -am dependency:go-offline

COPY contracts/src contracts/src
COPY notification-service/src notification-service/src
# Os testes rodam no CI, com Postgres e Kafka de verdade; repeti-los aqui exigiria
# Docker dentro do build e não acrescentaria garantia nenhuma.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl notification-service -am -DskipTests package

# O jar é desmontado em camadas para que dependências e aplicação virem camadas
# separadas na imagem: um deploy que só muda o código reenvia poucos megabytes.
RUN java -Djarmode=tools -jar notification-service/target/notification-service-*.jar extract --layers --launcher --destination /build/extracted

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Usuário sem privilégios: um processo comprometido não é root dentro do container.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app

COPY --from=builder --chown=spring:spring /build/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /build/extracted/application/ ./

EXPOSE 8084

# MaxRAMPercentage em vez de -Xmx fixo: a JVM respeita o limite de memória do
# container, qualquer que seja ele.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
