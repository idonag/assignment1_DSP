
//import com.amazonaws.services.s3.AmazonS3Client;

//import software.amazon.awssdk.services.sqs.SqsClient;
//import software.amazon.awssdk.services.sqs.model.*;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.utils.IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    // Replace jsonString with your actual JSON data
    String jsonString = "Your JSON data here";

    Map<String, List<String>> reviewsMap = parseJsonFileToMap(Path.of("C:\\study\\fifth_semester\\distributed_system_programing\\assignment1\\src\\main\\input1.txt"));

    // Print the parsed data
    for (Map.Entry<String, List<String>> entry : reviewsMap.entrySet()) {
        System.out.println("Book Title: " + entry.getKey());
        System.out.println("Reviews:");
        for (String review : entry.getValue()) {
            System.out.println("  - " + review);
        }
        System.out.println();
    }
}
    public static Map<String, List<String>> parseJsonFileToMap(Path filePath) {
        Map<String, List<String>> reviewsMap = new HashMap<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(filePath.toFile());

            // Iterate over each JSON object in the input file
            for (JsonNode bookNode : rootNode) {
                String title = bookNode.get("title").asText();
                List<String> reviews = new ArrayList<>();

                // Iterate over reviews for each book
                for (JsonNode reviewNode : bookNode.get("reviews")) {
                    String reviewText = reviewNode.get("text").asText();
                    reviews.add(reviewText);
                }

                reviewsMap.put(title, reviews);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return reviewsMap;
    }


}


