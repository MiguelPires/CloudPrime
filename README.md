
# CloudPrime 

**CloudPrime** is a load balancer/auto-scaler that uses regression to predict future load based on current requests and scale appropriately. The auto-scaler collects performance metrics associated with certain input requests and uses them to train a predictive model. This model is then used to auto-scale an elastic cluster of web servers that provide an integer factorization service. The system is organized into four main components:
* **Web Servers** - EC2 instances that receive HTTP requests to perform factorizations, run them
and return the results. 
* **Load Balancer** - The load balancer is the entry point into the CloudPrime system. It
receives requests and selects an active web server based on the each server's load.
* **Auto-Scaler** - The auto-scaler collects system performance metrics and adjusts the cluster's size to strike a balance between minimizing latency and minimizing operational costs. 
* **Metrics Storage System** - The metrics storage system (Amazon's S3) is used to store performance metrics.

For a detailed description of the system and its evaluation, please refer to the [report](https://github.com/MiguelPires/CloudPrime/blob/master/report.pdf).

## Further instructions ##

The project's subdirectories contain instructions on how to run each module.

## Deployment ##

If you want to deploy the modules to AWS, copy the project to an
instance and use the rc.local files that are included in the LoadBalancer and
webServer directories (the scripts' path should be /etc/rc.local). Note that 
the rc.local scripts assume that maven is installed in /usr/local/apache-maven. 

The rc.local scripts also assume that the project is already compiled. The 
compilation and instrumentation commands are commented out in the scripts. 
Upon boot of the instance, the script will run the module.

Note that the scripts will redirect the output to a /tmp/rc.local.log file, so if
you want to see what's going on just run:

<pre><code>$ tail -f /tmp/rc.local.log</code></pre>

If you have any questions or remarks, please send them to miguel(dot)pires(at)ist.utl.pt

