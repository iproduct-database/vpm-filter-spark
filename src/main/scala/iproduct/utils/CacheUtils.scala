package iproduct.utils

import java.io._

import scala.reflect.runtime.universe.{TypeTag, typeOf}

object CacheUtils {
  def name[T: TypeTag]: String =
    typeOf[T].typeSymbol.name.toString

  def write[T: TypeTag](file: File, v: T) {
    println(s"+++ write ${name[T]} to file $file. START.")
    val oos = new ObjectOutputStream(new FileOutputStream(file))
    oos.writeObject(v)
    oos.close()
    println(s"+++ write ${name[T]} to file $file. END.")
  }

  def read[T: TypeTag](file: File): T = {
    println(s"+++ read ${name[T]} from file $file. START.")
    val ois = new LocalObjectInputStream(new FileInputStream(file))
    val v = ois.readObject.asInstanceOf[T]
    ois.close()
    println(s"+++ read ${name[T]} from file $file. END.")
    v
  }

  // workaround when running on sbt: https://stackoverflow.com/a/12180547/280393
  class LocalObjectInputStream(val in: InputStream) extends ObjectInputStream(in) {
    override protected def resolveClass(desc: ObjectStreamClass): Class[_] = Class.forName(desc.getName, false, this.getClass.getClassLoader)
  }

  def cache[T: TypeTag](file: File, forceEvaluate: Boolean, v: => T): T = {
    if (!forceEvaluate && file.exists()) read(file)
    else {
      println(s"+++ cache ${name[T]} to file $file. computing.")
      write(file, v)
      v
    }
  }
}
