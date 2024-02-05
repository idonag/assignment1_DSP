import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class Worker {
    private static String createResponseJson(String revId,String link,int rank,List<String> entities ,boolean isSarcasm) {
        String entitiesJson = null;
        try {
            entitiesJson = new ObjectMapper().writeValueAsString(entities);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // Create a JSON structure for the response
        return String.format("{\"id\": \"%s\",\"link\": \"%s\",\"rank\": %d,\"entities\": %s  ,\"isSarcasm\": %s}", revId,link,rank,entitiesJson, isSarcasm);
    }
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
                    String revId = review.findValue("id").asText();
                    String link = review.findValue("link").asText();
                    int rank = review.findValue("rating").asInt();
                    List<String>  entities = new ArrayList<>();
                    boolean isSarcasm = false;
                    // If successful, process the JSON object
                    //System.out.println("Review: " + review.findValue("text").asText());
                    // System.out.println("Grade: " + sf.findSentiment(review.findValue("text").asText()));
                    // ep.printEntities(review.findValue("text").asText());
                    ep.printEntities(review.findValue("text").asText(),entities);
                    int reviewOutput = sf.findSentiment(review.findValue("text").asText());
                    if(Math.abs(reviewOutput - review.findValue("rating").asInt()) >= 3){
                        isSarcasm = true;
                    }
                    String responseJson = createResponseJson(revId,link,rank ,entities,isSarcasm);
                    awsHandler.sendMessage(responseJson, outputsqsUrl);

                    // Clear the StringBuilder for the next JSON object
                    jsonStringBuilder.setLength(0);

                } catch (Exception ignored) {
                    // Not a complete JSON object yet, continue processing
                }

            }
            awsHandler.deleteMessage(inputsqsUrl, m.receiptHandle());
        }
    }
}
