import com.fasterxml.jackson.core.JsonProcessingException;
import software.amazon.awssdk.services.sqs.model.*;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class Manager {

public static void main(String[] args) {
    AWSHandler awsHandler = new AWSHandler();
    String managerID = null;
    String amid = "ami-029abde7a909e7f6e";
    List<String> workersId = new ArrayList<>();
    String filessqsUrl = awsHandler.getSqsUrl("files.fifo");
    String inputsqsUrl = awsHandler.getSqsUrl("inputs");
    String outputsqsUrl = awsHandler.getSqsUrl("outputs");
    String answersUrlSqs = awsHandler.getSqsUrl("answers");

    String fullMessage = "";

    while (true) {
        List<Message> files = awsHandler.readMessage(filessqsUrl,10);
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
                workersId.clear();
                System.out.println(managerID);
                awsHandler.terminateManager(managerID);
            }
            break;
        }
        else if(fullMessage.startsWith("managerID:")){
            managerID = Local.extractFilenameAfterColon(fullMessage);
            System.out.println("-------------------------------------------------------------------------");
            System.out.println(managerID);
            System.out.println("-------------------------------------------------------------------------");
            continue;
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
        int reviewsPerWorker = jsonLocalMessage.findValue("n").asInt();
        String filePath = awsHandler.getObjectFromBucket("worker-bucket-dsp",key,fileUrl);
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
                        awsHandler.sendMessage(review.toString(), inputsqsUrl,null,null);
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
        int numOfInstancesRequested = count % reviewsPerWorker == 0 ? count/reviewsPerWorker : count/reviewsPerWorker + 1;
        int numOfInstances = Math.min(8,numOfInstancesRequested);
        ///TODO check if exists.
        if(!awsHandler.isInstanceWithTagExists("worker0")) {
            workersId = awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://worker-bucket-dsp.s3.amazonaws.com/worker.jar\n java -Xmx2g -jar worker.jar", "worker", amid, numOfInstances);
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
        awsHandler.uploadToBucket("worker-bucket-dsp", localId+":"+outputFile, outputFile);
        String summaryFilePath = awsHandler.getFileUrl("worker-bucket-dsp",localId+":"+outputFile);
        String json = String.format("{\"localId\":\"%s\" ,\"output file\":\"%s\" ,\"path\":\"%s\"}",localId,localId+":"+outputFile,summaryFilePath);
        awsHandler.sendMessage(json,answersUrlSqs,null,null);
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


