
//import com.amazonaws.services.s3.AmazonS3Client;

//import software.amazon.awssdk.services.sqs.SqsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Manager {

public static void main(String[] args) {
    AWSHandler awsHandler = new AWSHandler();
    String amid = "ami-0628812371fda9787";
    List<String> workersId = new ArrayList<>();
//    String sqsUrl = awsHandler.createSqs("reviews");
//    String sqsReturnUrl = awsHandler.createSqs("outputs");
    String filessqsUrl = awsHandler.getSqsUrl("files");
    String inputsqsUrl = awsHandler.getSqsUrl("inputs");
    String outputsqsUrl = awsHandler.getSqsUrl("outputs");
    String answersUrlSqs = awsHandler.getSqsUrl("answers");

    String fullMessage = "";

    while (true) {
        List<Message> files = awsHandler.readMessage(filessqsUrl,0);
        if(files.isEmpty()){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            continue;
        }
        Message fileM = files.get(0);
        System.out.println(fileM.body());
        fullMessage = fileM.body();
        awsHandler.deleteMessage(filessqsUrl,fileM.receiptHandle());
        if(fullMessage.equals("t")){
            if(!workersId.isEmpty()) {
                awsHandler.terminateInstances(workersId);
            }
            break;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonLocalMessage = null;
        try {
            jsonLocalMessage = objectMapper.readTree(fullMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String key = extractKey(jsonLocalMessage.findValue("input file").asText());
        String localId = jsonLocalMessage.findValue("localId").asText();
        String fileUrl = extractUrl(jsonLocalMessage.findValue("input file").asText());
        String outputFile = jsonLocalMessage.findValue("output file").asText();
        String filePath = awsHandler.getObjectFromBucket("workerbucketido",key,fileUrl);
        int count = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
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
                    for (JsonNode review : jsonNode.findValue("reviews")) {
                        awsHandler.sendMessage(review.toString(), inputsqsUrl);
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
        int numOfInstances = Math.min(8,count/30);
        ///TODO check if exists.
        if(!awsHandler.isInstanceWithTagExists("worker0")) {
            workersId = awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://workerbucketido.s3.amazonaws.com/worker.jar\n java -Xmx2g -jar worker.jar", "worker", amid, numOfInstances);
        }
        List<JsonNode> outputs = new ArrayList<>();
        while (count > 0) {
            try {
                List<Message> messages = awsHandler.readMessage(outputsqsUrl,1);
                System.out.println(count);
                outputs.add(objectMapper.readTree(messages.get(0).body()));
                awsHandler.deleteMessage(outputsqsUrl, messages.get(0).receiptHandle());
                count--;
            } catch (Exception e) {
                System.out.println("ERROR: "+e.getMessage());
            }
        }
        String summary = sumToFile(outputs);
        writeToFile(outputFile, summary);
        awsHandler.uploadToBucket("workerbucketido", localId+":"+outputFile, outputFile);
        String summaryFilePath = awsHandler.getFileUrl("workerbucketido",localId+":"+outputFile);
        String json = String.format("{\"localId\":\"%s\" ,\"output file\":\"%s\" ,\"path\":\"%s\"}",localId,localId+":"+outputFile,summaryFilePath);
        awsHandler.sendMessage(json,answersUrlSqs);
    }
}

    private static String sumToFile(List<JsonNode> outputs) {
        StringBuilder stringBuilder = new StringBuilder();
        for (JsonNode node : outputs) {
            stringBuilder.append(node.toString()).append("\n");
        }
        // Remove the last newline character if there are elements in the list
        if (!outputs.isEmpty()) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return stringBuilder.toString();
    }

    private static String extractUrl(String fullMessage) {
        for(int i =0; i < fullMessage.length();i++){
            if (fullMessage.charAt(i) == ':'){
                return fullMessage.substring(i+1);
            }
        }
        return "";
    }

    private static String extractId(String fullMessage) {
        for(int i =0; i < fullMessage.length();i++){
            if (fullMessage.charAt(i) == ':'){
                return fullMessage.substring(0,i);
            }
        }
        return "";
    }


private static void writeToFile(String fileName, String content) {
    try (FileWriter writer = new FileWriter(fileName)) {
        writer.write(content);
        System.out.println("HTML file created: " + fileName);
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private static String extractKey(String url){
    for(int i = url.length()-1;i>=0;i--){
        if(url.charAt(i) == '/'){
            return url.substring(i+1);
        }
    }
    return "";
}
}


