package cnv.cloudprime.loadbalancer;

public class NoAvailableServerException extends
        Exception {
    public NoAvailableServerException(String message) {
        super(message);
    }
}
