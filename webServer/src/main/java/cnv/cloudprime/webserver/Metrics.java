package cnv.cloudprime.webserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class Metrics {
    private static final String BUCKET_NAME = "cloudprime";
    private static final String OBJECT_NAME = "metrics";

    private AmazonS3Client s3Client;
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
    public synchronized void decrStackDepth(String __) throws IOException {
        currentStackDepth--;

        if (currentStackDepth == 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        flushMetrics();
                        clearMetrics();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
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
     *  Logs every metric to disk and stored it in S3
     *  Returns the time at which the file was written
     */
    public synchronized void flushMetrics() throws IOException {
        System.out.println("Flushing metrics");
        File metricsFile = new File(IntFactorization.LOG_FILENAME);
        PrintWriter writer = new PrintWriter(new FileOutputStream(metricsFile, true));
        long threadId = Thread.currentThread().getId();

        if (s3Client == null) {
            AWSCredentials cred =
                new ProfileCredentialsProvider("credentials", "default").getCredentials();
            s3Client = new AmazonS3Client(cred);
        }

        // write metrics
        writer.write("Thread: " + threadId + " - Max depth: " + maxStackDepth + "\n");
        writer.write("Thread: " + threadId + " - Bytes executed: " + bytesExecuted + "\n");
        writer.write("Thread: " + threadId + " - Function calls: " + calls + "\n");
        writer.close();

        s3Client.putObject(new PutObjectRequest(BUCKET_NAME, OBJECT_NAME, metricsFile));
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
