# classifier1

This filter implements a classifier based keywords found on the page and url,
number of (distinct) patent numbers (matched on the [Patstat](https://wiki.epfl.ch/patstat/whatis) database or not)
and some diversity indexes on matched patent applicants.


## Steps
```
1. run mysql script prepare_db.sql           in:                                             out: VPM30Domains, patstat_us_one_author_per_patent
2. BuildPages                                in: warc.gz files, discardedDomains.txt         out: case class Page (tables: pages, pagePatents)
3. run mysql script pages_postprocess.sql    in: pagePatents                                 out: domains, remove page duplicates
4. BuildDomainStats                          in: pagePatents, patstat                        out: domainStats
5. ImportUrlRawLabelsToMysql                 in: google doc classifier_urlLabels.tsv         out: UrlRawLabels
6. TrainClassifierAndPredict                 in: UrlRawLabels, pages, domains, domainStats   out: predictOutputFile_*
```


## Details
```
1. run src/main/mysql/classifier1/prepare_db.sql
It requires the Patstat database
It creates the table patstat_us_one_author_per_patent, which takes into account only the first author of each patent.
It also creates VPM30Domains, a sample corpus of VPM domains
takes 1 hour


2. BuildPages
On SCITAS, it takes 15h queue + 20 hours using 5 nodes.
The results can be stored in the sql database, or in a spark file.
If discardedDomains.txt is empty,      we get 36'500 domains, 2'900 k pages, and 70 million patents (with duplicates).
With the current discardedDomains.txt, we get 32'814 domains,   622 k pages, and  6 million patents (with duplicates).

$ sbt -Dspark.master=local[*] "runMain iproduct.classifier1.BuildPages --help"
Usage: BuildPages [options]

  --in <value>             input warc.gz (file or folder)
  --discardedDomainsFile <value>
                           file with a list of domain to discard. default = Some(./discardedDomains.txt)
  --out <value>            output file
  --dbUrl <value>          dbUrl, where tables cc_pages and cc_page_patents will be created
  --createTables <value>   default=true
  --pagesTable <value>     default=pages
  --pagePatentsTable <value>
                           default=pagePatents
  --pageRegexsTable <value>
                           default=pageRegexs
  --patentKeywordPattern <value>
                           patentKeywordPattern. default=(?i)(patent|brevet|patent|patente|patent|brevetto|patente|octrooi|专利|專利|특허|特許)
  --patentNumberRegex <value>
                           patentNumberRegex. default=(?<![^ :])((((U\.?S\.?|EP|GB|JP) ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?((D ?(# ?)?)|((# ?)?\d[,.']?))\d\d\d[,.']?\d\d\d([,.' ]?[A-Z]\d?)?)|((U\.?S\.? ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?RE ?\d\d[,.']?\d\d\d([,.' ]?[A-Z]\d?)?)|((JP ?)?((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\.?) ?)?([HS] ?)?(\d{7,8}|\d{10})( ?[A-Z]\d?)?))(?=[,.';:()]?(?![^ ]))
  --additionalTextRegexs... <value>
                           optional additional text regexs. default=List((?i)virtual.?patent.?marking, (?i)America.?Invents.?Act, (?<!\d)287(?!\d), (?i)\bvpm\b, (?i)protected by([^ ]+ )? patents, (?i)following.*list.*products.*inclusive, (?i)additional patents.*pending, (?i)this.*provided.*satisfy.*patent)
  --additionalUrlRegexs... <value>
                           optional additional url regexs. default=List((?i)virtual.?patent.?marking, (?i)America.?Invents.?Act, (?<!\d)287(?!\d), (?i)\bvpm\b)
  --contextLength <value>  context size from left and right of the patentNumberRegex match. default=10
  --AWS_ACCESS_KEY_ID <value>

  --AWS_SECRET_ACCESS_KEY <value>

  --help                   prints this usage text


$ DBURL="jdbc:mysql://__DB_HOST__/classifier1?user=__DB_USER__&password=__DB_PASSWORD__&useSSL=false&useUnicode=yes&characterEncoding=utf8"

$ sbt -Dspark.master=local[*] "runMain iproduct.classifier1.BuildPages \
  --in=/work/scitas-share/datasets/crawl/crawl-data/CC-MAIN-2016-36/segments/*/warc/*.gz \
  --dbUrl=$DBURL
  "


3. run src/main/mysql/classifier1/pages_postprocess.md
creates the table domains from pagePatents.
commoncrawl has many duplicated urls. this removes url duplicates.
takes 10min


4. BuildDomainStats
$ sbt -Dspark.master=local[*] "runMain iproduct.classifier1.BuildDomainStats $DBURL"
# addIndexes domainStats
takes 10min


5. ImportUrlRawLabelsToMysql
export the google doc https://goo.gl/eI8eqZ to file /tmp/classifier_urlLabels.tsv
$ sbt "runMain iproduct.classifier1.ImportUrlRawLabelsToMysql /tmp/classifier_urlLabels.tsv $DBURL UrlRawLabels"


6. TrainClassifierAndPredict
$ sbt -Dspark.master=local[*] "runMain iproduct.classifier1.TrainClassifierAndPredict $DBURL /tmp/pages_prediction.txt"
takes 1h
```


## Structures

```
case class Page(
                 url: String,
                 domain: String,
                 isHomePage: Boolean,
                 hasPatentKeyword: Boolean,
                 patentNumbers: List[Match],
                 additionalTextMatches: Map[String, List[Match]],
                 additionalUrlMatches: Map[String, List[Match]],
                 numWords: Int,
                 distinctWords: Set[String],
                 patentSequences: Seq[Sequence],
                 longestPatentSequenceSize: Int
               )

case class Match(text: String, context: String)

Example:
Page(
  url = "http://www.ipc.com/patents",

  patentNumbers = List(
    Match("7,904,056", "ent Nos.: 7,904,056; 8,767,94"),
    Match("8,767,942", ",904,056; 8,767,942 Singapore"),
    Match("8,290,138", "ent Nos.: 8,290,138; 8,363,57"),
    Match("8,363,572", ",290,138; 8,363,572; 8,570,85"),
    Match("8,570,853", ",363,572; 8,570,853 Singapore"),
    Match("8,189,566", "tent No.: 8,189,566 Unigy® CC"),
    Match("8,363,572", "tent Nos. 8,363,572; 8,599,83"),
    Match("8,599,834", ",363,572; 8,599,834; 8,570,85"),
    Match("8,570,853", ",599,834; 8,570,853; 8,594,27"),
    Match("8,594,278", ",570,853; 8,594,278; 8,767,94"),
    Match("8,767,942", ",594,278; 8,767,942 Singapore"),
    Match("8,805,714", "tent Nos. 8,805,714 Patents P"),
    Match("9,208,502", " Pending: 9,208,502; 2013/011")
  ),

  additionalUrlMatches = Map(
    "(?i)patent"                   -> List(Match("patent", "w.ipc.com/patents")),
    "(?i)\bvpm\b"                  -> List(),
    "(?i)virtual.?patent.?marking" -> List(),
    "(?i)America.?Invents.?Act"    -> List(),
    "(?<!\d)287(?!\d)"             -> List(),
  ),

  additionalTextMatches = Map(
    "(?i)\bvpm\b" -> List(),
    "(?i)virtual.?patent.?marking" -> List(
      Match("virtual patent marking", "tisfy the virtual patent marking provision")
    ),
    "(?i)America.?Invents.?Act" -> List(
      Match("America Invents Act", "16 of the America Invents Act. Addition")
    ),
    "(?<!\d)287(?!\d)" -> List(),
    "(?i)patent" -> List(
      Match("Patent", "tf-8 b4df Patents | IPC Po"),
      Match("Patent", "re Home / Patents Patents "),
      Match("Patent", "/ Patents Patents The foll"),
      Match("patent", "tected by patents in the U"),
      Match("patent", "e virtual patent marking p"),
      Match("patent", "dditional patents may be p"),
      Match("Patent", "Edge U.S. Patent Nos.: 7,9"),
      Match("Patent", " Site Map Patents Privacy ")
    )
  ),

  numWords = 687,

  numDistinctWords = 270,

  distinctWords = Set("speaker", "e", "enable", "mission", "legacy", "copyright", "reserved", "papers", "for", "patents", "program", "s", "network", "support"...)
)

table domains: domain, numPages, numPatentsWithDuplicates, numDistinctPatents

table domainStats: domain, numDistinctMatchedPatents, numDistinctPatents, distinctMatchedPatentsRatio, numDistinctHanNames, shareTop*, hanNamesHerfindhalIndex

manualy build:
table UrlRawLabels: url, pageType (mainly vpm or not), patentNumbersInPage, truePatentNumbers, pageAboutPatents

patstat: database of US and EU patents from the European Patent Office
https://www.epo.org/searching-for-patents/business/patstat.html

features of the classifier:
0	PositiveIntFeature(text_regex_1:(?i)virtual.?patent.?marking)
1	PositiveIntFeature(text_regex_2:(?i)America.?Invents.?Act)
2	PositiveIntFeature(text_regex_3:(?<!\d)287(?!\d))
3	PositiveIntFeature(text_regex_4:(?i)\bvpm\b)
4	PositiveIntFeature(url_regex_1:(?i)virtual.?patent.?marking)
5	PositiveIntFeature(url_regex_2:(?i)America.?Invents.?Act)
6	PositiveIntFeature(url_regex_3:(?<!\d)287(?!\d))
7	PositiveIntFeature(url_regex_4:(?i)\bvpm\b)
8	PositiveIntFeature(numWords)
9	PositiveIntFeature(numDistinctWords)
10	PositiveIntFeature(numPages)
11	PositiveIntFeature(numDistinctMatchedPatents)
12	PositiveIntFeature(numDistinctHanNames)
13	RatioFeature(shareTop1)
14	RatioFeature(shareTop2)
15	RatioFeature(shareTop3)
16	RatioFeature(shareTop4)
17	RatioFeature(shareTop5)
18	RatioFeature(hanNamesHerfindhalIndex)
19	WordFeature(page,patent)
20	WordFeature(page,patents)
21	WordFeature(page,patented)
22	WordFeature(page,product)
23	WordFeature(page,products)
24	WordFeature(page,license)
...
70	WordFeature(page,jobs)
71	WordFeature(url,patent)
72	WordFeature(url,patents)
73	WordFeature(url,patented)
...
122	WordFeature(url,jobs)
```


## Stats
Some stats running with an empty discardedDomains.txt.

```
mysql> select count(*) from cc_domains;
36'506

mysql> select count(*) from cc_pages;
2'962'460

mysql> select count(*) from cc_page_patents;
71'666'032

mysql> select count(*) from cc_patents;
5'562'091


mysql> select * from cc_domains order by num_distinct_patents desc limit 100;
+-------------------------------+-----------+-----------------------------+----------------------+
| domain                        | num_pages | num_patents_with_duplicates | num_distinct_patents |
+-------------------------------+-----------+-----------------------------+----------------------+
| google.com                    |         0 |                    33985478 |              3414004 |   // fix: update mysql server
| uspto.gov                     |      5413 |                     2014360 |              1775796 |
| google.es                     |     79884 |                     8068366 |              1650819 |
| google.fr                     |     45995 |                     4409373 |              1272460 |
| google.com.au                 |     45305 |                     4725364 |              1215850 |
| google.ca                     |     43003 |                     5102081 |              1085453 |
| google.co.uk                  |     32811 |                     4038926 |               937474 |
| google.de                     |      7332 |                      807630 |               409294 |
| archive.org                   |      7626 |                      607884 |               389665 |
| godlikeproductions.com        |    190779 |                      371947 |               333604 |
| patentsencyclopedia.com       |     91893 |                     1013165 |               253076 |
| patents.com                   |     32791 |                      218704 |               136943 |
| google.cl                     |      1713 |                      247985 |                99521 |
| sec.gov                       |      5291 |                      204246 |                90002 |
| prnewswire.com                |      9132 |                      137041 |                80082 |
| getfilings.com                |      3620 |                      305581 |                67752 |
| freepatentsonline.com         |     12622 |                       89070 |                60569 |
| scribd.com                    |      4903 |                      129149 |                48229 |
| patentgenius.com              |     10066 |                       64321 |                46242 |
| google.se                     |       441 |                       50926 |                39989 |
| wikinvest.com                 |      1206 |                       36578 |                22714 |
| wordpress.com                 |     17440 |                       36743 |                17551 |
| justia.com                    |      2046 |                       22973 |                16280 |
| google.com.mx                 |       291 |                       15689 |                12909 |
| ufl.edu                       |      2474 |                       23376 |                12357 |
| newswire.ca                   |      2428 |                       21101 |                11841 |
| state.ny.us                   |        37 |                       21638 |                11251 |
| bio-medicine.org              |      7504 |                       20152 |                 9582 |
| wikipedia.org                 |      5591 |                       31586 |                 9046 |
| loc.gov                       |      8073 |                       19977 |                 8853 |
| issuu.com                     |      1403 |                       16741 |                 8423 |
| sumobrain.com                 |      1488 |                       12247 |                 8385 |
| google.it                     |       298 |                       10922 |                 8351 |
| secinfo.com                   |       241 |                       14070 |                 7811 |
| federalregister.gov           |      1045 |                       11624 |                 6265 |
| tsj.gob.ve                    |       106 |                        7570 |                 6135 |
| freshpatents.com              |      1593 |                        7307 |                 5814 |
| osti.gov                      |     21746 |                      451465 |                 5664 |
| google.nl                     |       140 |                        6219 |                 5296 |
| sys-con.com                   |      1568 |                       12764 |                 4945 |
| tp1rc.edu.tw                  |         1 |                        4719 |                 4717 |
| cnet.com                      |      5962 |                       22854 |                 4693 |
| digitimes.com                 |       967 |                       22089 |                 4681 |
| google.co.in                  |       117 |                        6272 |                 4312 |
| drugbank.ca                   |      1248 |                        5500 |                 4181 |
| erepublik.com                 |      5116 |                       11657 |                 4122 |
| fool.com                      |      2004 |                        5787 |                 3912 |
| openjurist.org                |      1665 |                        5976 |                 3636 |
| libertyfund.org               |       293 |                       72816 |                 3580 |
| cracked.com                   |       753 |                       10788 |                 3578 |
| biospace.com                  |      2425 |                        7737 |                 3506 |
| gurufocus.com                 |      1288 |                        5248 |                 3426 |
| tmcnet.com                    |      1145 |                        7773 |                 3216 |
| google.com.hk                 |        35 |                        3905 |                 3172 |
| drugs.com                     |      1260 |                        4439 |                 2803 |
| techvibes.com                 |        59 |                        3697 |                 2768 |
| todobrandsen.blogspot.com     |         2 |                        5746 |                 2743 |
| gpo.gov                       |      1120 |                        5937 |                 2603 |
| advfn.com                     |       208 |                        4164 |                 2364 |
| globenewswire.com             |       337 |                        2914 |                 2211 |
| patentlyapple.com             |       625 |                        2560 |                 2163 |
| ancestry.com                  |       406 |                        2667 |                 2160 |
| marketwired.com               |       456 |                        3181 |                 2060 |
| wikisource.org                |       231 |                        3076 |                 2034 |
| thestreet.com                 |       836 |                        3062 |                 2031 |
| nysenate.gov                  |         8 |                        6452 |                 2027 |
| slideshare.net                |       379 |                        3063 |                 2013 |
| inventioninfo.com             |         1 |                        2009 |                 2009 |
| uky.edu                       |      1957 |                        5187 |                 1976 |
| businesswire.com              |       272 |                        3071 |                 1973 |
| lohud.com                     |         1 |                        1955 |                 1949 |
| jdsupra.com                   |      1509 |                        4805 |                 1910 |
| encyclopedia.com              |       101 |                        2992 |                 1896 |
| uwaterloo.ca                  |        11 |                        1899 |                 1890 |
| websitelooker.net             |       174 |                        2363 |                 1876 |
| investegate.co.uk             |       147 |                        3442 |                 1865 |
| happi.com                     |      1808 |                        4367 |                 1864 |
| nanotech-now.com              |       457 |                        2618 |                 1804 |
| wipo.int                      |       736 |                        2524 |                 1786 |
| google.ru                     |        30 |                        2316 |                 1737 |
| reedtech.com                  |        10 |                        1695 |                 1684 |
| morningstar.com               |        71 |                        2571 |                 1537 |
| oclc.org                      |      2616 |                       10420 |                 1518 |
| rexresearch.com               |       174 |                        2211 |                 1423 |
| law360.com                    |       760 |                        2556 |                 1400 |
| drugpatentwatch.com           |       945 |                        3981 |                 1370 |
| typepad.com                   |       809 |                        3409 |                 1369 |
| findlaw.com                   |       512 |                        3412 |                 1335 |
| patentdocs.org                |       402 |                        2833 |                 1329 |
| laprensamisiones.blogspot.com |         1 |                        1340 |                 1322 |
| si.edu                        |       882 |                        3807 |                 1318 |
| ncdcr.gov                     |      2651 |                       31113 |                 1301 |
| applepatent.com               |        51 |                        1503 |                 1276 |
| slashdot.org                  |      5186 |                       10547 |                 1268 |
| biomedsearch.com              |        75 |                        1366 |                 1264 |
| youtube.com                   |       312 |                        1350 |                 1254 |
| google.pl                     |        23 |                        1353 |                 1232 |
| yahoo.com                     |       310 |                        1747 |                 1227 |
| patentados.com                |       254 |                        1877 |                 1217 |
| sltrib.com                    |        39 |                        1209 |                 1206 |
+-------------------------------+-----------+-----------------------------+----------------------+
100 rows in set (0.00 sec)



mysql> select * from cc_domains order by num_pages desc limit 50;
+-------------------------+-----------+-----------------------------+----------------------+
| domain                  | num_pages | num_patents_with_duplicates | num_distinct_patents |
+-------------------------+-----------+-----------------------------+----------------------+
| godlikeproductions.com  |    190779 |                      371947 |               333604 |
| salaryexpert.com        |    163522 |                      395943 |                    5 |
| patentsencyclopedia.com |     91893 |                     1013165 |               253076 |
| eventful.com            |     85630 |                       88955 |                   12 |
| google.es               |     79884 |                     8068366 |              1650819 |
| google.fr               |     45995 |                     4409373 |              1272460 |
| google.com.au           |     45305 |                     4725364 |              1215850 |
| smartfurniture.com      |     45286 |                       50516 |                    2 |
| persol.com              |     44489 |                       66329 |                    1 |
| google.ca               |     43003 |                     5102081 |              1085453 |
| google.co.uk            |     32811 |                     4038926 |               937474 |
| patents.com             |     32791 |                      218704 |               136943 |
| osti.gov                |     21746 |                      451465 |                 5664 |
| gc.com                  |     21029 |                       21833 |                    1 |
| local.com               |     20539 |                       21279 |                    4 |
| wordpress.com           |     17440 |                       36743 |                17551 |
| jobs.com                |     16315 |                       69961 |                    6 |
| self.com                |     16294 |                       18128 |                    1 |
| monster.com             |     14741 |                       83649 |                    6 |
| freepatentsonline.com   |     12622 |                       89070 |                60569 |
| elance.com              |     12433 |                       38776 |                   11 |
| patentgenius.com        |     10066 |                       64321 |                46242 |
| prnewswire.com          |      9132 |                      137041 |                80082 |
| canon.com               |      8638 |                       21813 |                   43 |
| loc.gov                 |      8073 |                       19977 |                 8853 |
| archive.org             |      7626 |                      607884 |               389665 |
| bio-medicine.org        |      7504 |                       20152 |                 9582 |
| google.de               |      7332 |                      807630 |               409294 |
| dailytech.com           |      6702 |                       18669 |                  206 |
| poz.com                 |      6602 |                       19385 |                   23 |
| city-data.com           |      6440 |                        7565 |                  495 |
| carfax.com              |      6188 |                       87111 |                    9 |
| cnet.com                |      5962 |                       22854 |                 4693 |
| paperbackswap.com       |      5894 |                       12313 |                  207 |
| pianoworld.com          |      5741 |                        6416 |                  499 |
| wikipedia.org           |      5591 |                       31586 |                 9046 |
| denverpost.com          |      5550 |                        5764 |                   26 |
| uspto.gov               |      5413 |                     2014360 |              1775796 |
| abebooks.com            |      5316 |                        5752 |                  236 |
| sec.gov                 |      5291 |                      204246 |                90002 |
| slashdot.org            |      5186 |                       10547 |                 1268 |
| erepublik.com           |      5116 |                       11657 |                 4122 |
| ray-ban.com             |      5044 |                        5293 |                    1 |
| scribd.com              |      4903 |                      129149 |                48229 |
| mounts.com              |      4728 |                       12221 |                    4 |
| ashleystewart.com       |      4191 |                        5077 |                    1 |
| getfilings.com          |      3620 |                      305581 |                67752 |
| nutritionexpress.com    |      3577 |                       10536 |                  107 |
| laserpegs.com           |      3438 |                        3803 |                    1 |
| lampsplus.com           |      3403 |                        3789 |                    3 |
+-------------------------+-----------+-----------------------------+----------------------+
50 rows in set (0.00 sec)


mysql> select * from cc_domains where num_distinct_patents > 30 and num_distinct_patents < 1000 limit 10;
+------------------------+-----------+-----------------------------+----------------------+
| domain                 | num_pages | num_patents_with_duplicates | num_distinct_patents |
+------------------------+-----------+-----------------------------+----------------------+
| accessgenealogy.com    |        27 |                          74 |                   31 |
| alarm.com              |         1 |                          31 |                   31 |
| all8.com               |         1 |                          31 |                   31 |
| ambientediritto.it     |         2 |                          36 |                   31 |
| americanheritage.com   |        77 |                         118 |                   31 |
| biomassmagazine.com    |        51 |                          68 |                   31 |
| cam.ac.uk              |        30 |                          59 |                   31 |
| cancergenetics.com     |         1 |                          34 |                   31 |
| carsandracingstuff.com |        10 |                          32 |                   31 |
| cbsa-asfc.gc.ca        |         4 |                          33 |                   31 |
+------------------------+-----------+-----------------------------+----------------------+
10 rows in set (0.00 sec)


mysql> select * from cc_domains where num_pages < 10 and num_distinct_patents > 10 limit 100;
+----------------------------------------+-----------+-----------------------------+----------------------+
| domain                                 | num_pages | num_patents_with_duplicates | num_distinct_patents |
+----------------------------------------+-----------+-----------------------------+----------------------+
| 8000vueltas.com                        |         2 |                          11 |                   11 |
| aboutjoints.com                        |         1 |                          11 |                   11 |
| acicontrols.com                        |         4 |                          14 |                   11 |
| adaywithoutcancer.ca                   |         2 |                          24 |                   11 |
| advocaciaornellas.com                  |         1 |                          13 |                   11 |
| ahrcanum.com                           |         9 |                          22 |                   11 |
| ajeng-blog.blogspot.com                |         1 |                          20 |                   11 |
| alicecorp.com                          |         1 |                          15 |                   11 |
| alternatehistory.com                   |         2 |                          12 |                   11 |
| america-land.blogspot.com              |         1 |                          11 |                   11 |
| amgen.com                              |         6 |                          14 |                   11 |
| amigura.co.uk                          |         4 |                          12 |                   11 |
| anewdomain.net                         |         6 |                          17 |                   11 |
| angola-brasil.blogspot.com             |         3 |                          12 |                   11 |
| aquafoam.com                           |         2 |                          11 |                   11 |
| archivodeportes158.blogspot.com        |         1 |                          13 |                   11 |
| armasonline.org                        |         8 |                          12 |                   11 |
| atarihq.com                            |         4 |                          26 |                   11 |
| atruthsoldier.com                      |         7 |                          20 |                   11 |
| autodesk.com                           |         1 |                          22 |                   11 |
| autoline.tv                            |         2 |                          22 |                   11 |
| avirubin.com                           |         1 |                          11 |                   11 |
| avtometals.gi                          |         3 |                          21 |                   11 |
| azure.com                              |         9 |                          35 |                   11 |
| basureronacional.blogspot.com          |         6 |                          11 |                   11 |
| befreetech.com                         |         2 |                          16 |                   11 |
| benthamscience.com                     |         7 |                          21 |                   11 |
| besserwisserseite.de                   |         2 |                          11 |                   11 |
| biochemia-medica.com                   |         1 |                          11 |                   11 |
| biopharmcatalyst.com                   |         1 |                          11 |                   11 |
| birdaviationmuseum.com                 |         1 |                          15 |                   11 |
| bleacherreport.com                     |         5 |                          18 |                   11 |
| blogd.com                              |         6 |                          38 |                   11 |
| bva.at                                 |         1 |                          11 |                   11 |
| cactus.org                             |         2 |                          24 |                   11 |
| calisma-kitabi.com                     |         1 |                          11 |                   11 |
| callutheran.edu                        |         6 |                          22 |                   11 |
| cencein.blogspot.com                   |         1 |                          12 |                   11 |
| chavez.org.ve                          |         1 |                          28 |                   11 |
| chembl.blogspot.com                    |         3 |                          11 |                   11 |
| china-embassy.org                      |         1 |                          11 |                   11 |
| cinsay.com                             |         1 |                          11 |                   11 |
| clarisonic.com                         |         2 |                          74 |                   11 |
| cnyhomes.com                           |         2 |                          44 |                   11 |
| colmenaroja.net                        |         1 |                          13 |                   11 |
| connectivityscorecard.org              |         1 |                          11 |                   11 |
| conspiracionesilluminatis.blogspot.com |         4 |                          11 |                   11 |
| courant.com                            |         6 |                          13 |                   11 |
| criver.com                             |         1 |                          11 |                   11 |
| cruise-ship.info                       |         1 |                          14 |                   11 |
| csusm.edu                              |         2 |                          20 |                   11 |
| dallasonline.us                        |         1 |                          22 |                   11 |
| derose.net                             |         1 |                          11 |                   11 |
| digital-law-online.info                |         7 |                          11 |                   11 |
| digitalimaginggroup.ca                 |         2 |                          26 |                   11 |
| dinero.com                             |         9 |                          12 |                   11 |
| directorblue.blogspot.com              |         6 |                          14 |                   11 |
| displayconsulting.com                  |         1 |                          11 |                   11 |
| dissercat.com                          |         4 |                          12 |                   11 |
| dividend.com                           |         7 |                          11 |                   11 |
| diyphotography.net                     |         8 |                          18 |                   11 |
| docketreport.blogspot.com              |         9 |                          13 |                   11 |
| docusign.com                           |         7 |                          18 |                   11 |
| dodge-wiki.com                         |         1 |                          11 |                   11 |
| doglegright.com                        |         2 |                          22 |                   11 |
| domain.com.au                          |         1 |                          22 |                   11 |
| educ.ar                                |         3 |                          13 |                   11 |
| eeoc.gov                               |         6 |                          63 |                   11 |
| efemproje.com                          |         3 |                          33 |                   11 |
| elliott.org                            |         4 |                          23 |                   11 |
| emagister.com                          |         3 |                          12 |                   11 |
| environmentalmarketsnewsletter.com     |         3 |                          29 |                   11 |
| enviroreporter.com                     |         4 |                          13 |                   11 |
| eremit.dk                              |         1 |                          11 |                   11 |
| esprockets.com                         |         1 |                          11 |                   11 |
| eurogamer.net                          |         8 |                          14 |                   11 |
| fallbrookrailway.com                   |         1 |                          12 |                   11 |
| fat-advertise.blogspot.com             |         1 |                          12 |                   11 |
| fathersmanifesto.net                   |         4 |                          13 |                   11 |
| fatl-advertise.blogspot.com            |         2 |                          24 |                   11 |
| finanznachrichten.de                   |         7 |                          17 |                   11 |
| fitness.com                            |         1 |                          12 |                   11 |
| florencecradleofrenaissance.info       |         1 |                          14 |                   11 |
| fnal.gov                               |         3 |                          11 |                   11 |
| foolsmountain.com                      |         6 |                          19 |                   11 |
| fotogramas.es                          |         2 |                          12 |                   11 |
| fpcgil.net                             |         1 |                          11 |                   11 |
| fractalfield.com                       |         3 |                          21 |                   11 |
| francesdinkelspiel.blogspot.com        |         1 |                          11 |                   11 |
| french-soft-cuisine.info               |         1 |                          11 |                   11 |
| gameit.es                              |         1 |                          13 |                   11 |
| gatesofvienna.blogspot.com.au          |         1 |                          17 |                   11 |
| georgiapatentlaw.com                   |         1 |                          22 |                   11 |
| gigsby.com                             |         3 |                          24 |                   11 |
| grainger.com                           |         1 |                          11 |                   11 |
| grudge-match.com                       |         7 |                          12 |                   11 |
| gulli.com                              |         3 |                          12 |                   11 |
| hipertextual.com                       |         6 |                          16 |                   11 |
| hometheaterhifi.com                    |         8 |                          14 |                   11 |
| homeworktutorials.blogspot.com         |         1 |                          13 |                   11 |
+----------------------------------------+-----------+-----------------------------+----------------------+
100 rows in set (0.00 sec)


mysql> select * from cc_domains where domain in (select domain from vpm30) order by domain;
+---------------------+-----------+-----------------------------+----------------------+
| domain              | num_pages | num_patents_with_duplicates | num_distinct_patents |
+---------------------+-----------+-----------------------------+----------------------+
| blackberry.com      |         4 |                        1632 |                  377 |
| callawaygolf.com    |         1 |                           3 |                    3 |
| davisnet.com        |         2 |                          26 |                   13 |
| dolby.com           |         2 |                           6 |                    4 |
| pg.com              |         1 |                           2 |                    1 |
| rapiscansystems.com |         1 |                          44 |                   34 |
| sjm.com             |         1 |                           1 |                    1 |
| sunpower.com        |        11 |                         154 |                   86 |
| symantec.com        |         1 |                           1 |                    1 |
| tivo.com            |        19 |                          66 |                   30 |
| viatech.com         |         1 |                           2 |                    2 |
+---------------------+-----------+-----------------------------+----------------------+
11 rows in set (0.07 sec)


mysql> select count(*) from cc_pages where text_vpm_r1+text_vpm_r2+text_vpm_r3+text_vpm_r4+url_vpm_r1+url_vpm_r2+url_vpm_r3+url_vpm_r4 > 0;
94'857


mysql> select count(distinct(domain)) from cc_pages where text_vpm_r1+text_vpm_r2+text_vpm_r3+text_vpm_r4+url_vpm_r1+url_vpm_r2+url_vpm_r3+url_vpm_r4 > 0;
3'490


mysql> select distinct(domain) from cc_pages where text_vpm_r1+text_vpm_r2+text_vpm_r3+text_vpm_r4+url_vpm_r1+url_vpm_r2+url_vpm_r3+url_vpm_r4 > 0;
+-------------------------------------------------------------------+
| domain                                                            |
+-------------------------------------------------------------------+
| tr.gg                                                             |
| 1-single-letter-domains.com                                       |
| 10qdetective.blogspot.com                                         |
| poff.ee                                                           |
| commerce.gov                                                      |
| bokmassan.se                                                      |
| 2164th.blogspot.com                                               |
| 23vistamarina.com                                                 |
| antiquesnavigator.com                                             |
| 271patent.blogspot.com                                            |
| webs.com                                                          |
| 55tools.blogspot.com                                              |
| hikayen.net                                                       |
| 99kdy.net                                                         |
| aarf.fr                                                           |
| mtsu.edu                                                          |
| aboveavgjane.blogspot.com                                         |
| abs.gov.au                                                        |
| art122-5.net                                                      |
| academic-genealogy.com                                            |
| acolori.it                                                        |
| acroquer.com                                                      |
| activation-keys.ru                                                |
| actualicese.com                                                   |
| motololo.fr                                                       |
| adonay-diccionariobiblico.blogspot.com                            |


mysql> select num_patents, domain, url, isHomePage, text_vpm_r1, text_vpm_r2, text_vpm_r3, text_vpm_r4, url_vpm_r1, url_vpm_r2, url_vpm_r3, url_vpm_r4 from cc_pages where domain in (select domain from vpm30) order by domain;
+-------------+---------------------+----------------------------------------------------------------------------------------------------------------------------------------+------------+-------------+-------------+-------------+-------------+------------+------------+------------+------------+
| num_patents | domain              | url                                                                                                                                    | isHomePage | text_vpm_r1 | text_vpm_r2 | text_vpm_r3 | text_vpm_r4 | url_vpm_r1 | url_vpm_r2 | url_vpm_r3 | url_vpm_r4 |
+-------------+---------------------+----------------------------------------------------------------------------------------------------------------------------------------+------------+-------------+-------------+-------------+-------------+------------+------------+------------+------------+
|           2 | blackberry.com      | http://us.blackberry.com/company/about-us/corporate-responsibility/supply-chain.html                                                   |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | blackberry.com      | http://us.blackberry.com/content/dam/blackBerry/pdf/legal/global/bbm-terms/09-BBM_Terms_-_EU-INT_Danish.html                           |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|        1628 | blackberry.com      | http://us.blackberry.com/legal/blackberry-virtual-patent-marking.html                                                                  |          0 |           4 |           0 |           2 |           0 |          1 |          0 |          0 |          0 |
|           1 | blackberry.com      | http://www.blackberry.com/newsletters/connection/it/i409/wirelessleadership.shtml                                                      |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           3 | callawaygolf.com    | http://ir.callawaygolf.com/phoenix.zhtml?c=68083&p=irol-news                                                                           |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          13 | davisnet.com        | http://www.davisnet.com/legal/                                                                                                         |          0 |           1 |           0 |           1 |           0 |          0 |          0 |          0 |          0 |
|          13 | davisnet.com        | http://www.davisnet.com/legal/#privacypolicy                                                                                           |          0 |           1 |           0 |           1 |           0 |          0 |          0 |          0 |          0 |
|           4 | dolby.com           | http://blog.dolby.com/2014/01/sharp-demonstrate-worlds-largest-glasses-free-3d-tv-developed-philips-dolby/                             |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           2 | dolby.com           | http://www.dolby.com/us/en/technologies/speech-gating-reference-code/agreement.html                                                    |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           2 | pg.com              | http://www.pg.com/es_LATAM/MX/marcas-p-and-g/innovacion-empresarial.shtml                                                              |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          44 | rapiscansystems.com | http://www.rapiscansystems.com/en/company/virtual_patent_marking                                                                       |          0 |           5 |           1 |           0 |           0 |          1 |          0 |          0 |          0 |
|           1 | sjm.com             | http://investors.sjm.com/default.aspx?SectionId=2355eefe-458c-4fdb-9075-55bec3806390                                                   |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          24 | sunpower.com        | http://newsroom.sunpower.com/2013-10-30-SunPower-Reports-Third-Quarter-2013-Results                                                    |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          25 | sunpower.com        | http://newsroom.sunpower.com/2014-02-12-SunPower-Reports-Fourth-Quarter-and-Fiscal-Year-2013-Results                                   |          0 |           1 |           0 |           3 |           0 |          0 |          0 |          0 |          0 |
|          17 | sunpower.com        | http://newsroom.sunpower.com/2014-04-24-SunPower-Reports-First-Quarter-2014-Results                                                    |          0 |           1 |           0 |           2 |           0 |          0 |          0 |          0 |          0 |
|          26 | sunpower.com        | http://newsroom.sunpower.com/2014-07-31-SunPower-Reports-Second-Quarter-2014-Results                                                   |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | sunpower.com        | http://newsroom.sunpower.com/2014-08-05-Audi-energy-New-sustainability-program-will-add-to-e-tron-experience                           |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          10 | sunpower.com        | http://newsroom.sunpower.com/press-releases?item=122671                                                                                |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          16 | sunpower.com        | http://newsroom.sunpower.com/press-releases?item=122822                                                                                |          0 |           1 |           0 |           1 |           0 |          0 |          0 |          0 |          0 |
|           1 | sunpower.com        | http://newsroom.sunpower.com/press-releases?item=122828                                                                                |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | sunpower.com        | http://newsroom.sunpower.com/press-releases?item=122832                                                                                |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          19 | sunpower.com        | http://newsroom.sunpower.com/press-releases?item=122882                                                                                |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          14 | sunpower.com        | http://newsroom.sunpower.com/press-releases?item=122922                                                                                |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | symantec.com        | https://support.symantec.com/en_US/article.TECH102611.html                                                                             |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1261660&highlight=                                               |          0 |           0 |           0 |           1 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1316525&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           3 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1324787&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1324788&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&id=1359102                                                          |          0 |           0 |           0 |           2 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1359102&highlight=                                               |          0 |           0 |           0 |           2 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1557614&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           3 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&id=1664735                                                          |          0 |           0 |           0 |           2 |           0 |          0 |          0 |          0 |          0 |
|           6 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1664735&highlight=                                               |          0 |           0 |           0 |           2 |           0 |          0 |          0 |          0 |          0 |
|           5 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1729867&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           4 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&id=1822298                                                          |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           4 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1822298&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|          12 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&id=1850119                                                          |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           6 | tivo.com            | http://investor.tivo.com/phoenix.zhtml?c=106292&p=irol-newsArticle&ID=1850119&highlight=                                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://pr.tivo.com/press-releases/halftime-seinfeld-appearance-takes-the-top-spot-in-tivo-s-annual-super-bowl-repo-nasdaq-tivo-1086662 |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           7 | tivo.com            | http://pr.tivo.com/press-releases/tivo-reports-results-for-the-first-quarter-ended-april-30-2014-nasdaq-tivo-1117762                   |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           4 | tivo.com            | http://pr.tivo.com/press-releases/tivo-reports-results-for-the-fourth-quarter-and-fi-nasdaq-tivo-990020                                |          0 |           0 |           0 |           2 |           0 |          0 |          0 |          0 |          0 |
|           1 | tivo.com            | http://pr.tivo.com/press-releases/tivo-s-annual-super-bowl-commercial-report-taco-b-nasdaq-tivo-981501                                 |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           4 | tivo.com            | http://pr.tivo.com/press-releases/tivo-to-acquire-leading-platform-for-improving-tv--nasdaq-tivo-0909872                               |          0 |           0 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
|           2 | viatech.com         | http://www.viatech.com/en/december-sales-2002/                                                                                         |          0 |           1 |           0 |           0 |           0 |          0 |          0 |          0 |          0 |
+-------------+---------------------+----------------------------------------------------------------------------------------------------------------------------------------+------------+-------------+-------------+-------------+-------------+------------+------------+------------+------------+
44 rows in set (4.10 sec)


mysql> select domain, url, num_patents from cc_pages where domain in (select domain from vpm30) and url like "%patent%";
+---------------------+-----------------------------------------------------------------------+-------------+
| domain              | url                                                                   | num_patents |
+---------------------+-----------------------------------------------------------------------+-------------+
| blackberry.com      | http://us.blackberry.com/legal/blackberry-virtual-patent-marking.html |        1628 |
| rapiscansystems.com | http://www.rapiscansystems.com/en/company/virtual_patent_marking      |          44 |
+---------------------+-----------------------------------------------------------------------+-------------+
2 rows in set (4.09 sec)



mysql> select * from cc_patents order by instances desc limit 10;
+---------------+-----------+
| patent_number | instances |
+---------------+-----------+
| 7973796       |    691892 |
| 7752060       |    495083 |
| 8719052       |    495074 |
| 7647322       |    198044 |
| 6862596       |    198030 |
| 1000000       |    111795 |
| 8577723       |     88932 |
| 7231405       |     72324 |
| 6624843       |     71649 |
| 6845871       |     50528 |
+---------------+-----------+
10 rows in set (0.00 sec)


mysql> select avg(instances) from cc_patents;
12.8


mysql> select median(instances) from cc_patents;
ERROR 1305 (42000): FUNCTION david.median does not exist


mysql> create table temp as select PUBLN_NR, count(*) as num_authors from patstat_us_multiple_authors_per_patent group by PUBLN_NR;
mysql> select num_authors, count(*) from temp group by num_authors;
+-------------+----------+
| num_authors | count(*) |
+-------------+----------+
|           1 |  8706568 |
|           2 |   370115 |
|           3 |    51351 |
|           4 |    17619 |
|           5 |     7104 |
|           6 |     3298 |
|           7 |     1473 |
|           8 |      769 |
...

# 95% of patentes have only one author
mysql>  select (select count(*) from temp where num_authors = 1) / (select count(*) from temp);
0.9506


mysql> select * from patstat_us_one_author_per_patent limit 10;
+----------+------------+------------+------------------+--------------------------------------+----------------------------+------------------------------+
| PUBLN_NR | PUBLN_KIND | PUBLN_DATE | PERSON_CTRY_CODE | PERSON_NAME                          | DOC_STD_NAME               | HAN_NAME                     |
+----------+------------+------------+------------------+--------------------------------------+----------------------------+------------------------------+
| D584876  | S1         | 2009-01-20 | US               | Victory Management Group, LLC        | VICTORY MAN GROUP LLC      | VICTORY MANAGEMENT GROUP LLC |
| D584877  | S1         | 2009-01-20 | US               | Mars, Incorporated                   | MARS INC                   | MARS INC                     |
| D584878  | S1         | 2009-01-20 | US               | Blume Forever, LLC                   | BLUME FOREVER LLC          | BLUME FOREVER LLC            |
| D584881  | S1         | 2009-01-20 | US               | Nike, Inc.                           | NIKE INC                   | NIKE INC                     |
| D584883  | S1         | 2009-01-20 | LU               | Laridel Participations S.A.          | LARIDEL PARTICIPATIONS S A | LARIDEL PARTICIPATIONS SA    |
| D584884  | S1         | 2009-01-20 | US               | Peeerfect Fit, LLC                   | PEEERFECT FIT LLC          | PEEERFECT FIT LLC            |
| D584885  | S1         | 2009-01-20 | US               | BioWorld Merchandising, Incorporated | BIOWORLD MERCHANDISING INC | BIOWORLD MERCHANDISING INC   |
| D584886  | S1         | 2009-01-20 | US               | Columbia Insurance Company           | COLUMBIA INSURANCE CO      | COLUMBIA INSURANCE CO        |
| D584887  | S1         | 2009-01-20 | US               | Deckers Outdoor Corporation          | DECKERS OUTDOOR CORP       | DECKERS OUTDOOR CORP         |
| D584888  | S1         | 2009-01-20 | GB               | J. Choo Limited                      | HOLLOWAY SIMON             | J CHOO LTD                   |
+----------+------------+------------+------------------+--------------------------------------+----------------------------+------------------------------+
10 rows in set (0.00 sec)


mysql> select count(*) from patstat_us_one_author_per_patent;
9'162'696


mysql> select count(*) from patstat_us_multiple_authors_per_patent;
9'753'825


mysql> select count(*) from cc_patents;
5'562'091


mysql> select count(*) from cc_patents A left join patstat_us_one_author_per_patent B ON (A.patent_number = B.PUBLN_NR) where B.person_name is not null limit 5;
4'432'191


mysql>
select
  B.HAN_NAME,
  A.domain,
  count(*) as num_patents
from cc_page_patents A left join patstat_us_one_author_per_patent B ON (A.patent_number = B.PUBLN_NR)
where domain in (select domain from vpm30)
  and B.HAN_NAME is not null
group by B.HAN_NAME, A.domain
order by A.domain, num_patents desc;

+------------------------------------------------------+---------------------+-------------+
| HAN_NAME                                             | domain              | num_patents |
+------------------------------------------------------+---------------------+-------------+
| RESEARCH IN MOTION                                   | blackberry.com      |         461 |
| BLACKBERRY LTD                                       | blackberry.com      |         212 |
| PARATEK MICROWAVE INC                                | blackberry.com      |         201 |
| CERTICOM                                             | blackberry.com      |         141 |
| MITSUBISHI DENKI KABUSHIKI KAISHA                    | blackberry.com      |         105 |
| RESEARCH IN MOTION RF INC                            | blackberry.com      |          80 |
| VISTO CORP                                           | blackberry.com      |          34 |
| QNX SOFTWARE SYSTEMS                                 | blackberry.com      |          23 |
| NORTEL NETWORK LTD                                   | blackberry.com      |          19 |
| GOOD TECH INC                                        | blackberry.com      |          19 |
| TELEFON AB LM                                        | blackberry.com      |          18 |
| GENNUM CORP                                          | blackberry.com      |          16 |
| ATHOC INC                                            | blackberry.com      |          15 |
| GOOD TECH CORP                                       | blackberry.com      |          14 |
| PITNEY BOWES INC                                     | blackberry.com      |          11 |
| QNX SOFTWARE SYTEMS CO                               | blackberry.com      |          11 |
| RES IN MOTION                                        | blackberry.com      |           9 |
| STACK LTD M                                          | blackberry.com      |           9 |
| BlackBerrry Limited                                  | blackberry.com      |           8 |
| NOKIA LTD                                            | blackberry.com      |           8 |
| GOOD TECH SOFTWARE INC                               | blackberry.com      |           6 |
| WRIGHT STRATEGIES INC                                | blackberry.com      |           6 |
| Miasnik, Guy                                         | blackberry.com      |           6 |
| BlackBerry Limited                                   | blackberry.com      |           4 |
| INTELLECTUAL PROPERTY                                | blackberry.com      |           4 |
| SILICON VALLEY BANK                                  | blackberry.com      |           4 |
| ROAMPAGE INC                                         | blackberry.com      |           4 |
| COPIUN INC                                           | blackberry.com      |           3 |
| GOOD TECH LLC                                        | blackberry.com      |           2 |
| MITSUBISHI ELECT CO                                  | blackberry.com      |           1 |
| Research In Motion Limited                           | blackberry.com      |           1 |
| 8758271 Canada Inc.                                  | blackberry.com      |           1 |
| 2236008 ONTARIO INC                                  | blackberry.com      |           1 |
| Taylor, Norrie                                       | blackberry.com      |           1 |
| QNX Software Systems Limited                         | blackberry.com      |           1 |
| QNX SOFTWARE SYSTEMS WAVEMAKERS INC                  | blackberry.com      |           1 |
| JARNA INC                                            | blackberry.com      |           1 |
| EDWARD G. BUDD MANUFACTURING COMPANY                 | blackberry.com      |           1 |
| UNITED STATES AMERICA                                | blackberry.com      |           1 |
| NORTH AMERICAN PHILIPS COMPANY, INC.                 | callawaygolf.com    |           1 |
| BECTON DICKINSON&CO                                  | callawaygolf.com    |           1 |
| DAVIS INSTRUMENTS                                    | davisnet.com        |           4 |
| IRROMETER CO INC                                     | davisnet.com        |           2 |
| TRANSCONTECH CORP                                    | davisnet.com        |           2 |
| General Electric Company                             | davisnet.com        |           2 |
| PAN PACIFIC PLASTICS MANUFACTURING INC               | davisnet.com        |           2 |
| FLAGLE HARRY D                                       | dolby.com           |           2 |
| VIDEO WEST INC.                                      | dolby.com           |           2 |
| DOLBY LABORATORIES LICENSING                         | dolby.com           |           1 |
| LODGE WILLIAM H                                      | dolby.com           |           1 |
| UNIVERSITY OF FLORIDA                                | pg.com              |           2 |
| RAPISCAN SYSTEM INC                                  | rapiscansystems.com |          25 |
| RAPISCAN SECURITY PRODUCTS                           | rapiscansystems.com |          11 |
| RAPISCAN SECURITY PRODUCTS USA INC                   | rapiscansystems.com |           6 |
| RAPISCAN INC                                         | rapiscansystems.com |           2 |
| SHOZABURO AKI                                        | sjm.com             |           1 |
| PERTEC CORPORATION                                   | sunpower.com        |           6 |
| DAIMLER-BENZ AKTIENGESELLSCHAFT                      | sunpower.com        |           6 |
| PER HEIBERG                                          | sunpower.com        |           6 |
| GENERAL VEHICLE CO INC                               | sunpower.com        |           5 |
| INTERNATIONAL FIREPROOF PRODUCTS CORPORATION         | sunpower.com        |           4 |
| American Cyanamid Company                            | sunpower.com        |           3 |
| C. BLAND JAMISON                                     | sunpower.com        |           3 |
| TELEGRAVURE COMPANY                                  | sunpower.com        |           3 |
| WALTER ATTWOOD                                       | sunpower.com        |           3 |
| A.-V. BURNER COMPANY                                 | sunpower.com        |           2 |
| SOUTHERN CALIFORNIA EDISON COMPANY                   | sunpower.com        |           2 |
| FINCH JR JAMES M                                     | sunpower.com        |           2 |
| JOHNSTON THOMAS M                                    | sunpower.com        |           2 |
| NAVY,US                                              | sunpower.com        |           2 |
| MCKENNEY GEORGE F                                    | sunpower.com        |           2 |
| CONTINENTAL OIL COMPANY                              | sunpower.com        |           2 |
| CLEVITE CORPORATION                                  | sunpower.com        |           2 |
| ROSALIND G. KAMLET                                   | sunpower.com        |           2 |
| THE REECE BUTTON HOLE MACHINE COMPANY                | sunpower.com        |           2 |
| HERCULES POWDER COMPANY                              | sunpower.com        |           2 |
| CONTINENTAL MOTORS CORPORATION                       | sunpower.com        |           2 |
| PREMOSHIS ROBERT T                                   | sunpower.com        |           2 |
| RESEARCH CORPORATION                                 | sunpower.com        |           2 |
| ROCKWELL-STANDARD CORPORATION                        | sunpower.com        |           2 |
| Honeywell Inc.                                       | sunpower.com        |           2 |
| EDMUNDS & JONES MFG CO                               | sunpower.com        |           2 |
| ACRA PLANT INC                                       | sunpower.com        |           2 |
| KING & HAMILTON CO                                   | sunpower.com        |           1 |
| BARNETTE TREW GEORGE                                 | sunpower.com        |           1 |
| WESTINGHOUSE LAMP COMPANY                            | sunpower.com        |           1 |
| NEPTUNE METER COMPANY                                | sunpower.com        |           1 |
| GUEST WILLIAM W                                      | sunpower.com        |           1 |
| Briggs & Stratton Corporation                        | sunpower.com        |           1 |
| THE SHARPLES CORPORATION                             | sunpower.com        |           1 |
| ERNEST W. FARR                                       | sunpower.com        |           1 |
| KELLEY KNIGHT CHARLES                                | sunpower.com        |           1 |
| DUNLOP RUBBER COMPANY, LTD.                          | sunpower.com        |           1 |
| THE CINCINNATI SHAPER COMPANY                        | sunpower.com        |           1 |
| HEINTZ MANUFACTURING COMPANY                         | sunpower.com        |           1 |
| O'BRIEN JOSEPH A                                     | sunpower.com        |           1 |
| WOOD ROBERT F                                        | sunpower.com        |           1 |
| STANDARD OIL COMPANY                                 | sunpower.com        |           1 |
| L. BLAKE-SMITH                                       | sunpower.com        |           1 |
| H C WHITE CO                                         | sunpower.com        |           1 |
| SHINKAWA K                                           | sunpower.com        |           1 |
| DAILEY OWEN R                                        | sunpower.com        |           1 |
| THE OHIO TRUSS CO.                                   | sunpower.com        |           1 |
| SCHILLING JR FREDERICK A                             | sunpower.com        |           1 |
| WESTINGHOUSE EL CO                                   | sunpower.com        |           1 |
| THE SPERRY CORPORATION                               | sunpower.com        |           1 |
| NAIONAL CASH REGISTER CO                             | sunpower.com        |           1 |
| CRAWFORD RAYMOND F                                   | sunpower.com        |           1 |
| GADDESS HARRY W                                      | sunpower.com        |           1 |
| MOFFATS LIMITED                                      | sunpower.com        |           1 |
| THE KELLEY-KOETT MANUFACTURING COMPANY, INCORPORATED | sunpower.com        |           1 |
| SIMPSON THOMAS H                                     | sunpower.com        |           1 |
| PETRY NICHOLAS A                                     | sunpower.com        |           1 |
| ZONE LABS INC                                        | symantec.com        |           1 |
| NICK GORGICHUK                                       | tivo.com            |           6 |
| FERRANTI LIMITED                                     | tivo.com            |           6 |
| THE FIRM GEBRUDER HARDY                              | tivo.com            |           5 |
| TIVO INC                                             | tivo.com            |           5 |
| TRA INC                                              | tivo.com            |           4 |
| C. H. HALSEY                                         | tivo.com            |           3 |
| ASTORIA SILK WORKS                                   | tivo.com            |           2 |
| FUJITSU LTD                                          | tivo.com            |           2 |
| AMERICAN BANKERS SAFETY CO                           | tivo.com            |           1 |
| PILKINGTON DEUTSCHLAND GMBH                          | tivo.com            |           1 |
| OLIN MATHIESON CHEMICAL CORPORATION                  | tivo.com            |           1 |
| GENERAL STEEL CASTINGS CORPORATION                   | viatech.com         |           1 |
| KEATING HERBERT J                                    | viatech.com         |           1 |
+------------------------------------------------------+---------------------+-------------+
127 rows in set (1 min 46.81 sec)


mysql> select * from DomainStats where domain in (select domain from vpm30) order by domain;
+---------------------+---------------------------+--------------------+-----------------------------+---------------------+--------------------+--------------------+--------------------+--------------------+-------------------+-------------------------+
| domain              | numDistinctMatchedPatents | numDistinctPatents | distinctMatchedPatentsRatio | numDistinctHanNames | shareTop1          | shareTop2          | shareTop3          | shareTop4          | shareTop5         | hanNamesHerfindhalIndex |
+---------------------+---------------------------+--------------------+-----------------------------+---------------------+--------------------+--------------------+--------------------+--------------------+-------------------+-------------------------+
| blackberry.com      |                       333 |                377 |                 0.883289124 |                  39 |   0.21021021021021 |  0.363363363363363 |  0.498498498498498 |   0.57957957957958 | 0.648648648648649 |       0.107558008458909 |
| callawaygolf.com    |                         2 |                  3 |                 0.666666666 |                   2 |                0.5 |                  1 |                  1 |                  1 |                 1 |                     0.5 |
| davisnet.com        |                         6 |                 13 |                 0.461538461 |                   5 |  0.333333333333333 |                0.5 |  0.666666666666667 |  0.833333333333333 |                 1 |       0.222222222222222 |
| dolby.com           |                         4 |                  4 |                           1 |                   4 |               0.25 |                0.5 |               0.75 |                  1 |                 1 |                    0.25 |
| pg.com              |                         1 |                  1 |                           1 |                   1 |                  1 |                  1 |                  1 |                  1 |                 1 |                       1 |
| rapiscansystems.com |                        34 |                 34 |                           1 |                   4 |  0.647058823529412 |  0.852941176470588 |  0.941176470588235 |                  1 |                 1 |       0.472318339100346 |
| sjm.com             |                         1 |                  1 |                           1 |                   1 |                  1 |                  1 |                  1 |                  1 |                 1 |                       1 |
| sunpower.com        |                        57 |                 86 |                 0.662790697 |                  57 | 0.0175438596491228 | 0.0350877192982456 | 0.0526315789473684 | 0.0701754385964912 | 0.087719298245614 |      0.0175438596491228 |
| symantec.com        |                         1 |                  1 |                           1 |                   1 |                  1 |                  1 |                  1 |                  1 |                 1 |                       1 |
| tivo.com            |                        19 |                 37 |                 0.513513513 |                  14 |  0.210526315789474 |  0.368421052631579 |  0.421052631578947 |  0.473684210526316 | 0.526315789473684 |       0.102493074792244 |
| viatech.com         |                         2 |                  2 |                           1 |                   2 |                0.5 |                  1 |                  1 |                  1 |                 1 |                     0.5 |
+---------------------+---------------------------+--------------------+-----------------------------+---------------------+--------------------+--------------------+--------------------+--------------------+-------------------+-------------------------+
11 rows in set (0.06 sec)


mysql> select * from DomainStats where domain like "%google.com%" or numDistinctPatents > 10000 order by domain;
+--------------------------+---------------------------+--------------------+-----------------------------+---------------------+---------------------+--------------------+--------------------+--------------------+--------------------+-------------------------+
| domain                   | numDistinctMatchedPatents | numDistinctPatents | distinctMatchedPatentsRatio | numDistinctHanNames | shareTop1           | shareTop2          | shareTop3          | shareTop4          | shareTop5          | hanNamesHerfindhalIndex |
+--------------------------+---------------------------+--------------------+-----------------------------+---------------------+---------------------+--------------------+--------------------+--------------------+--------------------+-------------------------+
| 9to5google.com           |                        12 |                 14 |                 0.857142857 |                   6 |                0.25 |  0.416666666666667 |  0.583333333333333 |               0.75 |  0.916666666666667 |       0.180555555555556 |
| archive.org              |                    305841 |             402422 |                 0.760000695 |              119324 |  0.0100248168165812 | 0.0199678918130663 | 0.0259677414081173 | 0.0309801498164079 | 0.0356721302899219 |    0.000597597792921839 |
| baogiaquangcaogoogle.com |                         5 |                  9 |                 0.555555555 |                   5 |                 0.2 |                0.4 |                0.6 |                0.8 |                  1 |                     0.2 |
| getfilings.com           |                     50141 |              69516 |                 0.721287185 |               28604 | 0.00833649109511178 | 0.0152170878123691 | 0.0197642647733392 | 0.0239325103208951 | 0.0277417682136375 |    0.000457474207451585 |
| godlikeproductions.com   |                    252032 |             344954 |                 0.730624952 |              116502 |  0.0160614525139665 | 0.0228502729812087 | 0.0295875126968004 | 0.0361343004062976 | 0.0416494730827831 |    0.000719474735053808 |
| google.ca                |                    921512 |            1103815 |                 0.834842795 |              124983 |  0.0308948771150023 | 0.0445355025219422 | 0.0554187031747823 | 0.0649302450754846 | 0.0733121218171874 |     0.00240132027542769 |
| google.cl                |                     86452 |             101300 |                 0.853425468 |               13552 |   0.086822745569796 |  0.130546430389118 |  0.149944477860547 |  0.164588442141304 |  0.178977929949567 |      0.0119869105800541 |
| google.com.ar            |                       621 |                770 |                 0.806493506 |                 337 |  0.0338164251207729 | 0.0531400966183575 |   0.07085346215781 | 0.0885668276972625 |  0.104669887278583 |     0.00670312544568655 |
| google.com.br            |                      1023 |               1254 |                 0.815789473 |                 400 |   0.143695014662757 |   0.20039100684262 |  0.242424242424242 |  0.280547409579668 |  0.305962854349951 |      0.0311916822180749 |
| google.com.do            |                         3 |                  3 |                           1 |                   3 |   0.333333333333333 |  0.666666666666667 |                  1 |                  1 |                  1 |       0.333333333333333 |
| google.com.et            |                         3 |                  6 |                         0.5 |                   2 |   0.666666666666667 |                  1 |                  1 |                  1 |                  1 |       0.555555555555556 |
| google.com.hk            |                      2805 |               3362 |                 0.834324806 |                 773 |  0.0766488413547237 |  0.148663101604278 |  0.180748663101604 |  0.210695187165775 |  0.238502673796791 |      0.0172194419819463 |
| google.com.lb            |                         7 |                 21 |                 0.333333333 |                   7 |   0.142857142857143 |  0.285714285714286 |  0.428571428571429 |  0.571428571428571 |  0.714285714285714 |       0.142857142857143 |
| google.com.pa            |                        33 |                 41 |                 0.804878048 |                   7 |   0.696969696969697 |  0.787878787878788 |  0.848484848484849 |  0.909090909090909 |  0.939393939393939 |       0.504132231404959 |
| google.com.ph            |                        20 |                 32 |                       0.625 |                  17 |                0.15 |               0.25 |                0.3 |               0.35 |                0.4 |                    0.07 |
| google.com.py            |                       100 |                113 |                 0.884955752 |                  50 |                0.14 |               0.25 |               0.32 |               0.38 |               0.43 |      0.0516000000000001 |
| google.com.sg            |                       117 |                135 |                 0.866666666 |                  86 |   0.111111111111111 |  0.162393162393162 |  0.188034188034188 |  0.213675213675214 |  0.230769230769231 |      0.0244722039593834 |
| google.com.tr            |                       446 |                510 |                 0.874509803 |                 252 |  0.0493273542600897 | 0.0874439461883408 |  0.116591928251121 |  0.141255605381166 |  0.161434977578475 |      0.0100846588509722 |
| google.com.tw            |                       317 |                370 |                 0.856756756 |                  73 |   0.135646687697161 |  0.255520504731861 |  0.334384858044164 |  0.413249211356467 |  0.466876971608833 |      0.0575087820557473 |
| google.com.ua            |                        46 |                 51 |                 0.901960784 |                  34 |   0.108695652173913 |  0.195652173913043 |  0.260869565217391 |  0.304347826086957 |  0.347826086956522 |      0.0425330812854442 |
| google.com.uy            |                        18 |                 19 |                 0.947368421 |                  15 |   0.166666666666667 |  0.277777777777778 |  0.333333333333333 |  0.388888888888889 |  0.444444444444444 |      0.0802469135802469 |
| google.com.vn            |                        23 |                 34 |                 0.676470588 |                  21 |  0.0869565217391304 |  0.173913043478261 |  0.217391304347826 |  0.260869565217391 |  0.304347826086957 |      0.0510396975425331 |
| google.de                |                    356541 |             418399 |                 0.852155478 |               50665 |  0.0357266064772354 | 0.0516883051318081 | 0.0640936105525031 | 0.0736324854645048 | 0.0826104150714785 |      0.0029997050613186 |
| google.es                |                   1401995 |            1681057 |                  0.83399611 |              189082 |  0.0254530151676718 | 0.0348867150025499 | 0.0440743369270219 | 0.0527847816860973 | 0.0603033534356399 |     0.00176914786825138 |
| google.fr                |                   1097308 |            1296187 |                  0.84656612 |              137875 |  0.0322762615418825 | 0.0446237519456707 | 0.0558922380954117 | 0.0659878539115727 | 0.0753334524126316 |     0.00255723530780544 |
| google.se                |                     35251 |              42082 |                 0.837674064 |                7513 |  0.0269779580721114 | 0.0517715809480582 | 0.0707781339536467 |  0.085813168420754 | 0.0959689086834416 |     0.00339681064047383 |
| hellogoogle.com          |                         1 |                  1 |                           1 |                   1 |                   1 |                  1 |                  1 |                  1 |                  1 |                       1 |
| justia.com               |                     12238 |              16412 |                 0.745673897 |                8054 | 0.00768099362640954 |  0.014218009478673 | 0.0201013237457101 | 0.0254126491256741 | 0.0305605491093316 |    0.000632682499281795 |
| newswire.ca              |                      8716 |              12134 |                  0.71831218 |                6250 | 0.00849013308857274 | 0.0167508031206976 | 0.0219137218907756 | 0.0267324460761817 |  0.031206975676916 |    0.000615122110361048 |
| patentgenius.com         |                     40311 |              47760 |                 0.844032663 |               13519 |  0.0664086725707623 | 0.0843690307856416 | 0.0985338989357744 |  0.109920369130014 |    0.1202897472154 |     0.00595736103471568 |
| patents.com              |                    119390 |             141196 |                 0.845562197 |               34299 |  0.0229751235446855 | 0.0316190635731636 | 0.0398525839685066 | 0.0472987687411006 | 0.0544350448111232 |     0.00161975201284343 |
| patentsencyclopedia.com  |                    224798 |             259517 |                 0.866216856 |               49408 |  0.0152625913041931 | 0.0254984474950845 |  0.033318801768699 | 0.0401427058959599 | 0.0467797756207795 |      0.0011230028867034 |
| scribd.com               |                     36049 |              49598 |                 0.726823662 |               20520 | 0.00837748619933979 | 0.0152015312491331 | 0.0200005548004106 | 0.0247718383311604 | 0.0293766817387445 |    0.000546067255874413 |
| sec.gov                  |                     66106 |              92476 |                 0.714844932 |               36065 | 0.00881916921308202 | 0.0156264181768675 | 0.0205125102108734 | 0.0243245696305933 |  0.028045865730796 |    0.000453758474058318 |
| state.ny.us              |                      7979 |              11251 |                 0.709181406 |                5639 |  0.0105276350419852 | 0.0172954004261186 | 0.0239378368216568 | 0.0292016543426495 | 0.0338388269206668 |    0.000683756887145387 |
| ufl.edu                  |                      9573 |              13151 |                 0.727929435 |                6789 |  0.0110728089418155 | 0.0197430272641805 | 0.0284132455865455 | 0.0333228872871618 | 0.0375013057557714 |    0.000704226973541647 |
| uspto.gov                |                   1459680 |            1836154 |                 0.794965999 |              264462 |   0.020246903430889 | 0.0321714348350323 |   0.04021018305382 | 0.0480495725090431 | 0.0557156363038474 |     0.00160509615568108 |
| wordpress.com            |                     13287 |              17769 |                 0.747762957 |                9177 |  0.0123428915481298 | 0.0200195679987958 | 0.0252126138330699 | 0.0299540904643637 | 0.0346955670956574 |       0.000621888993809 |
+--------------------------+---------------------------+--------------------+-----------------------------+---------------------+---------------------+--------------------+--------------------+--------------------+--------------------+-------------------------+
38 rows in set (0.02 sec)
```


## TrainClassifierAndPredict
```
$ sbt -Dspark.master=local[*] "runMain iproduct.classifier1.TrainClassifierAndPredict $DBURL /tmp/pages_prediction.txt"

Some example classification rules:

shareTop1 <= 0.25 & text_vpm_r1 > 1.0
shareTop1 > 0.25 & numPages <= 98.0 & patents > 0.0
shareTop1 > 0.25 & numPages > 98.0 & applications <= 0.0 & numWords > 1216.0
shareTop1 > 0.25 & numPages > 98.0 & applications > 0.0 & numWords <= 3762.0

example VPM pages found:
http://us.cricut.com/home/legal/patents
http://7search.com/legal/patents.htm
http://avairefloors.com/patents.aspx
http://brine.com/patents/
http://corehandf.com/patents/
http://devicepatents.com/
http://ecoupled.com/en/wireless-power/patents
http://gidynamics.com/patents.php
```

However there are still many false positives.
Other techniques are being investigated.
