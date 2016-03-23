package cnv.cloudprime.loadbalancer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;

import com.amazonaws.services.ec2.AmazonEC2;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import cnv.cloudprime.loadbalancer.*;

@SuppressWarnings("restriction")
public class RequestHandler implements HttpHandler {

	InstanceManager instanceManager = new InstanceManager();

	public RequestHandler(){

	}

	public void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().toString();
		if (/*exchange.getRequestMethod().equals("POST") && */path.contains("/factor/")) {
			String inputNumber = exchange.getRequestURI().toString().replace("/factor/", "");
			
			// parse
			BigInteger bigInt;
			try {
				bigInt = new BigInteger(inputNumber);
			} catch (NumberFormatException e) {
				String response = "This '" + inputNumber + "' is not a number";
				System.out.println(response);
				exchange.sendResponseHeaders(400, 0);
				OutputStream outStream = exchange.getResponseBody();
				outStream.write(response.getBytes());
				outStream.close();
				return;
			}

			instanceManager.getNextServer();
		}
	}

}
