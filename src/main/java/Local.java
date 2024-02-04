public class Local {
    public static void main(String[] args) {
        String amid = "ami-029abde7a909e7f6e";
        AWSHandler awsHandler = new AWSHandler();
        awsHandler.createSqs("inputs");
        awsHandler.createSqs("outputs");
        awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://worker-bucket-dsp.s3.amazonaws.com/manager.jar\n" +
                "wget https://workerbucketido.s3.amazonaws.com/input1.txt\n" +
                "java -Xmx2g -jar manager.jar input1.txt\n","manager",amid,1);
    }
    //java -jar manager.jar input1.txt
}
