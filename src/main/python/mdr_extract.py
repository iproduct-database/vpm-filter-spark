# testing https://github.com/scrapinghub/mdr
# package mdr imported and slightly modified

# results: List[Result]
# Result:
#     doc
#     root
#     seedRecord
#     recordMappingDict: Dict[Record -> Mapping]
# Mapping: Dict[Elem -> Elem]


from __future__ import absolute_import
from __future__ import print_function

import json
import mdr.utils
import re
import six
import sys
from lxml import etree

from mdr import MDR

mdr = MDR()

def xmlToString(tree):
    return etree.tostring(tree, pretty_print=True, encoding="unicode").replace("\n", " ")

def recordToString(record, indent):
    return ('\n' + indent*' ').join([xmlToString(tree) for tree in record])

def removeEmptyMappingEntries(mapping):
    return {s: t for s, t in six.iteritems(mapping) if s is not None and t is not None}

def extractResultsFromRoot(doc, root):
    seedRecord, recordMappingDict = mdr.extract(root)
    if seedRecord is None:
        return None
    if recordMappingDict is None:
        return None

    recordMappingDict = {k: removeEmptyMappingEntries(v) for k, v in six.iteritems(recordMappingDict) if k is not None and v is not None}
    recordMappingDict = {k: v for k, v in six.iteritems(recordMappingDict) if len(v) > 0}

    if recordMappingDict is None:
        return None

    return {'doc': doc, 'root': root, 'seedRecord': seedRecord, 'recordMappingDict': recordMappingDict}

def extractResultsFromRoots(doc, roots):
    for root in roots:
        try:
            result = extractResultsFromRoot(doc, root)
            if (result is not None):
                yield result
        except:
            print("Unexpected error for " + doc.getpath(root) + ": " + str(sys.exc_info()))
    #        raise
            pass

def printResult(result):
    print("+++ root: " + result['doc'].getpath(result['root']))
    print("   +++ seedRecord: " + recordToString(result['seedRecord'], 20))
    for record, mapping in six.iteritems(result['recordMappingDict']):
        print("      +++ record: " + recordToString(record, 18))
        for s, t in six.iteritems(mapping):
            print("          +++ mapping from: " + xmlToString(s) + "\n                        to: " + xmlToString(t))

#def normalizeResult(result):
#    return set(itertools.chain(*[mapping.keys() for record, mapping in result['recordMappingDict'].iteritems()]))

# Note1: We could use the seed_record to extract the data in order
# Note2: In a given tree level, we are merging nodes with the same name, even if their subtrees might be different.
def extractDataFromMapping(mapping):
    data = {}
    for s, t in six.iteritems(mapping):
        if len(t) == 0:
            merge(data, dataToNestedDict(s.getroottree().getpath(s), t.text))
    return data

# key=/table/product[2]/name, text="product2" -> {table: {product: {2: {name: product2}}}
def dataToNestedDict(path, text):
    assert(path[0] == '/')
    path = re.sub("\[([0-9]+)\]", "/\\1", path)
    assert(not bool(re.search('[[\\]]', path)))
    dict = {}
    nestedDict = dict
    localPaths = path[1:].split("/")
    for localPath in localPaths[0:-1]:
        nestedDict[localPath] = {}
        nestedDict = nestedDict[localPath]
    nestedDict[localPaths[-1]] = text
    return dict

def merge(d1, d2):
    for k in d2:
        if k in d1 and isinstance(d1[k], dict) and isinstance(d2[k], dict):
            merge(d1[k], d2[k])
        else:
            d1[k] = d2[k]

def extractDataFromResult(result):
    return [extractDataFromMapping(mapping) for mapping in result['recordMappingDict'].values()]

def printData(data):
    print("+++ data")
    print(json.dumps(data, sort_keys=True, indent=4))
    print()

def someXmlTextSatisfies(xml, f):
    if xml.text is not None and f(xml.text):
        return True
    for c in xml:
        if (someXmlTextSatisfies(c, f)):
            return True
    return False

def someXmlTextInRecordSatisfies(record, f):
    for tree in record:
        if (someXmlTextSatisfies(tree, f)):
            return True
    return False

def someXmlTextInResultSatisfies(result, f):
    for xml in result['recordMappingDict'].keys():
        if (someXmlTextInRecordSatisfies(xml, f)):
            return True
    return False
