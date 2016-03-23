package cnv.cloudprime.loadbalancer;

import java.util.ArrayList;

import com.amazonaws.services.autoscaling.model.Instance;

public class InstanceManager {
	
	ArrayList<Instance> servers = new ArrayList<Instance>();
	int index = 0;
	
	public InstanceManager() {
		
	}

	public Instance getNextServer() {
		index = (++index) % servers.size(); 
		return servers.get(index);
	}

	public void asdasd()
	{
		
	}
	
}
