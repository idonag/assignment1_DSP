import java.util.UUID;

public class Local {
    public static void main(String[] args) {
        //generate unique id
        UUID uniqueID = UUID.randomUUID();
        boolean terminate = false;
        String filePath;
        if(args.length > 0 && args[0].equals("t")){
            terminate = true;
            filePath = args[1];
        }
        else {
            filePath = args[0];
        }
        String amid = "ami-00e95a9222311e8ed";
        AWSHandler awsHandler = new AWSHandler();
        awsHandler.createSqs("files");
        String filesUrlSqs = awsHandler.getSqsUrl("files");
        awsHandler.createSqs("inputs");
        awsHandler.createSqs("outputs");
        //TODO implement how to get the url from s3/from user
        //TODO check if manager exist
        awsHandler.sendMessage(uniqueID+":"+filePath,filesUrlSqs);
       /* if(!awsHandler.isInstanceWithTagExists("manager")) {
            awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://workerbucketido.s3.amazonaws.com/manager.jar\n" +
                    "java -Xmx2g -jar manager.jar\n", "manager", amid, 1);
        }
        */
        if(terminate){
            awsHandler.sendMessage("t",filesUrlSqs);
        }



    }
    //java -jar manager.jar input1.txt
}
