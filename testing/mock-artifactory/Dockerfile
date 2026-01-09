FROM nginx:alpine

# Install zip utility
RUN apk add --no-cache zip

# Copy migration source files
COPY ../test-artifacts/customer-migrations /tmp/customer-migrations
COPY ../test-artifacts/orders-migrations /tmp/orders-migrations
COPY ../test-artifacts/inventory-migrations /tmp/inventory-migrations

# Create artifact directory structure and generate ZIPs
RUN mkdir -p /usr/share/nginx/html/migrations/customer \
             /usr/share/nginx/html/migrations/orders \
             /usr/share/nginx/html/migrations/inventory && \
    cd /tmp/customer-migrations && zip -q -r /usr/share/nginx/html/migrations/customer/customer-1.5.0.zip *.sql && \
    cd /tmp/orders-migrations && zip -q -r /usr/share/nginx/html/migrations/orders/orders-2.3.1.zip *.sql && \
    cd /tmp/inventory-migrations && zip -q -r /usr/share/nginx/html/migrations/inventory/inventory-1.0.0.zip *.sql && \
    rm -rf /tmp/*-migrations

# Nginx will serve files from /usr/share/nginx/html/migrations
