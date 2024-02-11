
import com.amazonaws.services.cognitoidentity.model.Credentials;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AWSHandler {
    S3Client s3Client;
    Ec2Client ec2;
    SqsClient sqsClient;
    public AWSHandler(){
        //AwsBasicCredentials awsCredentials = AwsBasicCredentials.create("ASIAU6VWMFG2IVMCEMWN", "Z4ixTAWahoik6Qr0vFqrJQQwxCnFsJ4wIze/Q//5");
        s3Client = S3Client.builder().region(Region.US_EAST_1).build();
        ec2 = Ec2Client.builder().region(Region.US_EAST_1)/*.credentialsProvider(StaticCredentialsProvider.create(awsCredentials))*/.build();
        sqsClient = SqsClient.builder().region(Region.US_EAST_1)./*credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ASIAU6VWMFG2LPSBSXVH","txdGAu4i6VClAHfekt+FnvvJLS6pAbSz5nEYlOaq")))*/build();
    }
    public List<String> createEC2Instance(String userData,String name, String amiId,int numOfInstances) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)

                /*.iamInstanceProfile(IamInstanceProfileSpecification.builder().arn("arn:aws:iam::340758636980:instance-profile/LabInstanceProfile").build())*/
                .instanceType(InstanceType.T2_MEDIUM)
                .maxCount(numOfInstances)
                .minCount(numOfInstances)
                .userData(Base64.getEncoder().encodeToString((userData).getBytes()))
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().arn("arn:aws:iam::169611487111:instance-profile/LabInstanceProfile").build())
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        int i =0;
        for (Instance instance: response.instances()){
            Tag tag = Tag.builder()
                    .key("Name")
                    .value(name + i)
                    .build();

            CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                    .resources(instance.instanceId())
                    .tags(tag)
                    .build();
            try {
                ec2.createTags(tagRequest);
                System.out.printf("Successfully started EC2 Instance %s based on AMI %s", instance.instanceId(), amiId);
            } catch (Ec2Exception e) {
                System.err.println(e.awsErrorDetails().errorMessage());
                System.exit(1);
            }
            i++;

        }
        return response.instances().stream().map(Instance::instanceId).collect(Collectors.toList());
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
    public String getObjectFromBucket(String bucketName, String keyName, String path) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        String fileName = new File(keyName).getName();
        BufferedOutputStream outputStream = null;
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(fileName));

        byte[] buffer = new byte[4096];
        int bytesRead = -1;

        while ((bytesRead = response.read(buffer)) !=  -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        response.close();
        outputStream.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return fileName;
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
    public String getFileUrl(String bucketname,String key){
        String fileUrl = s3Client.utilities().getUrl(GetUrlRequest.builder()
                .bucket(bucketname)
                .key(key)
                .build()).toExternalForm();

        return fileUrl;
    }

//    public String createSqs(String name){
//        sqsClient.createQueue(CreateQueueRequest.builder().queueName(name).build());
//        try {
//            Thread.sleep(1000);
//        }
//        catch (Exception e){
//            System.out.println("ERROR: "+e.getMessage());
//        }
//        String url = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
//        return url;
//    }

    public String createSqs(String name){
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        if(name.endsWith(".fifo")){
            attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
            attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false");
        }
        sqsClient.createQueue(CreateQueueRequest.builder().queueName(name).attributes(attributes).build());
        try {
            Thread.sleep(1000);
        }
        catch (Exception e){
            System.out.println("ERROR: "+e.getMessage());
        }
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }
    public String getSqsUrl(String name){
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }
    public void sendMessage(String m,String url,String messageGroupId,String messageDeduplicationId){
        SendMessageRequest sendMessageRequest;
        if(messageGroupId == null && messageDeduplicationId == null) {
            sendMessageRequest = SendMessageRequest.builder().messageBody(m).queueUrl(url).build();
        }
        else{
            sendMessageRequest = SendMessageRequest.builder().messageBody(m).queueUrl(url)
                    .messageGroupId(messageGroupId).messageDeduplicationId(messageDeduplicationId).build();
        }
        sqsClient.sendMessage(sendMessageRequest);
    }
    public List<Message> readMessage(String url,int visibilityTimeOut){
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder().queueUrl(url).visibilityTimeout(visibilityTimeOut).build();
        ReceiveMessageResponse response =  sqsClient.receiveMessage(receiveMessageRequest);
        return response.messages();
    }

    public void deleteMessage(String url,String receipt){
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder().queueUrl(url).receiptHandle(receipt).build();
        sqsClient.deleteMessage(deleteMessageRequest);
    }

    public boolean isInstanceWithTagExists(String tagName) {
        // Specify the filter to search for instances with the specified tag
        Filter tagFilter = Filter.builder()
                .name("tag:Name")
                .values(tagName)
                .build();

        Filter stateFilter = Filter.builder()
                .name("instance-state-name")
                .values("running", "pending")
                .build();

        // Create a describe instances request with the tag filter
        DescribeInstancesRequest describeInstancesRequest = DescribeInstancesRequest.builder()
                .filters(tagFilter,stateFilter)
                .build();

        // Send the describe instances request and get the response
        DescribeInstancesResponse describeInstancesResponse = ec2.describeInstances(describeInstancesRequest);

        // Check if any instances are found
        for (Reservation reservation : describeInstancesResponse.reservations()) {
            for (Instance instance : reservation.instances()) {
                for (Tag tag : instance.tags()) {
                    if (tagName.equals(tag.value())) {
                        return true; // Found an instance with the specified tag
                    }
                }
            }
        }

        return false; // No instance with the specified tag found
    }

    public void terminateInstances(List<String> idList){
        TerminateInstancesRequest terminateInstancesRequest = TerminateInstancesRequest.builder().instanceIds(idList).build();
        ec2.terminateInstances(terminateInstancesRequest);
    }

    public void terminateManager(String id){
        try {
        // Terminate the instance
        TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                .instanceIds(id)
                .build();

        TerminateInstancesResponse response  = ec2.terminateInstances(request);

        System.out.println("Termination successful: " + response);
        } catch (Exception e) {
            System.err.println("Error terminating instance: " + e.getMessage());
        } finally {
            // Close the EC2 client
            ec2.close();
            s3Client.close();
            sqsClient.close();
        }
    }

}
