for svc in api-gateway user-service product-service order-service payment-service; do
  docker build -t kmawashreh/personal_projects_repo/general:$svc ./$svc
  docker push kmawashreh/personal_projects_repo/general:$svc
done