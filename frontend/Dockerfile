FROM nginx:alpine
LABEL maintainer="dwaigx"
LABEL description="小登哥的灵魂合盘 - 前端静态页面"

COPY . /usr/share/nginx/html

# Nginx config: SPA + API proxy
RUN cat > /etc/nginx/conf.d/default.conf << 'EOF'
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 180s;
        proxy_read_timeout 180s;
    }
}
EOF

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
