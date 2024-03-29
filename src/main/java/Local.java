import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.io.*;


public class Local {
    private static final String LOCK_FILE_PATH = "initManager.lock";

    public static void main(String[] args) {
        String amid = "ami-029abde7a909e7f6e";
        String messageGroupId = "files";
        AWSHandler awsHandler = new AWSHandler();
        awsHandler.createSqs("files.fifo");
        String filesUrlSqs = awsHandler.getSqsUrl("files.fifo");

        if (acquireLock()) {
            try {
                initManager(awsHandler, amid, filesUrlSqs, messageGroupId);
            } finally {
                // Release lock
                releaseLock();
            }
        }
        else {
            System.out.println("Another instance is already running.");
        }

        awsHandler.createSqs("inputs");
        awsHandler.createSqs("outputs");
        awsHandler.createSqs("answers");
        String answersUrlSqs = awsHandler.getSqsUrl("answers");
        //generate unique id
        UUID uniqueID = UUID.randomUUID();
        int numOFfiles;
        int reviewsPerWorker;
        if (args.length %2 == 0){
            numOFfiles = (args.length-2)/2;
            reviewsPerWorker =  Integer.parseInt(args[args.length -2]);
        }
        else{
            numOFfiles = (args.length-1)/2;
            reviewsPerWorker =  Integer.parseInt(args[args.length -1]);
        }
        for(int i = 0; i < numOFfiles; i++){
            String json = String.format("{\"localId\":\"%s\" ,\"input file\":\"%s\" ,\"output file\":\"%s\" ,\"n\":%d}",uniqueID,args[i],args[numOFfiles+i],reviewsPerWorker);
            awsHandler.sendMessage(json,filesUrlSqs,messageGroupId,""+uniqueID+i);
        }
        if(args[args.length-1].equals("t")){
            //terminate = true;
            awsHandler.sendMessage("t",filesUrlSqs,messageGroupId,"t");
        }
        waitForAnswer(numOFfiles,awsHandler,answersUrlSqs,uniqueID.toString());
    }
    public static void initManager(AWSHandler awsHandler,String amid,String filesUrlSqs,String messageGroupId){
        if(!awsHandler.isInstanceWithTagExists("manager0")) {
            List<String> ids = awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://worker-bucket-dsp.s3.amazonaws.com/manager.jar\n" +
                    "while true; do java -Xmx4g -jar manager.jar; done\n", "manager", amid, 1);
            awsHandler.sendMessage("managerID:" + ids.get(0),filesUrlSqs,messageGroupId,"managerID");
        }
    }
    private static String generateHtml(List<JsonNode> outputs) {
        String[] colors ={"black","darkred","red","black","lightgreen","darkgreen"};
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>\n<head>\n<style>\n");
        htmlBuilder.append("body { font-family: Arial, sans-serif; }\n");
        htmlBuilder.append(".line { padding: 10px; margin: 5px; }\n");
        htmlBuilder.append("</style>\n</head>\n<body>\n");

        for (JsonNode json : outputs) {
            // Parse JSON and extract values
            int rank = json.findValue("rank").asInt();

            // Determine line color based on rank
            String lineColor = colors[rank];

            // Create HTML line
            htmlBuilder.append(String.format("<div class='line' style='color: %s;'>id: %s, link: <a href=%s target='_blank'>%s</a>, rank: %d, entities: %s, <b>isSarcasm: %s</b></div>\n",
                    lineColor, json.findValue("id"), json.findValue("link"), json.findValue("link"), rank, json.findValue("entities"), json.findValue("isSarcasm")));
        }

        htmlBuilder.append("</body>\n</html>");
        return htmlBuilder.toString();
    }
    private static void waitForAnswer(int numOfFiles,AWSHandler awsHandler,String answersUrlSqs,String localId){
        System.out.println("\nwaiting for answer");
        ObjectMapper objectMapper = new ObjectMapper();
        while (numOfFiles>0){
            try {
                Message message = awsHandler.readMessage(answersUrlSqs,0).get(0);
                JsonNode jsonReceived  =  objectMapper.readTree(message.body());
                if(jsonReceived.findValue("localId").asText().equals(localId)){
                    String outputFile = jsonReceived.findValue("output file").asText();
                    awsHandler.deleteMessage(answersUrlSqs,message.receiptHandle());
                    String summaryFilePath =  awsHandler.getObjectFromBucket("worker-bucket-dsp",jsonReceived.findValue("output file").asText());
                    List<JsonNode> results = parseToJSonList(summaryFilePath);
                    String htmlContent = generateHtml(results);
                    writeToFile(extractFilenameAfterColon(outputFile+".html"),htmlContent);
                    numOfFiles--;
                }
            }
            catch (Exception e){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
    public static List<JsonNode> parseToJSonList(String filePath){
        List<JsonNode> results = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            StringBuilder jsonStringBuilder = new StringBuilder();
            //int currentChar;
            String line;
            //while ((currentChar = br.read()) != -1) {
            while ((line = br.readLine()) != null) {
                //char character = (char) currentChar;
                jsonStringBuilder.append(line.trim());

                // Build the JSON string until you find a valid JSON object
                //jsonStringBuilder.append(character);

                try {
                    // Attempt to parse the JSON string
                    JsonNode jsonNode = objectMapper.readTree(jsonStringBuilder.toString());
                    // If successful, process the JSON object
                    results.add(jsonNode);
                    // Clear the StringBuilder for the next JSON object
                    jsonStringBuilder.setLength(0);
                } catch (Exception ignored) {
                    // Not a complete JSON object yet, continue reading
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
    private static void writeToFile(String fileName, String content) {
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(content);
            System.out.println("HTML file created: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String extractFilenameAfterColon(String filename) {
        int colonIndex = filename.indexOf(':'); // Find the index of the colon
        if (colonIndex != -1 && colonIndex < filename.length() - 1) {
            // If colon exists and there's content after it
            return filename.substring(colonIndex + 1); // Extract the substring after colon
        } else {
            // If colon doesn't exist or it's at the end of the string
            return ""; // Return an empty string or handle error accordingly
        }
    }

    private static boolean acquireLock() {
        try {
            File lockFile = new File(LOCK_FILE_PATH);
            return lockFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void releaseLock() {
        File lockFile = new File(LOCK_FILE_PATH);
        lockFile.delete();
    }
}
