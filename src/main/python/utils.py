import six
from lxml import etree

from mdr import MDR

mdr = MDR()

def xmlToString(xml):
    return etree.tostring(xml, pretty_print=False, encoding="unicode", doctype="")

def elementFromXpath(doc, path):
    nodes = doc.xpath(path)
    assert(len(nodes) == 1 and isinstance(nodes[0], etree._Element))
    return nodes[0]

def removeEmptyMappingEntries(mapping):
    return {s: t for s, t in six.iteritems(mapping) if s is not None and t is not None}

def mdrExtract_(root):
    seedRecord, recordMappingDict = mdr.extract(root)
    if seedRecord is None:
        return None, None
    if recordMappingDict is None:
        return None, None

    recordMappingDict = {k: removeEmptyMappingEntries(v) for k, v in six.iteritems(recordMappingDict) if k is not None and v is not None}
    recordMappingDict = {k: v for k, v in six.iteritems(recordMappingDict) if len(v) > 0}

    if recordMappingDict is None:
        return None, None

    return seedRecord, recordMappingDict

def seedRecordToScala(seedRecord):
    seed = etree.Element("seed")
    for element in seedRecord: seed.append(element)
    return xmlToString(seed)

def recordToScala(doc, record):
    return [doc.getpath(element) for element in record]

def mappingToScala(doc, seedRecord, mapping):
    return {seedRecord[0].getroottree().getpath(s): doc.getpath(t) for s, t in six.iteritems(mapping)}

def recordMappingDictToScala(doc, seedRecord, recordMappingDict):
    return [(recordToScala(doc, record), mappingToScala(doc, seedRecord, mapping)) for record, mapping in six.iteritems(recordMappingDict)]

def mdrExtract(doc, rootPath):
    root = elementFromXpath(doc, rootPath)
    seedRecord, recordMappingDict = mdrExtract_(root)
    if seedRecord is None: return None, None
    return seedRecordToScala(seedRecord), recordMappingDictToScala(doc, seedRecord, recordMappingDict)
