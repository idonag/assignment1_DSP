import com.fasterxml.jackson.core.JsonProcessingException;
import software.amazon.awssdk.services.sqs.model.*;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Manager {

    static List<String> workersId = new ArrayList<>();

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        System.out.println("threadPool created");
        AWSHandler awsHandler = new AWSHandler();
        String managerID = null;
        Object createWorkersKey = new Object();
        String filessqsUrl = awsHandler.getSqsUrl("files.fifo");
        String fullMessage;

        while (true) {
            List<Message> files = awsHandler.readMessage(filessqsUrl, 10);
            if (files.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                continue;
            }
            Message fileM = files.get(0);
            System.out.println(fileM.body());
            fullMessage = fileM.body();
            awsHandler.deleteMessage(filessqsUrl, fileM.receiptHandle());
            if (fullMessage.equals("t")) {
                executorService.shutdown();
                while(!executorService.isTerminated()){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!workersId.isEmpty()) {
                    awsHandler.terminateInstances(workersId);
                    workersId.clear();
                    System.out.println(managerID);
                    awsHandler.terminateManager(managerID);
                }
                break;
            } else if (fullMessage.startsWith("managerID:")) {
                managerID = Local.extractFilenameAfterColon(fullMessage);
                System.out.println("-------------------------------------------------------------------------");
                System.out.println(managerID);
                System.out.println("-------------------------------------------------------------------------");
                continue;
            }
            String finalFullMessage = fullMessage;
            System.out.println("sending file to thread");
            executorService.submit(() -> handleFile(awsHandler, finalFullMessage, createWorkersKey));
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

public static void handleFile(AWSHandler awsHandler,String fullMessage,Object createWorkersKey){
    System.out.println("entered handleFile function");
    String inputsqsUrl = awsHandler.getSqsUrl("inputs");
    String outputsqsUrl = awsHandler.getSqsUrl("outputs");
    String answersUrlSqs = awsHandler.getSqsUrl("answers");
    String amid = "ami-029abde7a909e7f6e";
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonLocalMessage;
    try {
        jsonLocalMessage = objectMapper.readTree(fullMessage);
    } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
    }
    String key = extractKey(jsonLocalMessage.findValue("input file").asText());
    String localId = jsonLocalMessage.findValue("localId").asText();
    //String fileUrl = extractUrl(jsonLocalMessage.findValue("input file").asText());
    String outputFile = jsonLocalMessage.findValue("output file").asText();
    int reviewsPerWorker = jsonLocalMessage.findValue("n").asInt();
    String filePath = awsHandler.getObjectFromBucket("worker-bucket-dsp",key);
    int count = 0;
    System.out.println("start process Json");
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
                    awsHandler.sendMessage(jsonForWorker(review.toString(),localId,outputFile), inputsqsUrl,null,null);
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
    synchronized(createWorkersKey){
        int existsInstances = awsHandler.runningInstances(workersId);
        System.out.println("exist: " + existsInstances);
        System.out.println("num: " + numOfInstances);
        if(existsInstances < numOfInstances) {
            System.out.println("creating workers");
            List<String> temp = awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://worker-bucket-dsp.s3.amazonaws.com/worker.jar\nwhile true; do java -Xmx4g -jar worker.jar; done\n", "worker", amid, numOfInstances - existsInstances);
            workersId.addAll(temp);
        }
    }
    List<JsonNode> outputs = new ArrayList<>();
    System.out.println("start process answers from workers");
    while (count > 0) {
        try {
            List<Message> messages = awsHandler.readMessage(outputsqsUrl,1);
            System.out.println(count);
            JsonNode messageToCheck = objectMapper.readTree(messages.get(0).body());
            if(messageToCheck.findValue("local id").toString().equals("\"" + localId + "\"") && messageToCheck.findValue("output file").toString().equals("\"" + outputFile + "\"")){
                JsonNode result = messageToCheck.findValue("result");
                outputs.add(result);
                awsHandler.deleteMessage(outputsqsUrl, messages.get(0).receiptHandle());
                count--;
            }
        } catch (Exception e) {
            System.out.println("ERROR: "+e.getMessage());
        }
    }
    System.out.println("finished to process answers");
    System.out.println("creating summery file");
    String summary = sumToFile(outputs);
    writeToFile(outputFile, summary);
    awsHandler.uploadToBucket("worker-bucket-dsp", localId+":"+outputFile, outputFile);
    System.out.println("summery file uploaded to bucket");
    String summaryFilePath = awsHandler.getFileUrl("worker-bucket-dsp",localId+":"+outputFile);
    String json = String.format("{\"localId\":\"%s\" ,\"output file\":\"%s\" ,\"path\":\"%s\"}",localId,localId+":"+outputFile,summaryFilePath);
    awsHandler.sendMessage(json,answersUrlSqs,null,null);
    System.out.println("update message sent to local");
}

    private static String jsonForWorker(String review, String localId, String outputFile) {
        return String.format("{\"local id\":\"%s\",\"output file\":\"%s\",\"review\": %s}",localId,outputFile,review);
    }
}


