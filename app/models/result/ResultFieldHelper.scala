package models.result

import models.database.DatabaseField
import models.result.filter._
import models.result.orderBy.OrderBy

object ResultFieldHelper {
  def sqlForField(t: String, field: String, fields: Seq[DatabaseField]) = fields.find(_.prop == field) match {
    case Some(f) => f.col
    case None => throw new IllegalStateException(s"Invalid $t field [$field]. Allowed fields are [${fields.map(_.prop).mkString(", ")}].")
  }

  def orderClause(orderBys: Seq[OrderBy], fields: Seq[DatabaseField]) = if (orderBys.isEmpty) {
    None
  } else {
    Some(orderBys.map(orderBy => sqlForField("order by", orderBy.col, fields) + " " + orderBy.dir.sql).mkString(", "))
  }

  def filterClause(filters: Seq[Filter], fields: Seq[DatabaseField], add: Option[String] = None) = if (filters.isEmpty) {
    add
  } else {
    val clauses = filters.map { filter =>
      val col = sqlForField("where clause", filter.k, fields)
      val vals = filter.v.map(_ => "?").mkString(", ")
      filter.o match {
        case Equal => s"$col in ($vals)"
        case NotEqual => s"$col not in ($vals)"
        case StartsWith => "(" + filter.v.map(_ => s"$col like ?").mkString(" or ") + ")"
        case EndsWith => "(" + filter.v.map(_ => s"$col like ?").mkString(" or ") + ")"
        case Contains => "(" + filter.v.map(_ => s"$col like ?").mkString(" or ") + ")"
        case GreaterThanOrEqual => "(" + vals.map(_ => s"$col >= ?").mkString(" or ") + ")"
        case LessThanOrEqual => "(" + vals.map(_ => s"$col <= ?").mkString(" or ") + ")"
        case x => throw new IllegalStateException(s"Operation [$x] is not currently supported.")
      }
    }
    Some(add.map(f => s"(${clauses.mkString(" and ")}) and $f").getOrElse(clauses.mkString(" and ")))
  }

  def getResultSql(
    filters: Seq[Filter], orderBys: Seq[OrderBy] = Nil, limit: Option[Int] = None, offset: Option[Int] = None,
    fields: Seq[DatabaseField] = Nil, add: Option[String] = None
  ) = {
    filterClause(filters, fields, add) -> orderClause(orderBys, fields)
  }
}
