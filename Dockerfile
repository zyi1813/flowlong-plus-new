FROM openjdk:8

MAINTAINER jiaixaoyu

ENV TZ Asia/Shanghai

WORKDIR /workdir

COPY target/flowlong-engine-1.0.jar /workdir/app.jar

ENTRYPOINT ["java","-jar","/workdir/app.jar"]

EXPOSE 19092