import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

public class Worker {
    public static void main(String[] args) {
        //TODO: read the review from the sqs instead of the local path
        // String filePath = "C:\\Users\\noams\\IdeaProjects\\assignment1_DSP\\assignment1_DSP-master\\src\\main\\review.txt";

        System.out.println("running jar");
        EntitiesPrinter ep = new EntitiesPrinter();
        System.out.println("created Entities printer");
        SentimentFinder sf = new SentimentFinder();
        System.out.println("created Sentiment Finder");
        AWSHandler awsHandler = new AWSHandler();
        String inputsqsUrl = awsHandler.getSqsUrl("inputs");
        String outputsqsUrl = awsHandler.getSqsUrl("outputs");
        System.out.println("try receive a message");
        System.out.println(inputsqsUrl);
        while(true) {
            List<Message> messages = awsHandler.readMessage(inputsqsUrl);
            Message m = messages.get(0);
            awsHandler.deleteMessage(inputsqsUrl, m.receiptHandle());
            String jsonMessage = m.body();
            ObjectMapper objectMapper = new ObjectMapper();
            StringBuilder jsonStringBuilder = new StringBuilder();

            for (char character : jsonMessage.toCharArray()) {
                // Build the JSON string until you find a valid JSON object
                jsonStringBuilder.append(character);

                try {
                    // Attempt to parse the JSON string
                    JsonNode review = objectMapper.readTree(jsonStringBuilder.toString());
                    System.out.println("json has been red- :\n" + review.toString());
                    // If successful, process the JSON object
                    //System.out.println("Review: " + review.findValue("text").asText());
                    // System.out.println("Grade: " + sf.findSentiment(review.findValue("text").asText()));
                    // ep.printEntities(review.findValue("text").asText());
                    int reviewOutput = sf.findSentiment(review.findValue("text").asText());
                    awsHandler.sendMessage("" + reviewOutput, outputsqsUrl);

                    // Clear the StringBuilder for the next JSON object
                    jsonStringBuilder.setLength(0);
                } catch (Exception ignored) {
                    // Not a complete JSON object yet, continue processing
                }

            }
        }

    }
}
