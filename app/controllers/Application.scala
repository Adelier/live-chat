package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee.{Iteratee, Concurrent}

import play.libs.Akka;
import akka.actor._
import akka.actor.ActorRef;

import play.api.libs.json._
import play.api.Play.current

import scala.collection.mutable
import scala.collection.mutable._

import scala.concurrent._
import scala.concurrent.duration._

import ExecutionContext.Implicits.global

object Application extends Controller {

  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def index = Action { implicit request =>
    val wsPath = routes.Application.ws.webSocketURL()
    Ok(views.html.index(wsPath))
  }

  def room(room_id: String) = Action {
    val room = Akka.system().actorOf(Props[RoomActor], room_id)
    println(s"created room: $room")
    Ok(views.html.room(s"Room $room_id"))
  }
  def message(room_id:String, m: String) = Action {
    val room = Akka.system().actorSelection(s"akka://${Akka.system().name}/user/$room_id")
    // room ! m
    println(s"created room: $room")
    Ok(views.html.room(s"Room $room_id"))
  }

  // JSON WebSocket
  def ws:WebSocket[JsValue, JsValue] = WebSocket.acceptWithActor[JsValue, JsValue] { implicit request => out =>{
      Logger.info(s"WebSocket request (remote ip=${request.remoteAddress})")
      ChatSocketActor.props(out)
    }
  }

}

case class CompleteMessage(m: String, message_id: String, username: String)
case class Connected(name: String)
case class Disconnected(name: String)
case class ServerDown()
case class Room(room_id: String)


object ChatSocketActor {
  def props(out: ActorRef) = Props(new ChatSocketActor(out))
}
class ChatSocketActor (out: ActorRef) extends Actor {
  var name: String = "default_noname"
  var room: ActorRef = null

  override def receive = {
    case json: JsValue => (json \ "type").as[String] match {
//      case "diff" => // TODO
      case "room" => {
        Logger.info("FINE: room request")
        this.name = (json \ "name").asOpt[String].getOrElse("noname")
        val room_id = (json \ "room_id").asOpt[String].getOrElse("room404")
        val futureSelection = Akka.system().actorSelection(s"akka://${Akka.system().name}/user/$room_id") resolveOne 1.second
        def tellConnected = {
          Logger.info(s"room.tell(Connected(name), out) room=$room, name=$name, out=$out")
          room.tell(Connected(name), out)
        }
        futureSelection onSuccess { // get old room
          case oldRoom: ActorRef => {
            room = oldRoom
            Logger.debug(s"Getted room ${room.path}")
            tellConnected
          }
        }
        futureSelection onFailure { // create new room
          case t => {
            room = Akka.system().actorOf(Props[RoomActor], s"$room_id")
            Logger.debug(s"Created room ${room.path}")
            room ! Room(room_id)
            tellConnected
          }
        }
      }
      case "complete" => {
        Logger.debug("complete request: " + json)
        if (room == null) Logger.warn("no room")
        else this.room.tell(CompleteMessage(
          (json \ "value").asOpt[String].getOrElse("error"),
          (json \ "message_id").asOpt[String].getOrElse("noid"),
          name), out)
      }
      case "ping" => // prevent timeout disconnection
      case _ => Logger.info("NOT FINE: strange request")
    }
  }
  override def postStop() = {
    if (room != null)
      this.room.tell(Disconnected(name), out)
  }
}

class RoomActor extends Actor {
  var name: String = ""
  var users: Map[ActorRef, String] = Map.empty

  var system_message_id = 0

  def messageToAll(author:String, m:String, message_id:String) = {
    users foreach { case (out: ActorRef, username: String) => {
      val resp = JsObject(mutable.Seq(
        "type" -> JsString("message"),
        "author" -> JsString(author),
        "message" -> JsString(m),
        "message_id" -> JsString(message_id)
        // TODO message id
      ))
      Logger.info(s"FINE: responding $resp")
      out ! resp
    }}
  }

  override def receive: Receive = {
    case Room(room_id) => this.name = room_id
    case Connected(username) => {
      if (username ne "") {
        users get sender() match {
          case Some(oldname) => { 
            messageToAll("system", s"rename ${oldname} -> $username", s"$system_message_id")
            users += sender() -> username
            Logger.info(s"FINE: Connected($username), now in room '$name' ${users size} users")
          }
          case None => {
            users += sender() -> username
            messageToAll("system", s"$username connected, now in room '$name' ${users size} users", s"$system_message_id")
            Logger.info(s"FINE: Connected($username), now in room '$name' ${users size} users")
          }
        }
        system_message_id+=1
      }
    }
    case Disconnected(username) => {
      system_message_id+=1
      users -= sender()
      messageToAll("system", s"$username disconnected, now in room '$name' ${users size} users", s"$system_message_id")
      Logger.info(s"FINE: Disconnected($username), now in room '$name' ${users size} users")
    }
    case CompleteMessage(m, message_id, author) => {
        messageToAll(author, m, message_id)
    }
  }

  override def postStop() = {
    /*try {
      messageToAll("system", "room server going down. Try reload the page", s"$system_message_id")
    } catch {
      case e: Exception => // it's all over by now.
    }*/
  }
}