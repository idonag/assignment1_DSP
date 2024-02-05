public class Local {
    public static void main(String[] args) {
        boolean terminate = false;
        String filePath;
        if(args.length > 0 && args[0].equals("t")){
            terminate = true;
            filePath = args[1];
        }
        else {
            filePath = args[0];
        }
        String amid = "ami-06f533aa95c8c0dad";
        AWSHandler awsHandler = new AWSHandler();
        awsHandler.createSqs("files");
        String filesUrlSqs = awsHandler.getSqsUrl("files");
        awsHandler.createSqs("inputs");
        awsHandler.createSqs("outputs");
        //TODO implement how to get the url from s3/from user
        //TODO check if manager exist
        awsHandler.sendMessage(filePath,filesUrlSqs);
        if(!awsHandler.isInstanceWithTagExists("manager")) {
            awsHandler.createEC2Instance("#!/bin/bash\ncd usr/bin/\nmkdir dsp_files\ncd dsp_files\nwget https://workerbucketido.s3.amazonaws.com/manager.jar\n" +
                    "java -Xmx2g -jar manager.jar\n", "manager", amid, 1);
        }
        if(terminate){
            awsHandler.sendMessage("t",filesUrlSqs);
        }

    }
    //java -jar manager.jar input1.txt
}
