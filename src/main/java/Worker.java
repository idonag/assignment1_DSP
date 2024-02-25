import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.ArrayList;
import java.util.List;

public class Worker {
    private static String createResponseJson(String revId,String link,int rank,List<String> entities ,boolean isSarcasm) {
        String entitiesJson;
        try {
            entitiesJson = new ObjectMapper().writeValueAsString(entities);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // Create a JSON structure for the response
        return String.format("{\"id\": \"%s\",\"link\": \"%s\",\"rank\": %d,\"entities\": %s  ,\"isSarcasm\": %s}", revId,link,rank,entitiesJson, isSarcasm);
    }
    public static void main(String[] args) {
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
            List<Message> messages = awsHandler.readMessage(inputsqsUrl,450);
            if(messages.isEmpty()){
                continue;
            }
            Message m = messages.get(0);
            String jsonMessage = m.body();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode reviewJson;
            JsonNode review;
            try {
                reviewJson = objectMapper.readTree(jsonMessage);
                review = reviewJson.findValue("review");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            String localID = reviewJson.findValue("local id").asText();
            String outputFile = reviewJson.findValue("output file").asText();
            String revId = review.findValue("id").asText();
            String link = review.findValue("link").asText();
            int rank = review.findValue("rating").asInt();
            List<String>  entities = new ArrayList<>();
            boolean isSarcasm = false;
            // If successful, process the JSON object
            ep.printEntities(review.findValue("text").asText(),entities);
            int reviewOutput = sf.findSentiment(review.findValue("text").asText());
            if(Math.abs(reviewOutput - review.findValue("rating").asInt()) >= 3){
                isSarcasm = true;
            }
            String responseJson = createResponseJson(revId,link,rank ,entities,isSarcasm);
            String messageToManager = String.format("{\"local id\":\"%s\" ,\"output file\":\"%s\" ,\"result\":%s}",localID,outputFile,responseJson);
            awsHandler.sendMessage(messageToManager, outputsqsUrl,null,null);
            awsHandler.deleteMessage(inputsqsUrl, m.receiptHandle());
        }
    }
}
