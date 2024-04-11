FROM openjdk:21
RUN microdnf update \
 && microdnf install --nodocs wget unzip \
 && microdnf clean all \
 && rm -rf /var/cache/yum
COPY ./target/isolation_forest_stats_poc-1.0-SNAPSHOT.jar /usr/app/
WORKDIR /usr/app
ENTRYPOINT ["java", "-jar", "isolation_forest_stats_poc-1.0-SNAPSHOT.jar"]