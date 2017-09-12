package iproduct.utils

import java.sql.Connection

import iproduct.utils.EnvUtils.getProperty

object DatabaseUtils {
  def getDbConnection(dbUrl: String): Connection = {
    Class.forName("com.mysql.jdbc.Driver").newInstance()
    java.sql.DriverManager.getConnection(dbUrl)
  }

  def getDbConnection: Connection =
    getDbConnection(getProperty("dbUrl").get)
}
