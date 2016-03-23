package cnv.cloudprime.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;

public class AutoScaler {

	static AmazonEC2 ec2;
	
	public AutoScaler() {

	}

	private static void createInstance() throws AmazonServiceException, AmazonClientException, InterruptedException {
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
				.withInstanceType("m1.small")
				.withImageId("my_ami")
				.withMinCount(1)
				.withMaxCount(1)
				.withKeyName("my_key")
				;
		RunInstancesResult runInstances = ec2.runInstances(runInstancesRequest);
	}

}
