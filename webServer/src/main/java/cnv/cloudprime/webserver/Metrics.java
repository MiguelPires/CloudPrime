package cnv.cloudprime.webserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

public class Metrics {
    private int maxStackDepth = 0;
    private int currentStackDepth = 0;
    private int bytesExecuted = 0;
    private int calls = 0;

    public synchronized void incrStackDepth(String __) {
        currentStackDepth++;

        if (currentStackDepth > maxStackDepth) {
            maxStackDepth = currentStackDepth;
        }
    }

    /*
     *  Stack Depth Metric - Decrements the stack depth
     */
    public synchronized void decrStackDepth(String __) throws FileNotFoundException {
        currentStackDepth--;

        if (currentStackDepth == 0) {
            flushMetrics();
            clearMetrics();
        }
    }

    /*
     * Byte Code Metric - Increments the executed byte code
     */
    public synchronized void incrByteCode(String __) {
        bytesExecuted++;
    }


    /*
     *  Method Calls Metric - Increments the number of calls
     */
    public synchronized void incrCall(String __) {
        calls++;
    }

    /*
     *  Logs every metric to disk after the execution ends 
     */
    public synchronized void flushMetrics() throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(new FileOutputStream(new File("metrics.log"), true));
        long threadId = Thread.currentThread().getId();

        // write metrics
        long millis = System.currentTimeMillis();

        writer.write("Thread: " + threadId + " - Time: " + millis + "\n");
        writer.write("Thread: " + threadId + " - Max depth: " + maxStackDepth + "\n");
        writer.write("Thread: " + threadId + " - Bytes executed: " + bytesExecuted + "\n");
        writer.write("Thread: " + threadId + " - Function calls: " + calls + "\n");
        writer.close();
    }

    /*
     *  Clears the metrics' value  
     */
    private synchronized void clearMetrics() {
        currentStackDepth = 0;
        maxStackDepth = 0;
        bytesExecuted = 0;
        calls = 0;
    }
}
