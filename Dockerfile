# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage: compila el artefacto con Maven 3.9 sobre JDK 21.
# Se usa la imagen oficial de Maven en lugar del wrapper porque el repositorio
# no versiona maven-wrapper.jar (distributionType=only-script).
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Capa de dependencias: se resuelven antes de copiar el codigo fuente para
# aprovechar la cache de Docker cuando solo cambia el codigo.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
COPY config ./config

RUN mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Runtime stage: imagen minima con JRE 21.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Usuario no-root: la imagen no necesita privilegios de root en runtime.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /build/target/portfolio-*.jar app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
