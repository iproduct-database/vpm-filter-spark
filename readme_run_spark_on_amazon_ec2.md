# Run the `ExtractPatentNumbersFromWarc` Spark job on Amazon EC2

## Create Amazon EC2 account
http://aws.amazon.com/console/
-> Create an AWS account

## Set-up billing alarm
https://console.aws.amazon.com/billing/
http://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-alarms.html


## Create an Amazon EC2 key pair
http://aws.amazon.com/console/
-> [sing in to console]
-> [change region right-top to US-East-1]
-> EC2
-> Key Pairs
-> Create Key Pair
-> Key pair name: [test-us-east-1]
-> download test-us-east-1.pem

```
$ mv test-us-east-1.pem to ~/.ssh/
$ chmod 400 ~/.ssh/test-us-east-1.pem
```

this file looks like:

```
-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEArb4Y7iqPqpV/WBHlR5sUroixDpud8MizSkz5KJYuN1brTAkF6qowmQj8uKJO
DFeeIuKRtTSPoXKogwcneX6qCApYei7+uPQY5C8+PO+PYAn6qWs++gdkkaYhzAHvB2oTdP8er1qW
...
0VKP7MR6fBViz62bE6DuUgdzSTldbeUH4wzmYJrGi8fNy2heNx14ne7bbZWkMmAudPPjFg==
-----END RSA PRIVATE KEY-----
```

## Create Amazon EC2 access key ID and secret access key
http://aws.amazon.com/console/
-> [my account name]
-> Security Credentials
-> Access Keys (Access Key ID and Secret Access Key)
-> Create New Access Key
-> download rootkey-csv

this looks like:
```
# AWS_ACCESS_KEY_ID: AKIAJ6RUSSNHLZWAAAAA
# AWS_SECRET_ACCESS_KEY: 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
```

## Access the Amazon S3 file system
Install [s3cmd](http://s3tools.org/s3cmd) or [AWS CLI](https://aws.amazon.com/cli/):

```
$ brew install S3cmd  # on OSX

$ workon py2-data   # use python2 (not python3)

$ s3cmd --configure
Access Key: ???
Secret Key: ???
Default Region [US]:
Encryption password: gaetan8
Path to GPG program: # left blank for the moment. TODO: install GPG and reconfigure s3cmd later
Use HTTPS protocol [Yes]:
HTTP Proxy server name:
Test access with supplied credentials? Y
Save settings? [y/N] y
Configuration saved to '/Users/david/.s3cfg'


# Example:
$ s3cmd get s3://aws-publicdatasets/common-crawl/crawl-data/CC-MAIN-2013-48/segments/1386163035819/warc/CC-MAIN-20131204131715-00000-ip-10-33-133-15.ec2.internal.warc.gz

$ s3cmd ls s3://your-output-bucket/

$ s3cmd rm -r s3://your-output-bucket/out

Or use [AWS CLI](https://aws.amazon.com/cli/):
$ aws s3 --no-sign-request cp s3://aws-publicdatasets/common-crawl/crawl-data/CC-MAIN-2013-48/segments/1386163035819/warc/CC-MAIN-20131204131715-00000-ip-10-33-133-15.ec2.internal.warc.gz data/
```


## Select which input data to use
http://commoncrawl.org/the-data/get-started/
-> August 2016 -> all WARC files -> warc.paths.gz, unzip it
The dataset is divided in 100 segments, and each segment has 300 files of 1 GB. Examples:
```
- one file:     s3n://commoncrawl/crawl-data/CC-MAIN-2016-36/segments/1471982290442.1/warc/CC-MAIN-20160823195810-00000-ip-10-153-172-175.ec2.internal.warc.gz
- ten files:    s3n://commoncrawl/crawl-data/CC-MAIN-2016-36/segments/1471982290442.1/warc/CC-MAIN-20160823195810-0000*
- one segment:  s3n://commoncrawl/crawl-data/CC-MAIN-2016-36/segments/1471982290442.1/warc/
- all:          s3n://commoncrawl/crawl-data/CC-MAIN-2016-36/segments/*/warc/
```


## Create a cluster (attention with the amazon fees!)

### Install [spark-ec2](https://github.com/amplab/spark-ec2)
Download and unzip https://github.com/amplab/spark-ec2/archive/branch-1.6.zip

```
$ export AWS_ACCESS_KEY_ID=AKIAJ6RUSSNHLZWAAAAA
$ export AWS_SECRET_ACCESS_KEY=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
```

Choose an [Amazon EC2 machine type](https://aws.amazon.com/ec2/instance-types/), for instance "m3.xlarge", and number of slaves. Note [Pricing](https://aws.amazon.com/ec2/pricing/) and [Calculator](http://calculator.s3.amazonaws.com/index.html).

```
$ spark-ec2 \
--key-pair=test-us-east-1 \
--identity-file=$HOME/.ssh/test-us-east-1.pem \
--region=us-east-1 \
--instance-type=m3.xlarge \
--slaves=1 \
--spark-version=1.6.2 --hadoop-major-version=2.4.0 \
launch my-spark-cluster
```

**Note that we use spark 1.6.2 with hadoop 2.4.0! Running a different version of spark or hadoop might fail with ClassNotFoundException or other strange behaviour.**

## Run the job

```
# login to Amazon EC2 and run it
$ spark-ec2 --key-pair=test-us-east-1 --identity-file=$HOME/.ssh/test-us-east-1.pem --region=us-east-1 login my-spark-cluster

# find out the MASTER url
$ grep "Starting Spark master at spark:" /root/spark/logs/*
16/09/27 11:58:24 INFO master.Master: Starting Spark master at spark://ec2-54-197-194-153.compute-1.amazonaws.com:7077

$ MASTER="spark://ec2-54-197-194-153.compute-1.amazonaws.com:7077"


# copy your program to Amazon EC2:
# $ s3cmd put vpm-filter-spark/target/scala-2.1?/vpm-filter-spark-assembly-*-SNAPSHOT.jar s3://your-output-bucket/
# but I didn't look on how to take it from s3 to the local EC2 machine, so for the moment I upload/download the program to/from Dropbox:

$ wget -Ovpm-filter-spark-assembly-0.1-SNAPSHOT.jar "https://www.dropbox.com/s/s155zwwvr6gct9f/vpm-filter-spark-assembly-0.1-SNAPSHOT.jar?dl=0#"


# run the job
$ /root/spark/bin/spark-submit \
--driver-memory 6G --executor-memory 6G \
--master $MASTER \
--class application.VPMFilter /root/vpm-filter-spark-assembly-0.1-SNAPSHOT.jar \
--AWS_ACCESS_KEY_ID=AKIAJ6RUSSNHLZWAAAAA --AWS_SECRET_ACCESS_KEY=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 \
--in="s3n://commoncrawl/crawl-data/CC-MAIN-2016-36/segments/1471982290442.1/warc/CC-MAIN-20160823195810-0000*" \
--out=s3n://your-output-bucket/out
```


## Destroy your cluster, and stop paying Amazon EC2 fees
```
$ spark-ec2 --region=us-east-1 destroy my-spark-cluster
```


## Download your results (and delete them to stop paying for Amazon S3 storage fees):
```
s3cmd get -r s3://your-output-bucket/out
s3cmd rm -r s3://your-output-bucket/out
```

