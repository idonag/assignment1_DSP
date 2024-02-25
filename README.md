Instructions:
Run the local app using ide or jar, with the following arguments: pathToInput1  pathToInput2... pathToInputN  outputName1 outputName2... outputNameN n [t]
-All the input files pathes , are pathes from buckets in s3.  
-n statitng how many reviews are going to be prcessed by a worker.
-t is an optional flag to terminate the program (manager and workers) after finishing the job.

Explanation:
-Our local applications create a manger if one dose not exists, creates  sqs's if necesery, and send json message to the manager which indicates
what file to process.
-The manager receives the messages and process the files in parallel using thread pool.
-While processing the files, the manager creates worker instances according to the n argument.
-The manager splits the files into tasks, and send those tasks as jsons to the workers via sqs.
-The workes continuously try to pull messages from the 'inputs' sqs, process the review using sentiment analysis algorithms, and sends back
the result in json format to the manager via 'outputs' sqs.
-The manager keeps receiving the answers from the workers, until it is able to make a summary of all the reviews which are part of the same input file.
When done so the manager upload the summary file to s3, and alert the local app that the file is ready.
-The local app receives the alert from the manager, downloads the summary file from s3, using the link sent by the manager, and eventually, creats an html file from summary and saves it in the current directory.

ami used: ami-029abde7a909e7f6e (linux and java)
type of ec2 used: t2-large
astimated time to finish working on all input files with 3 local applications and n=30  -  20 min 
* Although we used n = 30, the limit on our aws account is 9 ec2 instances at the same time. 

Additional explantions:
-Scalability: Our program currently limited by the 9 ec2 limitations and the one only ec2 manager requirement. In optimal situation, with several managers that are able to save locally the files they are working on, and with no ec2 limitations on the number of workers, the program is sacalable to a any number of files or clients (the number of managers and workes will be determine by the size of the input). 
-Presistence: Due to our read/delete method, using the visibility time out of the sqs, we garantee no loss of data even in the case of failing worker. The workers delete the message only after compliting the process of analyse it.  
-The manager using threads to process the files in parallel, it creates a threadPool and process each file with different thread. 
The usuing of threads for different files is a good idea because there is almost no shared data between them.
Contrary to that, using more than one thread to work on a single file can cause problems due to massive need of synchronization.
-The workers are efficient due to the fact they receive small tasks and that the workers are not related to single file, but reading from the sqs the next available task.
-Again, the limitations of our aws account is not compatible with 5 files scenario, so the limitation cause the manager to wait for answers from time to time from the workers. If we had more ec2 workers, the waiting time of the manager will be reduced(with the right amount of workers, it will be reduced to zero).
