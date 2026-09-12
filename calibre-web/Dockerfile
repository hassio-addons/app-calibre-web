ARG BUILD_FROM=ghcr.io/hassio-addons/debian-base:9.4.0

# Calibre-Web reads a library, it does not convert one. Everything this app can
# do with a book that is not simply handing it over, the "Convert" button, the
# format a Kindle wants, the metadata read out of an upload, is done by
# calibre's own command line tools.
#
# Calibre is not packaged for this base, and the distribution package that does
# exist elsewhere pulls in the whole Qt desktop stack as a hard dependency.
# Upstream's own bundle is self-contained, is the same build the calibre project
# tests, and can be pinned to a version of our choosing, so that is what is used
# here, with the parts only a desktop needs taken back out.
# hadolint ignore=DL3006
FROM ${BUILD_FROM} AS calibre

# Set shell
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG BUILD_ARCH=amd64
ARG CALIBRE_VERSION="9.14.0"
RUN \
    apt-get update \
    \
    && apt-get install -y --no-install-recommends \
        xz-utils=5.8.1-1+deb13u1 \
    \
    && if [[ "${BUILD_ARCH}" = "aarch64" ]]; then ARCH="arm64"; fi \
    && if [[ "${BUILD_ARCH}" = "amd64" ]]; then ARCH="x86_64"; fi \
    \
    && mkdir -p /opt/calibre \
    && curl -J -L -o /tmp/calibre.txz \
        "https://download.calibre-ebook.com/${CALIBRE_VERSION}/calibre-${CALIBRE_VERSION}-${ARCH}.txz" \
    && tar xf /tmp/calibre.txz -C /opt/calibre \
    \
    # What is left behind is what a desktop calibre needs and a conversion does
    # not: the voices and the machine learning runtime behind the reader's
    # read-aloud button, the front end of calibre's own book server, Chromium's
    # developer tools, and calibre's interface translated into every language it
    # speaks. Chromium renders the pages for PDF output, and wants a translation
    # of its own to exist, so English stays.
    && rm -f -r \
        /opt/calibre/lib/calibre-extensions/piper.so \
        /opt/calibre/lib/libonnxruntime.so.1 \
        /opt/calibre/resources/content-server \
        /opt/calibre/resources/localization/locales.zip \
        /opt/calibre/resources/piper-voices.json \
        /opt/calibre/resources/qtwebengine_devtools_resources.pak \
        /opt/calibre/share/espeak-ng-data \
    && find /opt/calibre/translations/qtwebengine_locales \
        -name '*.pak' ! -name 'en-US.pak' -delete \
    \
    && rm -f -r /tmp/*

# Calibre-Web is installed from the Python package upstream publishes, into a
# virtual environment of its own. Only that environment is carried into the app
# image, so nothing that was needed to compile it comes along.
# hadolint ignore=DL3006
FROM ${BUILD_FROM} AS builder

# Set shell
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ENV \
    VIRTUAL_ENV=/opt/calibre-web

# The packages listed after Calibre-Web itself are its "metadata" extra, minus
# scholarly. That one is for looking books up in Google Scholar, and it arrives
# with Selenium and a documentation theme in tow; the other providers, the
# metadata read out of an uploaded book, and the comic and Kobo support are all
# in here. Version ranges are upstream's own, from the same file the extra is
# built out of.
ARG CALIBRE_WEB_VERSION="0.6.27"
RUN \
    apt-get update \
    \
    && apt-get install -y --no-install-recommends \
        build-essential=12.12 \
        python3-dev=3.13.5-1 \
        python3-venv=3.13.5-1 \
    \
    && python3 -m venv "${VIRTUAL_ENV}" \
    \
    && "${VIRTUAL_ENV}/bin/pip" install --no-cache-dir \
        "calibreweb[comics,kobo]==${CALIBRE_WEB_VERSION}" \
        "beautifulsoup4>=4.0.1,<4.16.0" \
        "faust-cchardet>=2.1.18,<2.1.20" \
        "html2text>=2020.1.16,<2025.4.16" \
        "markdown2>=2.0.0,<2.6.0" \
        "mutagen>=1.40.0,<1.50.0" \
        "py7zr>=0.15.0,<0.21.0" \
        "python-dateutil>=2.1,<2.10.0" \
        "rarfile>=3.2,<5.0" \
    \
    # Calibre-Web can update itself, by downloading a release and writing it
    # over its own files. Here that would put a version nobody chose on top of
    # the one the image was built with, and leave it there until this app is
    # rebuilt. Upstream already has a switch for the builds where updating is
    # not the installation's job, and this is one of those.
    && sed -i \
        's/^UPDATER_AVAILABLE = True$/UPDATER_AVAILABLE = False/' \
        "${VIRTUAL_ENV}"/lib/python*/site-packages/calibreweb/cps/constants.py \
    \
    && find "${VIRTUAL_ENV}" \
        \( \
            \( -type d -a \( -name test -o -name tests -o -name __pycache__ \) \) \
            -o \( -type f -a \( -name '*.pyc' -o -name '*.pyo' \) \) \
        \) \
        -exec rm -rf '{}' +

# hadolint ignore=DL3006
FROM ${BUILD_FROM}

