import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;

public class Worker {
    public static void main(String[] args) {
        //TODO: read the review from the sqs instead of the local path
        String filePath = "C:\\Users\\noams\\IdeaProjects\\assignment1_DSP\\assignment1_DSP-master\\src\\main\\review.txt";
        EntitiesPrinter ep = new EntitiesPrinter();
        SentimentFinder sf = new SentimentFinder();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            ObjectMapper objectMapper = new ObjectMapper();
            StringBuilder jsonStringBuilder = new StringBuilder();
//
            int currentChar;
            while ((currentChar = br.read()) != -1) {
                char character = (char) currentChar;

                // Build the JSON string until you find a valid JSON object
                jsonStringBuilder.append(character);

                try {
                    // Attempt to parse the JSON string
                    JsonNode review = objectMapper.readTree(jsonStringBuilder.toString());

                    // If successful, process the JSON object
                    System.out.println("Review: "+review.findValue("text").asText()+"\nGrade: "+sf.findSentiment(review.findValue("text").asText()));
                    ep.printEntities(review.findValue("text").asText());
                    //TODO:push the result to the sqsa

                    // Clear the StringBuilder for the next JSON object
                    jsonStringBuilder.setLength(0);
                } catch (Exception ignored) {
                    // Not a complete JSON object yet, continue reading
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
