import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.List;
import java.util.Properties;

public class EntitiesPrinter {
    Properties props;
    StanfordCoreNLP NERPipeline;
    public EntitiesPrinter(){
        props = new Properties();
        props.put("annotators", "tokenize , ssplit, pos, lemma, ner");
        NERPipeline = new StanfordCoreNLP(props);
    }
    public void printEntities(String review){
// create an empty Annotation just with the given text
        Annotation document = new Annotation(review);

// run all Annotators on this text
        NERPipeline.annotate(document);
// these are all the sentences in this document
// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
// traversing the words in the current sentence
// a CoreLabel is a CoreMap with additional token-specific methods
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
// this is the text of the token
                String word = token.get(CoreAnnotations.TextAnnotation.class);
// this is the NER label of the token
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                System.out.println("\t-" + word + ":" + ne);
            }
        }
    }
}
