server {
    {{ if not .ssl }}
    listen {{ .port }} default_server;
    {{ else }}
    listen {{ .port }} default_server ssl;
    http2 on;
    {{ end }}

    include /etc/nginx/includes/server_params.conf;
    include /etc/nginx/includes/proxy_params.conf;

    proxy_redirect off;

    {{ if .ssl }}
    include /etc/nginx/includes/ssl_params.conf;

    ssl_certificate /ssl/{{ .certfile }};
    ssl_certificate_key /ssl/{{ .keyfile }};
    {{ end }}

    # Served from the root here, so Calibre-Web needs no prefix and must not be
    # handed one. These three are what it reads to work out the address it is
    # reached at, and taking them from a client on this port would let that
    # client decide what the links in a page, an OPDS feed or a Kobo sync
    # response point at.
    proxy_set_header X-Forwarded-Host "";
    proxy_set_header X-Scheme $scheme;
    proxy_set_header X-Script-Name "";

    location / {
        proxy_pass http://backend;
    }
}
