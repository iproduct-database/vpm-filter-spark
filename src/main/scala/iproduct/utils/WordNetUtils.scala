package iproduct.utils

import java.net.URL

import edu.mit.jwi.Dictionary
import edu.mit.jwi.item.{ISynset, POS}
import edu.mit.jwi.morph.WordnetStemmer

import scala.collection.JavaConversions._

object WordNetUtils {
  val dict = new Dictionary(new URL("file:///Users/david/nltk_data/corpora/wordnet"))
  dict.open
  val wordnetStemmer = new WordnetStemmer(dict)

  def wordInDict(word: String): Boolean =
    POS.values.toList.exists(pos => wordnetStemmer.findStems(word, pos).nonEmpty)

  def getSynsets(word: String): List[ISynset] =
    dict.getIndexWord(word, POS.NOUN).getWordIDs.toList.map(wordID => dict.getWord(wordID).getSynset)

  def getSynsetsFromUnstemmedWord(word: String): List[ISynset] =
    POS.values.toList.flatMap { pos =>
      val stems = wordnetStemmer.findStems(word, pos)
      stems.flatMap(getSynsets)
    }

  def isNounPerson(synset: ISynset): Boolean =
    synset.getLexicalFile.getName == "noun.person"

  // word (or inflected form) exists in US dictionary, and it is not a person noun
  def isRegularWord(word: String): Boolean =
    !getSynsetsFromUnstemmedWord(word).forall(isNounPerson)
}
