FROM alpine:3.6

ADD ./ /src/
RUN adduser -S app \
    && apk add\
            --no-cache \
            --virtual build-dependencies \
		    openjdk8 \
            maven \
            git \
            curl \
            gnupg \
    && cd /src/ \
    && gpg --keyserver https://secchannel.rsk.co/release.asc --recv-keys 5DECF4415E3B8FA4 \
    && gpg --verify SHA256SUMS.asc \
    && sha256sum --check SHA256SUMS.asc \
    && ./configure.sh \
    && ./gradlew shadow reproducible \
    && cp rskj-core/build/libs/rskj-core-0.2.5-GINGER-all.jar /home/app/rskj.jar \
    && apk --no-cache --purge del build-dependencies \
    && apk --no-cache add openjdk8-jre su-exec \
    && rm -rf /src/

CMD su-exec app java -jar /home/app/rskj.jar

EXPOSE 21000
