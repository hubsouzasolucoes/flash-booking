FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/nginx.conf

# Validate the image-owned configuration while building, before Compose starts it.
RUN nginx -t

EXPOSE 8080
