package cnv.cloudprime.loadbalancer;

public class RequestResult {
    private WebServer server;
    private int requestIndex;

    public RequestResult(WebServer server, int requestIndex) {
        this.server = server;
        this.requestIndex = requestIndex;
    }

    public RequestResult() {
        this.server = null;
        this.requestIndex = -1;
    }

    public WebServer getServer() {
        return server;
    }

    public int getRequestIndex() {
        return requestIndex;
    }

    public boolean isResponseValid() {
        return server != null && requestIndex != -1;
    }
}

