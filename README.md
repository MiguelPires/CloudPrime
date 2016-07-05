
# CloudPrime # 

## Further instructions ##

The project's subdirectories contain instructions on how to run each module.

## Deployment ##

If you want to deploy the modules to AWS, copy the project to an
instance and use the rc.local files that are included in the LoadBalancer and
webServer directories (the scripts' path should be /etc/rc.local). Note that 
the rc.local scripts assume that maven is installed in /usr/local/apache-maven. 
To install maven, just run the following commands:

<pre><code>$ wget http://www-us.apache.org/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
$ tar -xzf apache-maven-3.3.9-bin.tar.gz
$ mv apache-maven-3.3.9 apache-maven
$ sudo mv apache-maven /usr/local </code></pre>

The rc.local scripts also assume that the project is already compiled. The 
compilation and instrumentation commands are commented out in the scripts. 
Upon boot of the instance, the script will run the module.

Note that the scripts will redirect the output to a /tmp/rc.local.log file, so if
you want to see what's going on just run:

<pre><code>$ tail -f /tmp/rc.local.log</code></pre>

Also, don't forget to install Java:

<pre><code>$ sudo yum update
$ sudo yum install java-devel</code></pre>

If you have any questions or remarks, please send them to miguel(dot)pires(at)ist.utl.pt

