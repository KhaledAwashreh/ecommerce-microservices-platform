# Define service directories
CONFIG_SERVICE_DIR=.config-service
EUREKA_SERVICE_DIR=.naming-server
API_GATEWAY_DIR=.api-gateway

# Define default JDKMaven command (assuming Maven projects)
MVN=mvn spring-bootrun

# Start Config Service
start-config
	@echo Starting Config Service...
	cd $(CONFIG_SERVICE_DIR) && $(MVN) & 
	sleep 10  # wait for it to start

# Start Eureka Service
start-eureka start-config
	@echo Starting Eureka Service...
	cd $(EUREKA_SERVICE_DIR) && $(MVN) & 
	sleep 10  # wait for it to start

# Start Order Service
start-api-gateway start-eureka
	@echo Starting Api gateway...
	cd $(API_GATEWAY_DIR) && $(MVN)

# Start all services in proper order
start-all start-order
	@echo All services started successfully!

# Stop all services (requires pkill, adjust as needed)
stop-all
	@echo Stopping all Spring Boot services...
	pkill -f 'java -jar'  true

.PHONY start-config start-eureka start-order start-all stop-all
