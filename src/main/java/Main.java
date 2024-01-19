import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.List;
import java.util.Properties;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
//import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

import javax.json.JsonObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;

public class Main {
    public static void main(String[] args) {
        String filePath = "C:\\study\\fifth_semester\\distributed_system_programing\\assignment1\\src\\main\\input1 (1).txt";
        final String usage = """

                Usage:
                   <name> <amiId>

                Where:
                   name - An instance name value that you can obtain from the AWS Console (for example, ami-xxxxxx5c8b987b1a0).\s
                   amiId - An Amazon Machine Image (AMI) value that you can obtain from the AWS Console (for example, i-xxxxxx2734106d0ab).\s
                """;

//        if (args.length != 2) {
//            System.out.println(usage);
//            System.exit(1);
//        }

        String name = "";
        String amiId = "ami-0b0ea68c435eb488d";
        Region region = Region.US_EAST_1;
        Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .build();

        String instanceId = createEC2Instance(ec2, name, amiId);
        System.out.println("The Amazon EC2 Instance ID is " + instanceId);
//        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder().instanceType(InstanceType.T1_MICRO).imageId(amiId).maxCount(1).minCount(1).userData(
//                Base64.getEncoder().encodeToString("".getBytes())
//        )
        ec2.close();


//        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
//            ObjectMapper objectMapper = new ObjectMapper();
//            StringBuilder jsonStringBuilder = new StringBuilder();
//
//            int currentChar;
//            while ((currentChar = br.read()) != -1) {
//                char character = (char) currentChar;
//
//                // Build the JSON string until you find a valid JSON object
//                jsonStringBuilder.append(character);
//
//                try {
//                    // Attempt to parse the JSON string
//                    JsonNode jsonNode = objectMapper.readTree(jsonStringBuilder.toString());
//
//                    // If successful, process the JSON object
//                    for(JsonNode review : jsonNode.findValue("reviews")){
//                        //System.out.println("Review: "+review.findValue("text").asText()+"\nGrade: "+findSentiment(review.findValue("text").asText()));
//                        System.out.println(review.findValue("text").asText());
//                       printEntities(review.findValue("text").asText());
//                    }
//
//                    // Clear the StringBuilder for the next JSON object
//                    jsonStringBuilder.setLength(0);
//                } catch (Exception ignored) {
//                    // Not a complete JSON object yet, continue reading
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

    }
    class Book{
        String title;
        List<review> reviews;
    }
    class review{
        String id;
        String link;
        String title;
        String text;
        int rating;
        String author;
        String date;

    }
    public static int findSentiment(String review) {
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, parse, sentiment");
        StanfordCoreNLP sentimentPipeline = new StanfordCoreNLP(props);
        int mainSentiment = 0;
        if (review!= null && review.length() > 0) {
            int longest = 0;
            Annotation annotation = sentimentPipeline.process(review);
            for (CoreMap sentence : annotation
                    .get(CoreAnnotations.SentencesAnnotation.class)) {
                Tree tree = sentence.get(
                        SentimentCoreAnnotations.SentimentAnnotatedTree.class
//                        SentimentCoreAnnotations.AnnotatedTree.class
                        );
                int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
                String partText = sentence.toString();
                if (partText.length() > longest) {
                    mainSentiment = sentiment;
                    longest = partText.length();
                }
            }
        }
        return mainSentiment;
    }
    public static void printEntities(String review){
        Properties props = new Properties();
        props.put("annotators", "tokenize , ssplit, pos, lemma, ner");
        StanfordCoreNLP NERPipeline = new StanfordCoreNLP(props);
// create an empty Annotation just with the given text
        Annotation document = new Annotation(review);
// run all Annotators on this text
        NERPipeline.annotate(document);
// these are all the sentences in this document
// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
// traversing the words in the current sentence
// a CoreLabel is a CoreMap with additional token-specific methods
            for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
// this is the text of the token
                String word = token.get(TextAnnotation.class);
// this is the NER label of the token
                String ne = token.get(NamedEntityTagAnnotation.class);
                System.out.println("\t-" + word + ":" + ne);
            }
        }
    }

    public static String createEC2Instance(Ec2Client ec2, String name, String amiId) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T1_MICRO)
                .maxCount(1)
                .minCount(1)
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();
        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf("Successfully started EC2 Instance %s based on AMI %s", instanceId, amiId);
            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }
}


