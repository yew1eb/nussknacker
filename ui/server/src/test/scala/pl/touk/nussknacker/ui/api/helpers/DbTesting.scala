package pl.touk.nussknacker.ui.api.helpers

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.db.DatabaseInitializer
import slick.jdbc.JdbcBackend

import scala.util.Try

object DbTesting extends LazyLogging {
  val db: JdbcBackend.Database = JdbcBackend.Database.forURL(
    url = s"jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
    driver = "org.hsqldb.jdbc.JDBCDriver",
    user = "SA",
    password = ""
  )

  new DatabaseInitializer(db).initDatabase()

  def cleanDB(): Try[Unit] = {
    Try {
      val session = db.createSession()
      session.prepareStatement("""delete from "process_attachments"""").execute()
      session.prepareStatement("""delete from "process_comments"""").execute()
      session.prepareStatement("""delete from "process_deployment_info"""").execute()
      session.prepareStatement("""delete from "process_versions"""").execute()
      session.prepareStatement("""delete from "tags"""").execute()
      session.prepareStatement("""delete from "environments"""").execute()
      session.prepareStatement("""delete from "processes"""").execute()
    }
  }
}