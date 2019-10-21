FROM maven:3.5-jdk-8-alpine AS builder

WORKDIR /usr/src/app

COPY . /usr/src/app

RUN mvn dependency:resolve

RUN cd src

RUN mvn package

FROM openjdk:8-jre-alpine

WORKDIR /usr/src/app

COPY --from=builder /usr/src/app/* ./

EXPOSE 80

CMD ["java", "-jar", "worker-jar-with-dependencies.jar"]