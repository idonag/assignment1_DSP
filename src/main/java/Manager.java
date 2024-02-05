
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

public static void main(String[] args) {
    AWSHandler awsHandler = new AWSHandler();
    String amid = "ami-06f533aa95c8c0dad";

//    String sqsUrl = awsHandler.createSqs("reviews");
//    String sqsReturnUrl = awsHandler.createSqs("outputs");
    String filessqsUrl = awsHandler.getSqsUrl("files");
    String inputsqsUrl = awsHandler.getSqsUrl("inputs");
    String outputsqsUrl = awsHandler.getSqsUrl("outputs");
    String fileUrl = "";
    while (true) {
        fileUrl = awsHandler.readMessage(filessqsUrl).get(0).body();
        if(fileUrl.equals("t")){
            break;
        }
        String filePath = awsHandler.getObjectFromBucket("workerbucketido","input1.txt",fileUrl);
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
        awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://workerbucketido.s3.amazonaws.com/worker.jar\n java -Xmx2g -jar worker.jar", "worker", amid, 1);

        ///TODO wait for workers to do their job
        List<JsonNode> outputs = new ArrayList<>();
        while (count > 0) {
            try {
                List<Message> messages = awsHandler.readMessage(outputsqsUrl);
                outputs.add(objectMapper.readTree(messages.get(0).body()));
                awsHandler.deleteMessage(outputsqsUrl, messages.get(0).receiptHandle());
                count--;
            } catch (Exception e) {

            }
        }
        String html = generateHtml(outputs);
        writeToFile("outputs.html", html);
        awsHandler.uploadToBucket("workerido", "htmlfile", "outputs.html");
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
        htmlBuilder.append(String.format("<div class='line' style='background-color: %s;'>id: %s, link: %s, rank: %d, isSarcasm: %s</div>\n",
                lineColor, json.findValue("revId"), json.findValue("link"), rank, json.findValue("isSarcasm")));
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
}


