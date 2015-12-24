import models.Account
import play.api._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    if (Account.findAll.isEmpty) {
      Seq(
        Account(1, "alice@example.com", "Alice", "ACTIVE"),
        Account(2, "alice2@example.com", "Alice", "INACTIVE"),
        Account(3, "bob@example.com", "Bob", "ACTIVE"),
        Account(4, "bob2@example.com", "Bob", "INACTIVE"),
        Account(5, "chris@example.com", "Chris", "ACTIVE"),
        Account(6, "chris2@example.com", "Chris", "INACTIVE")
      ) foreach Account.create
    }

  }

}