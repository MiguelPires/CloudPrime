To build the project run:
	mvn clean install

If the load balancer is being executed in AWS:
	- There is a property called "inAWS" in the pom.xml that must be altered to true

To run the load balancer:
	mvn exec:java
