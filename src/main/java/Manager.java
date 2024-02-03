
//import com.amazonaws.services.s3.AmazonS3Client;

//import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.utils.IoUtils;


import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Manager {
//    public static void main(String[] args) {
//        AWSHandler awsHandler = new AWSHandler();
//        String bucketName = "idobaucket2";
//        String key = "input1.txt";
//        awsHandler.getObjectBytes(bucketName,key,"inputlocal.txt");
//        //awsHandler.createEC2Instance("ec2-user","ami-0c5cb50ab85992664");
//        //awsHandler.uploadToBucket("idobucket223","firstfile","C:\\study\\fifth_semester\\distributed_system_programing\\assignment1\\files\\input1.txt");
////
//    }
public static void main(String[] args) {
    AWSHandler awsHandler = new AWSHandler();
    String amid = "ami-06f533aa95c8c0dad";

//    String sqsUrl = awsHandler.createSqs("reviews");
//    String sqsReturnUrl = awsHandler.createSqs("outputs");
    String inputsqsUrl = awsHandler.getSqsUrl("inputs");
    String outputsqsUrl = awsHandler.getSqsUrl("outputs");
    String filePath = args[0];
    int count = 0;
    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
        ObjectMapper objectMapper = new ObjectMapper();
        StringBuilder jsonStringBuilder = new StringBuilder();
//
        int currentChar;
        while ((currentChar = br.read()) != -1) {
            char character = (char) currentChar;

            // Build the JSON string until you find a valid JSON object
            jsonStringBuilder.append(character);

            try {
                // Attempt to parse the JSON string
                JsonNode jsonNode = objectMapper.readTree(jsonStringBuilder.toString());

                // If successful, process the JSON object
                for(JsonNode review : jsonNode.findValue("reviews")){
                    awsHandler.sendMessage(review.toString(),inputsqsUrl);
                    count++;
                }

                // Clear the StringBuilder for the next JSON object
                jsonStringBuilder.setLength(0);
            } catch (Exception ignored) {
                // Not a complete JSON object yet, continue reading
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    System.out.println("finish uploading jsons to sqs");
    awsHandler.createEC2Instance("wget https://workerbucketido.s3.amazonaws.com/worker.jar\n java -jar worker.jar","w",amid,3);
    ///TODO wait for workers to do their job
    while (count > 0) {
        try {
            List<Message> messages = awsHandler.readMessage(outputsqsUrl);
            System.out.println("this is the message received: " + messages.get(0).body());
            awsHandler.deleteMessage(outputsqsUrl,messages.get(0).receiptHandle());
            count--;
        }
        catch (Exception e){

        }
    }
}



}