# Set shell
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ENV \
    CACHE_DIRECTORY=/data/cache \
    CALIBRE_DBPATH=/data/calibre-web \
    CALIBRE_UNIX_SOCKET=/run/calibre-web.sock \
    PATH="/opt/calibre-web/bin:/opt/calibre:${PATH}" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    QTWEBENGINE_CHROMIUM_FLAGS="--no-sandbox --disable-gpu" \
    QT_QPA_PLATFORM=offscreen \
    VIRTUAL_ENV=/opt/calibre-web

# The libraries below the first two groups are calibre's. It brings its own Qt,
# but Qt still expects the X and OpenGL libraries of the system it runs on to be
# there, even with nothing on screen: Chromium renders the pages for PDF output
# and refuses to start without them.
#
# ImageMagick is how a cover is pulled out of an uploaded PDF, with Ghostscript
# behind it doing the rendering, and unrar-free is what reads a comic in a CBR.
# NGINX faces Ingress and the network; Calibre-Web itself only ever listens on a
# socket in /run.
RUN \
    apt-get update \
    \
    && apt-get install -y --no-install-recommends \
        ghostscript=10.05.1~dfsg-1+deb13u1 \
        libmagic1t64=1:5.46-5 \
        libmagickwand-7.q16-10=8:7.1.1.43+dfsg1-1+deb13u12 \
        nginx=1.26.3-3+deb13u8 \
        python3=3.13.5-1 \
        sqlite3=3.46.1-7+deb13u2 \
        unrar-free=1:0.3.1-1 \
    \
    && apt-get install -y --no-install-recommends \
        fonts-liberation=1:2.1.5-3 \
        libasound2t64=1.2.14-1+deb13u1 \
        libegl1=1.7.0-1+b2 \
        libfontconfig1=2.15.0-2.3 \
        libfreetype6=2.13.3+dfsg-1+deb13u1 \
        libgl1=1.7.0-1+b2 \
        libglx-mesa0=25.0.7-2+deb13u1 \
        libnss3=2:3.110-1+deb13u4 \
        libopengl0=1.7.0-1+b2 \
        libxcomposite1=1:0.4.6-1 \
        libxdamage1=1:1.1.6-1+b2 \
        libxfixes3=1:6.0.0-2+b4 \
        libxi6=2:1.8.2-1 \
        libxkbcommon0=1.7.0-2 \
        libxkbfile1=1:1.1.0-1+b4 \
        libxrandr2=2:1.5.4-1+b3 \
        libxtst6=2:1.2.5-1 \
    \
    && rm -f -r /etc/nginx \
    \
    && apt-get clean \
    && rm -f -r \
        /tmp/* \
        /var/{cache,log}/* \
        /var/lib/apt/lists/*

# Kepubify turns an EPUB into the KEPUB a Kobo wants, which is what makes
# reading progress and statistics work on the device. Calibre could do the same
# job, but it is slower at it and Calibre-Web prefers this when both are
# present.
#
# Calibre-Web looks for this binary by name, and the only Linux names it knows
# are 32 and 64 bit. The name is accurate on both architectures here, and the
# alternative is a path that the app blanks out the next time somebody saves the
# configuration page.
ARG BUILD_ARCH=amd64
ARG KEPUBIFY_VERSION="4.0.4"
RUN \
    if [[ "${BUILD_ARCH}" = "aarch64" ]]; then ARCH="arm64"; fi \
    && if [[ "${BUILD_ARCH}" = "amd64" ]]; then ARCH="64bit"; fi \
    \
    && mkdir -p /opt/kepubify \
    && curl -J -L -o /opt/kepubify/kepubify-linux-64bit \
        "https://github.com/pgaskin/kepubify/releases/download/v${KEPUBIFY_VERSION}/kepubify-linux-${ARCH}" \
    && chmod a+x /opt/kepubify/kepubify-linux-64bit

COPY --from=calibre /opt/calibre /opt/calibre
COPY --from=builder /opt/calibre-web /opt/calibre-web

# Copy root filesystem
COPY rootfs /

# Health check
HEALTHCHECK \
    CMD curl --silent --fail --unix-socket /run/calibre-web.sock http://localhost/login || exit 1

# Build arguments
ARG BUILD_ARCH
ARG BUILD_DATE
ARG BUILD_DESCRIPTION
ARG BUILD_NAME
ARG BUILD_REF
ARG BUILD_REPOSITORY
ARG BUILD_VERSION

# Labels
LABEL \
    io.hass.name="${BUILD_NAME}" \
    io.hass.description="${BUILD_DESCRIPTION}" \
    io.hass.arch="${BUILD_ARCH}" \
    io.hass.type="addon" \
    io.hass.version=${BUILD_VERSION} \
    maintainer="Franck Nijhof <opensource@frenck.dev>" \
    org.opencontainers.image.title="${BUILD_NAME}" \
    org.opencontainers.image.description="${BUILD_DESCRIPTION}" \
    org.opencontainers.image.vendor="Home Assistant Community Apps" \
    org.opencontainers.image.authors="Franck Nijhof <opensource@frenck.dev>" \
    org.opencontainers.image.licenses="MIT" \
    org.opencontainers.image.url="https://frenck.dev/home-assistant-apps" \
    org.opencontainers.image.source="https://github.com/${BUILD_REPOSITORY}" \
    org.opencontainers.image.documentation="https://github.com/${BUILD_REPOSITORY}/blob/main/README.md" \
    org.opencontainers.image.created=${BUILD_DATE} \
    org.opencontainers.image.revision=${BUILD_REF} \
    org.opencontainers.image.version=${BUILD_VERSION}
