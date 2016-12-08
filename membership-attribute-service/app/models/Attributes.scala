package models

import json._
import play.api.libs.json._
import play.api.mvc.Result
import play.api.mvc.Results.Ok

import scala.language.implicitConversions
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class ContentAccess(member: Boolean, paidMember: Boolean)

object ContentAccess {
  implicit val jsWrite = Json.writes[ContentAccess]
}

case class Attributes(UserId: String, Tier: String, MembershipNumber: Option[String], PublicTier: Option[Boolean] = None, AdFree: Option[Boolean] = None) {
  require(Tier.nonEmpty)
  require(UserId.nonEmpty)

  lazy val allowsPublicTierDisplay = PublicTier.exists(identity)
  lazy val isFriendTier = Tier.equalsIgnoreCase("friend")
  lazy val isPaidTier = !isFriendTier
  lazy val isAdFree = AdFree.exists(identity)

  lazy val contentAccess = ContentAccess(member = true, paidMember = isPaidTier) // we want to include staff!
}

object Attributes {

  val ignore = OWrites[Any](_ => Json.obj())
  implicit val jsWrite: OWrites[Attributes] = (
    (__ \ "userId").write[String] and
    (__ \ "tier").write[String] and
    (__ \ "membershipNumber").writeNullable[String] and
    (__ \ "adFree").writeNullable[Boolean] and
    ignore
  )(unlift(Attributes.unapply)).addField("contentAccess", _.contentAccess)

  implicit def toResult(attrs: Attributes): Result =
    Ok(Json.toJson(attrs))
}
