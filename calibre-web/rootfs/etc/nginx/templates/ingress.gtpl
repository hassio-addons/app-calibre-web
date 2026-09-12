server {
    listen {{ .interface }}:{{ .port }} default_server;

    include /etc/nginx/includes/server_params.conf;
    include /etc/nginx/includes/proxy_params.conf;

    # Calibre-Web can be served from below the root of a domain, which is what
    # Ingress does, as long as it is told where it has been put. Home Assistant
    # hands out a different path per session, so it is passed on per request and
    # Calibre-Web builds its links around it.
    proxy_set_header X-Script-Name $http_x_ingress_path;

    location / {
        allow   172.30.32.2;
        deny    all;

        proxy_pass http://backend;
    }
}
