# Web Server

## Deployment

To build the project run:
<pre><code> $ mvn clean install </code></pre>

To instrument the server (in AWS):
<pre><code> $ java -cp target/classes:/home/ec2-user/aws-java-sdk-1.10.71/lib/aws-java-sdk-1.10.71.jar:/home/ec2-user/aws-java-sdk-1.10.71/third-party/lib/* cnv.cloudprime.webserver.Instrumentation target/classes/cnv/cloudprime/webserver/IntFactorization.class </code></pre>

Note that for the previous command to work, the AWS SDK for java must be in the home directory

To run the server:
<pre><code> $ mvn exec:java </code></pre>
