import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AWSHandler {
    S3Client s3Client;
    Ec2Client ec2;
    SqsClient sqsClient;
    public AWSHandler(){
        s3Client = S3Client.builder().region(Region.US_WEST_2).build();
        ec2 = Ec2Client.builder().region(Region.US_EAST_1).build();
        sqsClient = SqsClient.builder().region(Region.US_EAST_1).build();
    }
    public String createEC2Instance(String name, String amiId,int numOfInstances) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T1_MICRO)
                .maxCount(numOfInstances)
                .minCount(numOfInstances)
                .keyName("vockey")
                /*.userData(Base64.getEncoder().encodeToString(("wget https://s3.console.aws.amazon.com/s3/object/helloworld.idonagler?region=us-east-1&bucketType=general&prefix=HelloWorldTest.jar" +
                        "java -jar HelloWorldTest.jar").getBytes()))*/
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();
        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf("Successfully started EC2 Instance %s based on AMI %s", instanceId, amiId);
            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }
    public void uploadToBucket(String bucketname,String key ,String path){
        Path filePath = Paths.get(path);
        try {
            byte[] filecontent = Files.readAllBytes(filePath);
            s3Client.putObject(PutObjectRequest
                            .builder().bucket(bucketname).
                            key(key).build(),
                    RequestBody.fromBytes(filecontent));
        }
        catch (Exception e){
            System.out.println("ERROR "+e.getMessage());
        }
    }
    public void getObjectBytes(String bucketName, String keyName, String path) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest
                    .builder()
                    .key(keyName)
                    .bucket(bucketName)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(objectRequest);
            byte[] data = objectBytes.asByteArray();

            // Write the data to a local file.
            File myFile = new File(path);
            OutputStream os = new FileOutputStream(myFile);
            os.write(data);
            System.out.println("Successfully obtained bytes from an S3 object");
            os.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
    public void createBucket(String bucketname){
        s3Client.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucketname)
                .createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                .build())
                .build());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("successfully created a bucket: "+bucketname);
    }

    public void  createSqs(){
        sqsClient.createQueue(CreateQueueRequest.builder().queueName("input").build());

    }
    public void sendMessage(String m){
        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl("https://sqs.us-east-1.amazonaws.com/340758636980/input\n").messageBody(m).build());
    }


}
