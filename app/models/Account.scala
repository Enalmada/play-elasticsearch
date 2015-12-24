package models

import scalikejdbc._

case class Account(id: Int, email: String, name: String, status: String)

// See https://github.com/t2v/play2-auth for a full authentication/authorization example
object Account extends SQLSyntaxSupport[Account] {

  private val a = syntax("a")

  def apply(a: SyntaxProvider[Account])(rs: WrappedResultSet): Account = autoConstruct(rs, a)

  private val auto = AutoSession

  def findByEmail(email: String)(implicit s: DBSession = auto): Option[Account] = withSQL {
    select.from(Account as a).where.eq(a.email, email)
  }.map(Account(a)).single.apply()

  def findById(id: Int)(implicit s: DBSession = auto): Option[Account] = withSQL {
    select.from(Account as a).where.eq(a.id, id)
  }.map(Account(a)).single.apply()

  def findAll()(implicit s: DBSession = auto): Seq[Account] = withSQL {
    select.from(Account as a)
  }.map(Account(a)).list.apply()

  def create(account: Account)(implicit s: DBSession = auto) {
    withSQL {
      import account._
      insert.into(Account).values(id, email, name, status)
    }.update.apply()
  }

  type Status = String

  object Status {
    val Active: Status = "ACTIVE"
    val Inactive: Status = "INACTIVE"

    val values = List(Active, Inactive)

    def isStatus(s: String) = values.exists(_.toString == s)

    def withName(s: String) = values.find(_.toString == s).head
  }

  def statusOptions = Status.values.map { e => (e.toString, e.toString) }


}