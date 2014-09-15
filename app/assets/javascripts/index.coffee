$ ->
  # wsUrl = jsRoutes.controllers.AppController.indexWS().webSocketURL()
  ws = new WebSocket $("body").data("ws-url")
  ws.onmessage = (event) ->
    message = JSON.parse event.data
    switch message.type
      when "message"
        printMessageLine(message)
      else
        console.log(message)

  ws.onopen = () ->
    nm = getCookie("username")
    nm = "person" + Math.floor(Math.random()*1000) if nm == ""
    $("#enterroomusernametext").val(nm)
    tryConnect("all", nm)
    setInterval(function(){ping();}, 10000);
  
  ping = () -> 
    ws.send(JSON.stringify({
            type: "ping"
        }))

  tryConnect = (sRoom_id, sName) ->
    ws.send(JSON.stringify({
            type: "room"
            room_id: sRoom_id
            name: sName
        }))
    # TODO recieve last messages, last msg id

  $("#enterroomform").submit (event) ->
    event.preventDefault()
    sRoom_id = $("#enterroomtext").val()
    sRoom_id = "all" if sRoom_id == ""
    sName = $("#enterroomusernametext").val()
    setCookie("username", sName, 365)

    tryConnect(sRoom_id, sName)

  message_id = 0

  $("#inputmessageform").submit (event) ->
    event.preventDefault()
    # send the message to enter the room
    msg_val = $("#inputmessagetext").val()
    message_id = message_id + 1 # if msg_val != ""
    ws.send(JSON.stringify({
        type: "complete"
        value: "" # msg_val
        message_id: "" + message_id# "" + message_id
        submit: "true"
    }))
    username = $("#enterroomusernametext").val() # getCookie("username") #
    $("#inputmessagetext").val("")

  #$("#inputmessagetext").keypress (event) ->
  $("#inputmessagetext").on('input', (event) ->
    ws.send(JSON.stringify({
        type: "complete"
        value: $("#inputmessagetext").val()
        message_id: "" + message_id
    })))

mess_fullid = (author, message_id) -> "message_" + author + message_id

printMessageLine = (message) ->
  m_fullid = mess_fullid(message.author, message.message_id)
  # temp
  document.title = "RC " + message.author + ":" + message.message

  if $("#" + m_fullid).length == 0
    # it doesn't exist
    # complete previous
    $("#"+mess_fullid(message.author, (parseInt(message.message_id) - 1) + "" )).removeClass("incomplete")
    # lift it down
    complete_message = $("#"+mess_fullid(message.author, (parseInt(message.message_id) - 1) + "" ))
    $("#messagearea").append(complete_message)
    complete_message.stop().animate(backgroundColor: "#88AAAA").animate(backgroundColor: "#fff");
    # construct new
    col_css = "mess_color_" + (hashCode(message.author) % 7 + 1)
    msg = "<div id=\"" + m_fullid + "\" class=\"incomplete " + col_css + "\">" +
         "<b>" + message.author + "</b>: " + message.message + "<\div>";
    mess = $("#messagearea").append(msg);
    # scroll down
    $("#messagearea").stop().animate({scrollTop:$("#messagearea")[0].scrollHeight}, 1000);
  else
    # it exists
    $("#" + m_fullid).html("<b>" + message.author + "</b>: " + message.message)

hashCode = (str) ->
  hash = 0;
  return hash if (str.length == 0)
  for i in [0...str.length]
      char = str.charCodeAt(i);
      hash = ((hash << 5)-hash)+char;
      hash = hash & hash; # Convert to 32bit integer
  return hash

getCookie = (cname) ->
    name = cname + "=";
    ca = document.cookie.split(';');
    for i in [0...ca.length]
        c = ca[i];
        while c.charAt(0) == ' ' then c = c.substring(1)
        return c.substring(name.length,c.length) if c.indexOf(name) != -1
    return ""

setCookie = (cname, cvalue, exdays) ->
  if (exdays) 
    date = new Date();
    date.setTime(date.getTime()+(exdays*24*60*60*1000));
    expires = "; expires="+date.toGMTString();
  else
    expires = ""
  document.cookie = cname + "=" + cvalue + expires+"; path=/";