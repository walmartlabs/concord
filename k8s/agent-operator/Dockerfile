ARG from_image=gcr.io/distroless/java17
FROM $from_image

COPY target/operator.jar /operator.jar
CMD ["/operator.jar", "-Xmx128m"]
