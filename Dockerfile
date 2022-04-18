FROM clojure:openjdk-13-lein-2.9.1 AS lein

COPY target/uberjar/herfi.jar /herfi/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/herfi/app.jar"]
