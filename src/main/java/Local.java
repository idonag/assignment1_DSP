public class Local {
    public static void main(String[] args) {
        String amid = "ami-0b407295341ecbda3";
        AWSHandler awsHandler = new AWSHandler();
        awsHandler.createSqs("inputs");
        awsHandler.createSqs("outputs");
        awsHandler.createEC2Instance("wget https://workerbucketido.s3.amazonaws.com/manager.jar\nwget https://workerbucketido.s3.amazonaws.com/input1.txt","manager",amid,1);
    }
    //java -jar manager.jar input1.txt
}
