public class Local {
    public static void main(String[] args) {
        String amid = "ami-06f533aa95c8c0dad";
        AWSHandler awsHandler = new AWSHandler();
        awsHandler.createSqs("inputs");
        awsHandler.createSqs("outputs");
        awsHandler.createEC2Instance("wget manager-jar-url\nwget input-file\njava -jar manager.jar input-file","manager",amid,1);
    }
}
