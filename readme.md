# Virtual patent marking crawler

The ongoing [IPRoduct project](http://iproduct.epfl.ch) seeks to link products to the patents upon which they are based
by crawling the web and looking for virtual patent marking (VPM) pages.

The current version of this software reads the [CommonCrawl](http://commoncrawl.org/the-data/get-started/) archive and filters potential VPM pages.
The first filter looks for pages with the "patent" keyword and at least a patent number based on a complex regular expression.

Another filter implements a classifier based keywords found on the page and url,
number of (distinct) patent numbers (matched on the [Patstat](https://wiki.epfl.ch/patstat/whatis) database or not)
and some diversity indexes on matched patent applicants.

Some VPM pages use tables or other regular structures to describe the products and their patents.
The project contains some preliminary work also to automatically detect this structure based on context-free grammars.

Other methods based based on natural language processing, conditional random fields and markov logic networks are being investigated,
but there are not yet published in this repository. This includes both filtering VPM pages and extracting the product-patents correspondences.


## Requirements: Install the follows tools

- [Java JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/)
- [SBT >=0.13.12](http://www.scala-sbt.org/) (build tool for Scala)
- [Apache Spark 2.0.2 Pre-built for Hadoop 2.7](http://spark.apache.org/) (optional)
- [IntelliJ](https://www.jetbrains.com/idea/) with the Scala plugin (IDE, optional)


## Get an input sample
Create a WARC archive using wget:
```
$ mkdir data
$ wget --directory-prefix=/tmp/wget_files/ --warc-file=data/sample --warc-max-size=500M --no-check-certificate --recursive --level=4 --reject gz,tar,zip,gif,js,css,ico,jpg,jpeg,png,tiff,mp3,mp4,mpg,mpeg,avi,rfa https://invue.com/ http://www.muellerwaterproducts.com/ https://www.sjm.com/ https://www.actifio.com/ http://www.ipc.com/

$ du -h data/sample.warc.gz
141M	data/sample.warc.gz

$ gunzip -c data/sample.warc.gz | grep "WARC-Target-URI: h"
WARC-Target-URI: https://invue.com/
WARC-Target-URI: https://invue.com/robots.txt
WARC-Target-URI: https://invue.com/products/
WARC-Target-URI: https://invue.com/retail/
...
WARC-Target-URI: https://invue.com/patents/
...
WARC-Target-URI: http://www.muellerwaterproducts.com/
WARC-Target-URI: http://www.muellerwaterproducts.com/robots.txt
WARC-Target-URI: http://www.muellerwaterproducts.com/about-mueller-water-products
...
```

## Build and run ExtractPatentNumbersFromWarc from the shell

```
$ sbt "runMain iproduct.ExtractPatentNumbersFromWarc --help"

Takes a web archive, and writes a text file with a line for each page with the url and patent numbers found.
(It filters the pages who have the patent keyword and at least one patent number)
Usage: ExtractPatentNumbersFromWarc [options]

  --in <value>             input warc.gz (file or folder)
  --out <value>            output
  --patentKeywordPattern <value>
                           patentKeywordPattern. default=(?i)(patent|brevet|patent|patente|patent|brevetto|patente|octrooi|专利|專利|특허|特許)
  --patentNumberRegex <value>
                           patentNumberRegex. default=(?<![^ :])((((U\.?S\.?|EP|GB|JP) ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?((D ?(# ?)?)|((# ?)?\d[,.']?))\d\d\d[,.']?\d\d\d([,.' ]?[A-Z]\d?)?)|((U\.?S\.? ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?RE ?\d\d[,.']?\d\d\d([,.' ]?[A-Z]\d?)?)|((JP ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?([HS] ?)?(\d{7,8}|\d{10})( ?[A-Z]\d?)?))(?=[,.';:()]?(?![^ ]))
  --AWS_ACCESS_KEY_ID <value>

  --AWS_SECRET_ACCESS_KEY <value>

  --version

$ sbt -Dspark.master=local[*] "runMain iproduct.ExtractPatentNumbersFromWarc --in=./data/sample.warc.gz --out=/tmp/out"
...


$ cat /tmp/out/part-* | sort
http://www.ipc.com/node/292	7,904,056	8,767,942	EP1992094	EP2486721	GB1992094	8,290,138	8,363,572	8,570,853	GB2492511	8,189,566	U.S. Patent Nos. 8,363,572	8,599,834	8,570,853	8,594,278	8,767,942	GB2492511	GB2499155	EP2483796	EP2486721	U.S. Patent No. 8,832,304	U.S. Patent Nos. 8,805,714	9,208,502	GB2502015
http://www.ipc.com/patents	7,904,056	8,767,942	EP1992094	EP2486721	GB1992094	8,290,138	8,363,572	8,570,853	GB2492511	8,189,566	U.S. Patent Nos. 8,363,572	8,599,834	8,570,853	8,594,278	8,767,942	GB2492511	GB2499155	EP2483796	EP2486721	U.S. Patent No. 8,832,304	U.S. Patent Nos. 8,805,714	9,208,502	GB2502015
http://www.muellerwaterproducts.com/content/sewerage-water-board-new-orleans-non-invasively-locates-massive-leak-reduces-non-revenue	7,200,000
http://www.muellerwaterproducts.com/content/sewerage-water-board-new-orleans-non-invasively-locates-massive-leak-reduces-non-revenue	7,200,000
http://www.muellerwaterproducts.com/highlight/mueller-systems-gets-lighter	1,000,000
http://www.muellerwaterproducts.com/highlight/mueller-systems-gets-lighter	1,000,000
https://invue.com/contact/	8900150
https://invue.com/contact/	8900150
https://invue.com/patents/	U.S. Patent Nos. 5,861,807	6,027,277	7,740,214	7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	7,564,351	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 5,861,807	7,740,214	7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,558,414	8,847,759	8,884,762	8,890,691	8,896,447	9,111,428	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,402,486	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,740,214	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,558,414	8,845,356	8,884,762	8,890,691	9,133,649	9,135,800	D741,093	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 5,861,807	6,027,277	7,740,214	7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,181,929	8,242,906	8,558,414	8,674,833	8,847,759	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,740,214	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,558,414	8,845,356	8,884,762	8,890,691	9,111,428	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,368,536	8,845,356	8,847,759	8,884,762	8,890,691	9,103,142	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,212,115	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,890,691	8,896,447	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,542,119	8,842,012	8,884,762	8,890,691	8,896,447	8,994,497	9,133,649	9,135,800	9,396,631	9,428,938	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 8,860,574	8,994,497	U.S. Patent Nos. 7,131,542	6,474,478	6,659,291	7,007,810	7,392,673	U.S. Patent Nos. 7,703,308	7,131,542	6,474,478	6,659,291	7,007,810	8,341,987	8,286,454	6,957,555	7,559,437	8,567,614	8,684,227	U.S. Patent Nos. 7,737,844	7,737,845	7,737,846	9,478,110	D771,056	9,576,452	9,659,472	U.S. Patent Nos. 7,212,115	7,737,843	7,737,844	7,737,845	7,969,305	7,710,266	D702,576	D705,104	8,845,356	8,884,762	8,890,691	9,000,920	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,212,115	7,737,844	7,737,845	7,737,846	7,969,305	8,845,356	8,884,762	8,890,691	8,896,447	9,111,428	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent No. 9,133,649	9,428,938	U.S. Patent No. 9,133,649	9,428,938	U.S. Patent No. 9,133,649	9,428,938	9,428,938	U.S. Patent Nos. 7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,000,920	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,111,428	9,133,649	9,135,800	9,396,631	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,878,673	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,552,708	9,576,452	9,659,472	9,728,054	U.S. Patent Nos. 7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,368,536	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,443,404	9,478,110	9,576,452	9,659,472	D793,779	9,747,765	U.S. Patent Nos. 7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,368,536	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,443,404	9,478,110	9,576,452	9,659,472	D793,779	9,747,765	U.S. Patent Nos. 7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472
https://invue.com/patents/	U.S. Patent Nos. 5,861,807	6,027,277	7,740,214	7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	7,564,351	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 5,861,807	7,740,214	7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,558,414	8,847,759	8,884,762	8,890,691	8,896,447	9,111,428	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,402,486	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,740,214	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,558,414	8,845,356	8,884,762	8,890,691	9,133,649	9,135,800	D741,093	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 5,861,807	6,027,277	7,740,214	7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,181,929	8,242,906	8,558,414	8,674,833	8,847,759	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,740,214	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,558,414	8,845,356	8,884,762	8,890,691	9,111,428	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,368,536	8,845,356	8,847,759	8,884,762	8,890,691	9,103,142	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,212,115	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,890,691	8,896,447	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,542,119	8,842,012	8,884,762	8,890,691	8,896,447	8,994,497	9,133,649	9,135,800	9,396,631	9,428,938	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 8,860,574	8,994,497	U.S. Patent Nos. 7,131,542	6,474,478	6,659,291	7,007,810	7,392,673	U.S. Patent Nos. 7,703,308	7,131,542	6,474,478	6,659,291	7,007,810	8,341,987	8,286,454	6,957,555	7,559,437	8,567,614	8,684,227	U.S. Patent Nos. 7,737,844	7,737,845	7,737,846	9,478,110	D771,056	9,576,452	9,659,472	U.S. Patent Nos. 7,212,115	7,737,843	7,737,844	7,737,845	7,969,305	7,710,266	D702,576	D705,104	8,845,356	8,884,762	8,890,691	9,000,920	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,212,115	7,737,844	7,737,845	7,737,846	7,969,305	8,845,356	8,884,762	8,890,691	8,896,447	9,111,428	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent No. 9,133,649	9,428,938	U.S. Patent No. 9,133,649	9,428,938	U.S. Patent No. 9,133,649	9,428,938	9,428,938	U.S. Patent Nos. 7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,000,920	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	9,111,428	9,133,649	9,135,800	9,396,631	9,466,192	9,478,110	9,576,452	9,659,472	U.S. Patent Nos. 7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,878,673	8,884,762	8,890,691	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,552,708	9,576,452	9,659,472	9,728,054	U.S. Patent Nos. 7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	7,710,266	8,368,536	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,443,404	9,478,110	9,576,452	9,659,472	D793,779	9,747,765	U.S. Patent Nos. 7,629,895	7,737,843	7,737,844	7,737,845	7,737,846	7,969,305	8,368,536	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,443,404	9,478,110	9,576,452	9,659,472	D793,779	9,747,765	U.S. Patent Nos. 7,737,844	7,737,845	7,737,846	7,969,305	8,884,762	8,890,691	8,896,447	9,133,649	9,135,800	9,171,441	9,269,247	9,396,631	9,478,110	9,576,452	9,659,472
https://www.actifio.com/patents/	8,299,944	8,396,905	8,402,004	8,417,674	8,688,650	8,843,489	8,874,863	8,904,126	9,244,967	9,251,198	9,372,758	9,372,866	9,384,207	9,384,254
...
```

We see that there are true-positives, such as patent number "5,861,807" from invue.com,
and false-positives, such as patent number "7,200,000" at muellerwaterproducts.com.
There are many way to improve this result: tunning the regular expression, classifying pages into VPM pages or not, applying other natural languages techniques...


## Run ExtractPatentNumbersFromWarc on all the CommonCrawl dataset stored at Amazon S3
Create an account at Amazon EC2, and replace your own `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`.
```
$ sbt -Dspark.master=local[*] "runMain iproduct.ExtractPatentNumbersFromWarc
--AWS_ACCESS_KEY_ID=AKIAJ6RUSSNHLZWAAAAA --AWS_SECRET_ACCESS_KEY=4F+5K/7t5hWACZJWVwSY8ofO5Zu88XKRVNYAAAAA
--in="s3n://commoncrawl/crawl-data/CC-MAIN-2016-36/segments/1471982290442.1/warc/*"
--out=s3n://your-output-bucket/out

$ s3cmd get s3://your-output-bucket/out
```


## Build and run from IntelliJ

IntelliJ -> Import project -> [find vpm-filter-spark/build.sbt]

All the errors should go away once IntelliJ automatically downloads all the dependencies (this takes a while).

The command "Run Application" does not work (see [ticket](https://youtrack.jetbrains.com/oauth?state=%2Fissue%2FIDEA-161609)), so use `Run SBT Task` as follows:

Menu -> Run -> Edit Configurations -> + -> SBT Task

```
Name: ExtractPatentNumbersFromWarc
Task: "runMain iproduct.ExtractPatentNumbersFromWarc --in=/data/www.ipc.com.warc.gz --out=/tmp/out"
VM Parameters. Add: -Dspark.master=local[*]
```

Menu -> Run -> Run ExtractPatentNumbersFromWarc


## Assembly jar and run with spark-submit

```
# this builds target/scala-2.11/vpm-filter-spark-assembly-*-SNAPSHOT.jar
$ export SBT_OPTS="-Xmx2G"
$ sbt assembly

# launch the application using spark-submit
$ $SPARK_HOME/bin/spark-submit --driver-memory 6G --executor-memory 6G --class iproduct.ExtractPatentNumbersFromWarc --master local[*] target/scala-2.11/vpm-filter-spark-assembly-*-SNAPSHOT.jar --in=/data/www.ipc.com.warc.gz --out=/tmp/out
```


## Run the `ExtractPatentNumbersFromWarc` Spark job on Amazon EC2
Read [this tutorial](readme_run_spark_on_amazon_ec2.md).


## Other tools: FilterArchivePatents
```
$ sbt "runMain iproduct.FilterArchivePatents --help"

Takes a web archive, filters the pages who have the patent keyword and at least one patent number, and writes an output web archive.
Usage: FilterArchivePatents [options]

  --in <value>             input warc.gz (file or folder)
  --discardedDomainsFile <value>
                           file with a list of domain to discard
  --outDir <value>         output dir
  --patentKeywordPattern <value>
                           patentKeywordPattern. default=(?i)(patent|brevet|patent|patente|patent|brevetto|patente|octrooi|专利|專利|특허|特許)
  --patentNumberPattern <value>
                           patentNumberRegex. default=(?<![^ :])((((U\.?S\.?|EP|GB|JP) ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?((D ?(# ?)?)|((# ?)?\d[,.']?))\d\d\d[,.']?\d\d\d([,.' ]?[A-Z]\d?)?)|((U\.?S\.? ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?RE ?\d\d[,.']?\d\d\d([,.' ]?[A-Z]\d?)?)|((JP ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?([HS] ?)?(\d{7,8}|\d{10})( ?[A-Z]\d?)?))(?=[,.';:()]?(?![^ ]))
  --AWS_ACCESS_KEY_ID <value>

  --AWS_SECRET_ACCESS_KEY <value>

  --help                   prints this usage text


$ sbt -Dspark.master=local[*] "runMain iproduct.FilterArchivePatents --in=/data/commoncrawl/warc/ --outDir=/tmp/filteted/"
```


## Other tools: ExportPagesFromWarc
```
$ sbt "runMain iproduct.tools.ExportPagesFromWarc --help"

Export a file for each page in the Web Archive (WARC).
Usage: ExportPagesFromWarc [options]

  --warc <value>        input warc.gz (file or folder)
  --sampleRate <value>  takes a page only every x pages. If sampleRate = 1, it takes all files. If sampleRate = 100, it takes only one page every 100 pages.
  --outDir <value>      output dir
  --help                prints this usage text

$ sbt "runMain iproduct.tools.ExportPagesFromWarc --warc=./data/sample.warc.gz --sampleRate=100 --outDir=/tmp/out"

+++ warcfile: ./data/sample.warc.gz
+++ exporting: https://invue.com/ to /tmp/outC/https:\\invue.com\.html
+++ exporting: https://invue.com/tag/locked-cabinets/ to /tmp/outC/https:\\invue.com\tag\locked-cabinets\.html
+++ exporting: https://invue.com/wp-content/uploads/2017/03/L410_smart_lock.pdf to /tmp/outC/https:\\invue.com\wp-content\uploads\2017\03\L410_smart_lock.pdf.pdf
...
```


## License
See the [license file](license.txt) for license rights and limitations (GNU GPLv3).


## See also
- [run spark on amazon ec2](readme_run_spark_on_amazon_ec2.md)
- [classifier1](readme_classifier1.md)
- [infer grammar](readme_inferGrammar.md)
