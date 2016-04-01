package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.oauth.{OAuthCalculator, ConsumerKey, RequestToken}
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws._
import play.api.libs.iteratee._
import play.api.Logger
import play.api.libs.json._
import play.extras.iteratees._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def tweets = Action.async {

    /*val loggingIteratee = Iteratee.foreach[Array[Byte]] { array =>
      Logger.info(array.map(_.toChar).mkString)
    }*/

    val credentials: Option[(ConsumerKey, RequestToken)] =
      for {
        apiKey <- Play.configuration.getString("twitter.apiKey")
        apiSecret <- Play.configuration.getString("twitter.apiSecret")
        token <- Play.configuration.getString("twitter.token")
        tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
      } yield (
        ConsumerKey(apiKey, apiSecret),
        RequestToken(token, tokenSecret)
        )

    credentials.map { case (consumerKey, requestToken) =>
      val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

      val jsonStream: Enumerator[JsObject] =

        enumerator &>
        Encoding.decode() &>
        Enumeratee.grouped(JsonIteratees.jsSimpleObject)

      val loggingIteratee = Iteratee.foreach[JsObject] {
        value => Logger.info(value.toString)
      }

      jsonStream run loggingIteratee

      WS
        .url("https://stream.twitter.com/1.1/statuses/filter.json")

        .sign(OAuthCalculator(consumerKey, requestToken))

        .withQueryString("track" -> "cat")

        .get { response =>
          Logger.info("Status: " + response.status)
          iteratee
        }.map { _ =>
          Ok("Stream closed")
        }
    } getOrElse {
      Future {

        InternalServerError("Twitter credentials missing")
      }
    }
  }
}
