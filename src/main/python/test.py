import requests
from pathlib2 import Path

from mdr import MDR

#text = Path('/Users/david/Dropbox/IPRoduct/vpm-filter-spark/data/the-ledbury-london.html').read_text(encoding='utf8')
text = Path('/Users/david/Dropbox/IPRoduct/vpm-filter-spark/data/data.html').read_text(encoding='utf8')

#text = requests.get('http://www.ipc.com/legal/patents/').text.encode('utf8')
#text = requests.get('https://www.tivo.com/legal/patents').text.encode('utf8')

text = requests.get('http://www.rmspumptools.com/innovation.php').text.encode('utf8')


mdr = MDR()
doc = mdr.parseHtml(text)
##print(etree.tostring(doc, pretty_print=False, encoding="unicode", doctype=""))
candidates = mdr.list_candidates(doc)
print([doc.getpath(c) for c in candidates])

#print(doc.getpath(candidates[8]))
#print(mdr.extract(candidates[8]))
#print(mdr.extract(doc.xpath("/html/body")[0]))

#import traceback
#import time
#for c in candidates:
#    print("+++ root: " + doc.getpath(c) + ": " + str(len(c)))
#    try:
#        print(mdr.extract(c))
#    except:
#        print("exception on " + doc.getpath(c) + ": " + str(len(c)))
#        traceback.print_exc()
#    #time.sleep(5)




#print([mdr.extract(c) for c in candidates])

print("+++ END")
import utils
#seedRecord, recordMappingDict = utils.mdrExtract(doc, "/html/body/div[2]")
#seedRecord, recordMappingDict = utils.mdrExtract(doc, "/html/body")
seedRecord, recordMappingDict = utils.mdrExtract(doc, doc.getpath(candidates[0]))

print(seedRecord)
print(recordMappingDict)


