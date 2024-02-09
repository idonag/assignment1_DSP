
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
    String fullMessage = "";

    while (true) {
        List<Message> files = awsHandler.readMessage(filessqsUrl);
        if(files.isEmpty()){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            continue;
        }
        Message fileM = files.get(0);
        fullMessage = fileM.body();
        awsHandler.deleteMessage(filessqsUrl,fileM.receiptHandle());
        if(fullMessage.equals("t")){
            if(!workersId.isEmpty()) {
                awsHandler.terminateInstances(workersId);
            }
            break;
        }
        String key = extractKey(fullMessage);
        String localId = extractId(fullMessage);
        String fileUrl = extractUrl(fullMessage);
        String filePath = awsHandler.getObjectFromBucket("workerbucketido",key,fileUrl);
        int count = 0;
        ObjectMapper objectMapper = new ObjectMapper();
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
                List<Message> messages = awsHandler.readMessage(outputsqsUrl);
                System.out.println(count);
                outputs.add(objectMapper.readTree(messages.get(0).body()));
                awsHandler.deleteMessage(outputsqsUrl, messages.get(0).receiptHandle());
                count--;
            } catch (Exception e) {
                System.out.println("ERROR: "+e.getMessage());
            }
        }
        String html = generateHtml(outputs);
        writeToFile("outputs.html", html);
        awsHandler.uploadToBucket("workerbucketido", localId+":htmlfile.html", "outputs.html");
    }
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


