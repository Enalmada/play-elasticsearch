package util

object Util {
  def sortByOrder(currentSortBy: String, currentOrder: String, newSortByOpt: Option[String]): (String, String) = {

    val sortBy = newSortByOpt.getOrElse(currentSortBy)
    val order = newSortByOpt.map { newSortBy =>
      if (currentSortBy.equals(sortBy)) {
        if (currentOrder == "asc") {
          "desc"
        } else {
          "asc"
        }
      } else {
        "desc"
      }
    }.getOrElse(currentOrder)

    (sortBy, order)

  }
}

/**
  * Pagination support.
  */
case class PageFilter(page: Int = 0, pageSize: Int = 50) {
  def offset = page * pageSize
}

case class Page[+T](items: Seq[T], pageFilter: PageFilter, hasPrev: Boolean, hasNext: Boolean) {
  lazy val prev = Option(pageFilter.page - 1).filter(_ >= 0)
  lazy val current = pageFilter.page
  lazy val next = Option(pageFilter.page + 1).filter(_ => hasNext)
  lazy val from = pageFilter.offset + 1
  lazy val to = pageFilter.offset + items.size
  lazy val total = 0
}