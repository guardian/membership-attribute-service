package controllers

import configuration.Config
import models.ApiError._
import models.ApiErrors._
import models.Features._
import actions._
import models._
import monitoring.CloudWatch
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.filters.cors.CORSActionBuilder
import services.{AuthenticationService, IdentityAuthService}
import models.AccountDetails._
import scala.concurrent.Future
import play.api.mvc.Results.Ok

class AttributeController {
  
  lazy val authenticationService: AuthenticationService = IdentityAuthService
  lazy val mmaCorsFilter = CORSActionBuilder(Config.mmaCorsConfig)
  lazy val corsFilter = CORSActionBuilder(Config.corsConfig)

  lazy val backendAction = corsFilter andThen BackendFromCookieAction
  lazy val mmaAction = mmaCorsFilter andThen BackendFromCookieAction
  lazy val metrics = CloudWatch("AttributesController")

  private def lookup(endpointDescription: String, onSuccess: Attributes => Result, onNotFound: Result = notFound) = backendAction.async { request =>
      authenticationService.userId(request).map[Future[Result]] { id =>
        request.touchpoint.attrService.get(id).map {
          case Some(attrs) =>
            metrics.put(s"$endpointDescription-lookup-successful", 1)
            onSuccess(attrs)
          case None =>
            metrics.put(s"$endpointDescription-user-not-found", 1)
            onNotFound
        }
      }.getOrElse {
        metrics.put(s"$endpointDescription-cookie-auth-failed", 1)
        Future(unauthorized)
      }
    }

  def membership = lookup("membership", identity[Attributes])

  def features = lookup("features",
    onSuccess = Features.fromAttributes,
    onNotFound = Features.unauthenticated
  )

  def membershipDetails = paymentDetails(Membership)
  def digitalPackDetails = paymentDetails(DigitalPack)

  def paymentDetails(product: ProductFamilyName) = mmaAction.async { implicit request =>

    val notSubscribed = Ok(Json.obj("msg" -> "not subscribed"))
    val productFamily = request.touchpoint.ratePlanIds(product)

    authenticationService.userId.fold[Future[Result]](Future(cookiesRequired)){ userId =>
      request.touchpoint.contactRepo.get(userId) flatMap { optContact =>
        optContact.fold[Future[Result]](Future(notSubscribed)) { contact =>
          request.touchpoint.paymentService.paymentDetails(contact, productFamily).map { details =>
            details.fold(notSubscribed) { paymentDetails =>
              (contact, paymentDetails).toResult
            }
          }
        }
      }
    }.recover {
      case e:IllegalStateException => notSubscribed
    }
  }
}
