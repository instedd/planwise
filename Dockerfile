FROM java:8u91-jre

ADD ./target/uberjar/planwise-0.1.0-SNAPSHOT-standalone.jar /srv/

CMD ["java", "-jar", "/srv/planwise-0.1.0-SNAPSHOT-standalone.jar"]
