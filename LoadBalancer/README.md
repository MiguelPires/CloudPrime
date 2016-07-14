# LoadBalancer

## Deployment ##

To build the project:
<pre><code> $ mvn clean install</code></pre>

If the load balancer is being executed in AWS, there is a property called "inAWS" in the pom.xml that must be altered to true

To run the load balancer:
<pre><code> $ mvn exec:java </code></pre>
