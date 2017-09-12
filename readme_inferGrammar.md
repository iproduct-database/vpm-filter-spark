# Infer Grammar

Some VPM pages use tables or other regular structures to describe the products and their patents.
The project contains some preliminary work also to automatically detect this structure based on context-free grammars.

Work based on [https://github.com/scrapinghub/mdr](https://github.com/scrapinghub/mdr).

```
$ wget --directory-prefix=/tmp/wget_files/ --warc-file=test http://www.ignitepatents.com/ http://www.muellerwaterproducts.com/patents
$ cdx-indexer test.warc.gz | sort > test.warc.cdx
$ PYTHONHOME=/Users/david/.virtualenvs/py3-data 
$ CDXFILE=test.warc.cdx 
$ sbt "runMain playground.inferGrammar.TestMDR http://www.ignitepatents.com/"

candidates: List(/html/body/main)

+++ test root: /html/body/main
interesting: false
compactNestedData, count: 10
seed/section
  div/
    [001]
      div/p/
        [001]/text: D581,727
        [002]/text: D592,905
        [003]/text: D592,913
        [004]/text: D643,249
        [005]/text: D656,360
        [006]/text: D682,034
        [007]/text: D700,012
        [008]/text: D700,802
        [009]/text: D724,385
        [010]/text: D724,896
        [011]/text: D725,436
        [012]/text: D740,605
        [013]/text: D748,943
        [014]/text: US 7,546,933
        [015]/text: US 7,997,442
        [016]/text: US 8,590,731
        [017]/text: US 8,602,238
        [018]/text: US 8,839,983
        [019]/text: US 8,863,979
        [020]/text: US 8,844,762
        [021]/text: US 9,095,233
        [022]/text: US 9,162,802
        [023]/text: US 9,398,823
        [024]/text: Patents Pending
      h4/text: Water Bottles
    [002]
      div/p/
        [001]/text: D721,920
        [002]/text: D720,962
        [003]/text: D722,826
        [004]/text: Patents Pending
      h4/text: Tumblers
    [003]
      div/p/
        [001]/text: D730,694
        [002]/text: D739,174
        [003]/text: D754,483
        [004]/text: Patents Pending
      h4/text: Shaker Bottles
    [004]
      div/p/
        [001]/text: D551,895
        [002]/text: D529,749
        [003]/text: D526,827
      h4/text: Sport Jugs
    [005]
      div/p/
        [001]/text: D730675
        [002]/text: Patents Pending
      h4/text: Other Hydration Products
  h3/text: Hydration Products


seed/text: <section class="langSwitcher"> <div class="langSel"> <p>Select Yoour Language: <a class="langChange" href="index.html" style="font-weight:bold;">English</a> / <a class="langChange" href="fr-CA.html">French</a> </div> </section>


seed/section
  div//[001]
    div/p//[001]/text: Patents Pending
    h4/text: Spirits Line
  h3/text: Spirits Line

seed/section
  div/
    [001]
      div/p/
        [001]/text: D490,654
        [002]/text: D500,231
        [003]/text: D500,428
        [004]/text: D507,458
        [005]/text: D509,408
        [006]/text: D524,107
        [007]/text: D557,075
        [008]/text: D564,841
        [009]/text: D651,855
        [010]/text: D656,787
        [011]/text: D657,246
        [012]/text: D657,247
        [013]/text: D693,629
        [014]/text: D693,630
        [015]/text: D656,787
        [016]/text: D675,873
        [017]/text: D696,073
        [018]/text: D699,509
        [019]/text: D732,337
        [020]/text: D742,684
        [021]/text: D669,737
        [022]/text: US 7,011,227
        [023]/text: US 7,306,113
        [024]/text: US 8,839,983
      h4/text: Mugs
    [002]
      div/p/
        [001]/text: D515,357
        [002]/text: Patents Pending
      h4/text: Thermal Bottles
    [003]/h4/text: Other Thermal Products
  h3/text: Thermal Products

seed/section
  div//[001]
    div/p/
      [001]/text: D581,727
      [002]/text: D592,456
      [003]/text: D700,012
      [004]/text: US 7,546,933
      [005]/text: US 7,997,442
      [006]/text: US 8,590,731
      [007]/text: US 8,602,238
      [008]/text: US 8,839,983
      [009]/text: US 8,863,979
      [010]/text: US 9,095,233
      [011]/text: US 9,162,802
      [012]/text: US 9,398,823
      [013]/text: Patents Pending
    h4/text: Kids Products
  h3/text: Kids Products

seed/section
  div//[001]/h4/text: Accessories
  h3/text: Accessories
+++ END
```
