package cnv.cloudprime.loadbalancer;

public class RequestResult {
    private WebServer server;
    private long requestId;

    public RequestResult(WebServer server, long requestIndex) {
        this.server = server;
        this.requestId = requestIndex;
    }

    public RequestResult() {
        this.server = null;
        this.requestId = -1;
    }

    public WebServer getServer() {
        return server;
    }

    public long getRequestIndex() {
        return requestId;
    }

    public boolean isResponseValid() {
        return server != null && requestId != -1;
    }
}

