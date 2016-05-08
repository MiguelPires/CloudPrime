package cnv.cloudprime.loadbalancer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class MetricsUpdater
        implements Runnable {
    private static final String BUCKET_NAME = "cloudprime";
    private static final String OBJECT_NAME = "metrics";

    private InstanceManager manager;
    private int UPDATE_PERIOD = 2000;
    private AmazonS3Client s3Client;

    public MetricsUpdater(InstanceManager manager) {
        this.manager = manager;
        AWSCredentials cred =
            new ProfileCredentialsProvider("credentials", "default").getCredentials();
        s3Client = new AmazonS3Client(cred);
    }

    @Override
    public void run() {
        new Thread(() -> {
            trainCostModel();
        }).start();
        new Thread(() -> {
            trainTimeModel();
        }).start();

        while (true) {
            ListObjectsRequest listObjectsRequest =
                new ListObjectsRequest().withBucketName(BUCKET_NAME).withPrefix(OBJECT_NAME);
            ObjectListing objectListing;

            do {
                objectListing = s3Client.listObjects(listObjectsRequest);
                listObjectsRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());

            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {

                try {
                    S3Object object = s3Client
                            .getObject(new GetObjectRequest(BUCKET_NAME, objectSummary.getKey()));
                    InputStream objectData = object.getObjectContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(objectData));

                    String metricLine;
                    BigInteger metricBigInt = new BigInteger("0");
                    BigInteger inputBigInt = new BigInteger("0");
                    boolean invalidFile = false;

                    for (int iter = 0; (metricLine = reader.readLine()) != null
                            && iter < 4; ++iter) {
                        if ((iter == 0 && !metricLine.contains("Input:"))
                                || (iter == 1 && !metricLine.contains("Max depth:"))
                                || (iter == 2 && !metricLine.contains("Bytes executed:"))
                                || (iter == 3 && !metricLine.contains("Function calls:"))) {
                            // validate metrics file
                            invalidFile = true;
                            break;
                        }

                        if (iter == 0) {
                            String inputNumber = metricLine.substring(metricLine.indexOf(":") + 1);
                            inputBigInt = new BigInteger(inputNumber);
                        } else {
                            String metricNumber = metricLine.substring(metricLine.indexOf(":") + 1);
                            metricBigInt = metricBigInt.add(new BigInteger(metricNumber));
                        }
                    }

                    // ignore invalid file
                    if (invalidFile) {
                        System.out.println("Deleted invalid file");
                        continue;
                    }

                    // TODO: change this because this is stupid. An average will
                    // be heavily influenced by the largest value
                    // NOTE: weighted average??? 
                    // compute the average of the values
                    metricBigInt =
                        BigIntRegression.divideWithRound(metricBigInt, new BigInteger("3"));
                    manager.addCostDatapoint(inputBigInt, metricBigInt);

                    objectData.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                } finally {
                    s3Client.deleteObject(
                            new DeleteObjectRequest(BUCKET_NAME, objectSummary.getKey()));
                }
            }

            try {
                Thread.sleep(UPDATE_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void trainCostModel() {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(BUCKET_NAME + "-training").withPrefix(OBJECT_NAME);
        ObjectListing objectListing;

        do {
            objectListing = s3Client.listObjects(listObjectsRequest);
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());

        System.out.println("Training cost model with " + objectListing.getObjectSummaries().size()
                + " data points");

        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            InputStream objectData = null;
            try {
                S3Object object = s3Client.getObject(
                        new GetObjectRequest(BUCKET_NAME + "-training", objectSummary.getKey()));
                objectData = object.getObjectContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(objectData));

                String metricLine;
                BigInteger metricBigInt = new BigInteger("0");
                BigInteger inputBigInt = new BigInteger("0");
                boolean invalidFile = false;

                for (int iter = 0; (metricLine = reader.readLine()) != null && iter < 4; ++iter) {
                    if ((iter == 0 && !metricLine.contains("Input:"))
                            || (iter == 1 && !metricLine.contains("Max depth:"))
                            || (iter == 2 && !metricLine.contains("Bytes executed:"))
                            || (iter == 3 && !metricLine.contains("Function calls:"))) {
                        // validate metrics file
                        invalidFile = true;
                        break;
                    }

                    if (iter == 0) {
                        String inputNumber = metricLine.substring(metricLine.indexOf(":") + 1);
                        inputBigInt = new BigInteger(inputNumber);
                    } else {
                        String metricNumber = metricLine.substring(metricLine.indexOf(":") + 1);
                        metricBigInt = metricBigInt.add(new BigInteger(metricNumber));
                    }
                }

                // ignore invalid file
                if (invalidFile) {
                    System.out.println("Deleted invalid file");
                    continue;
                }

                metricBigInt = BigIntRegression.divideWithRound(metricBigInt, new BigInteger("3"));
                manager.addCostDatapoint(inputBigInt, metricBigInt);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            } finally {
                if (objectData != null) {
                    try {
                        objectData.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Finished training cost model");
    }

    private void trainTimeModel() {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(BUCKET_NAME + "-timing").withPrefix(OBJECT_NAME);
        ObjectListing objectListing;

        do {
            objectListing = s3Client.listObjects(listObjectsRequest);
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());

        System.out.println("Training timing model with " + objectListing.getObjectSummaries().size()
                + " data points");

        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            InputStream objectData = null;
            try {
                S3Object object = s3Client.getObject(
                        new GetObjectRequest(BUCKET_NAME + "-timing", objectSummary.getKey()));
                objectData = object.getObjectContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(objectData));

                String metricLine;
                BigInteger timeBigInt = new BigInteger("0");
                BigInteger inputBigInt = new BigInteger("0");
                boolean invalidFile = false;

                for (int iter = 0; (metricLine = reader.readLine()) != null && iter < 2; ++iter) {
                    if ((iter == 0 && !metricLine.contains("Input:"))
                            || (iter == 1 && !metricLine.contains("Time:"))) {
                        // validate metrics file
                        invalidFile = true;
                        break;
                    }

                    if (iter == 0) {
                        String inputNumber = metricLine.substring(metricLine.indexOf(":") + 1);
                        inputBigInt = new BigInteger(inputNumber);
                    } else {
                        String timeNumber = metricLine.substring(metricLine.indexOf(":") + 1);
                        timeBigInt = new BigInteger(timeNumber);
                    }
                }

                // ignore invalid file
                if (invalidFile) {
                    System.out.println("Deleted invalid file");
                    continue;
                }

                manager.addTimeDatapoint(inputBigInt, timeBigInt);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            } finally {
                if (objectData != null) {
                    try {
                        objectData.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Finished training timing model");
    }
}
