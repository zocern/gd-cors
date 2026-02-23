# Build 阶段
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run 阶段
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# 设置 UTF-8 编码环境，避免中文乱码
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

# 设置时区为上海
RUN apt-get update && \
    apt-get install -y tzdata && \
    ln -fs /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    dpkg-reconfigure --frontend noninteractive tzdata && \
    rm -rf /var/lib/apt/lists/*

EXPOSE 8080

#ENTRYPOINT ["java", "-jar", "app.jar"]

#ENTRYPOINT ["java", \
#    "-server", \
#    "-Xms48g", \
#    "-Xmx48g", \
#    "-XX:MaxMetaspaceSize=512m", \
#    "-XX:MaxDirectMemorySize=8g", \
#    "-XX:+UseZGC", \
#    "-jar", "app.jar"]

ENTRYPOINT ["java", \
    "-server", \
    "-XX:InitialRAMPercentage=75.0", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:MinRAMPercentage=75.0", \
    "-XX:MaxDirectMemorySize=8g", \
    "-XX:+UseZGC", \
    "-jar", "app.jar"]


