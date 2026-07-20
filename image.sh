for svc in api-gateway user-service product-service order-service payment-service; do
  docker build -t ghcr.io/kawashreh/ecommerce-microservices-platform/$svc:latest -f $svc/Dockerfile .
  docker push ghcr.io/kawashreh/ecommerce-microservices-platform/$svc:latest
done