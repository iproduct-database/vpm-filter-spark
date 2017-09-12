# testing https://github.com/scrapinghub/mdr
# package mdr imported and slightly modified

from __future__ import absolute_import
from __future__ import print_function

import mdr_extract
import re
from pathlib2 import Path

from mdr import MDR

# text = requests.get('http://www.yelp.co.uk/biz/the-ledbury-london').text.encode('utf8')
# text = requests.get('https://www.tivo.com/legal/patents').text.encode('utf8')
# text = Path('the-ledbury-london.html').read_text(encoding='utf8')
# text = Path('/tmp/mdr/tests/samples/htmlpage0.html').read_text(encoding='utf8')
# text = Path('data/data.html').read_text(encoding='utf8')
text = Path('/Users/david/Dropbox/IPRoduct/vpm-filter-spark/data/data.html').read_text(encoding='utf8')

# text = requests.get('https://myboogieboard.com/patents').text.encode('utf8')

mdr = MDR()
doc = mdr.parseHtml(text)
candidates = MDR().list_candidates(doc)
for p in [doc.getpath(c) for c in candidates]: print(p)

results = list(mdr_extract.extractResultsFromRoots(doc, candidates))

r1 = "(US)\\d[,.']?\\d\\d\\d[,.']?\\d\\d\\d"
r2 = "\\d[,.']\\d\\d\\d[,.']\\d\\d\\d"
r = re.compile("(?<![^ :])((" + r1 + ")|(" + r2 + "))(?=[,.';:()]?(?![^ ]))")

def textContainsPatent(text):
    return len(r.findall(text)) > 0

results = [result for result in results if mdr_extract.someXmlTextInResultSatisfies(result, textContainsPatent)]

for result in results:
    mdr_extract.printResult(result)

for result in results:
    mdr_extract.printData(mdr_extract.extractDataFromResult(result))

print("+++ END")


#output:
#+++ data
#[
#    {
#        "h1": "title1",
#        "table": {
#            "product": {
#                "1": {
#                    "name": "p1",
#                    "price": "$10",
#                    "quantity": "111"
#                },
#                "2": {
#                    "name": "p2",
#                    "price": "$20"
#                },
#                "3": {
#                    "name": "p3",
#                    "price": "$30",
#                    "quantity": "333"
#                }
#            }
#        }
#    },
#    {
#        "h1": "title2",
#        "table": {
#            "product": {
#                "1": {
#                    "name": "p1",
#                    "text": "patents: 7,351,506 "
#                },
#                "2": {
#                    "name": "p2",
#                    "text": "patents: 7,351,507; 7,351,509"
#                },
#                "3": {
#                    "name": "p3",
#                    "text": "patents: 7,351,5019"
#                }
#            }
#        }
#    }
#]
#
#+++ data
#[
#    {
#        "product": {
#            "name": "p1",
#            "text": "patents: 7,351,506 "
#        }
#    },
#    {
#        "product": {
#            "name": "p2",
#            "text": "patents: 7,351,507; 7,351,509"
#        }
#    },
#    {
#        "product": {
#            "name": "p3",
#            "text": "patents: 7,351,5019"
#        }
#    }
#]
