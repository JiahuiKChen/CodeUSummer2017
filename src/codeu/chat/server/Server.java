// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package codeu.chat.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import codeu.chat.client.core.Context;
import codeu.chat.common.*;
import codeu.chat.util.*;
import codeu.chat.util.connections.Connection;

public final class Server {

  private interface Command {
    void onMessage(InputStream in, OutputStream out) throws IOException;
  }

  private static final Logger.Log LOG = Logger.newLog(Server.class);

  private static final int RELAY_REFRESH_MS = 5000;  // 5 seconds

  private final Timeline timeline = new Timeline();

  private final Map<Integer, Command> commands = new HashMap<>();

  private final Uuid id;
  private final Secret secret;

  private final Model model = new Model();
  private final View view = new View(model);
  private final Controller controller;

  private final Relay relay;
  private Uuid lastSeen = Uuid.NULL;

  public Server(final Uuid id, final Secret secret, final Relay relay) {

    this.id = id;
    this.secret = secret;
    this.controller = new Controller(id, model);
    this.relay = relay;

    // Whenever a new Server starts up, reload the data from the log
    try {
      reloadOldData();
    } catch (Exception e){
      System.out.println("Could not load transaction log.");
    }

    // New Message - A client wants to add a new message to the back end.
    this.commands.put(NetworkCode.NEW_MESSAGE_REQUEST, new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final Uuid author = Uuid.SERIALIZER.read(in);
        final Uuid conversation = Uuid.SERIALIZER.read(in);
        final String content = Serializers.STRING.read(in);

        final Message message = controller.newMessage(author, conversation, content);

        Serializers.INTEGER.write(out, NetworkCode.NEW_MESSAGE_RESPONSE);
        Serializers.nullable(Message.SERIALIZER).write(out, message);

        timeline.scheduleNow(createSendToRelayEvent(
            author,
            conversation,
            message.id));
      }
    });

    // New User - A client wants to add a new user to the back end.
    this.commands.put(NetworkCode.NEW_USER_REQUEST,  new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final String name = Serializers.STRING.read(in);
        final User user = controller.newUser(name);

        Serializers.INTEGER.write(out, NetworkCode.NEW_USER_RESPONSE);
        Serializers.nullable(User.SERIALIZER).write(out, user);
      }
    });

    // New Conversation - A client wants to add a new conversation to the back end.
    this.commands.put(NetworkCode.NEW_CONVERSATION_REQUEST,  new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final String title = Serializers.STRING.read(in);
        final Uuid owner = Uuid.SERIALIZER.read(in);
        final ConversationHeader conversation = controller.newConversation(title, owner);

        Serializers.INTEGER.write(out, NetworkCode.NEW_CONVERSATION_RESPONSE);
        Serializers.nullable(ConversationHeader.SERIALIZER).write(out, conversation);
      }
    });

    // Get Users - A client wants to get all the users from the back end.
    this.commands.put(NetworkCode.GET_USERS_REQUEST, new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final Collection<User> users = view.getUsers();

        Serializers.INTEGER.write(out, NetworkCode.GET_USERS_RESPONSE);
        Serializers.collection(User.SERIALIZER).write(out, users);
      }
    });

    // Get Conversations - A client wants to get all the conversations from the back end.
    this.commands.put(NetworkCode.GET_ALL_CONVERSATIONS_REQUEST, new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final Collection<ConversationHeader> conversations = view.getConversations();

        Serializers.INTEGER.write(out, NetworkCode.GET_ALL_CONVERSATIONS_RESPONSE);
        Serializers.collection(ConversationHeader.SERIALIZER).write(out, conversations);
      }
    });

    // Get Conversations By Id - A client wants to get a subset of the conversations from
    //                           the back end. Normally this will be done after calling
    //                           Get Conversations to get all the headers and now the client
    //                           wants to get a subset of the payloads.
    this.commands.put(NetworkCode.GET_CONVERSATIONS_BY_ID_REQUEST, new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final Collection<Uuid> ids = Serializers.collection(Uuid.SERIALIZER).read(in);
        final Collection<ConversationPayload> conversations = view.getConversationPayloads(ids);

        Serializers.INTEGER.write(out, NetworkCode.GET_CONVERSATIONS_BY_ID_RESPONSE);
        Serializers.collection(ConversationPayload.SERIALIZER).write(out, conversations);
      }
    });

    // Get Messages By Id - A client wants to get a subset of the messages from the back end.
    this.commands.put(NetworkCode.GET_MESSAGES_BY_ID_REQUEST, new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        final Collection<Uuid> ids = Serializers.collection(Uuid.SERIALIZER).read(in);
        final Collection<Message> messages = view.getMessages(ids);

        Serializers.INTEGER.write(out, NetworkCode.GET_MESSAGES_BY_ID_RESPONSE);
        Serializers.collection(Message.SERIALIZER).write(out, messages);
      }
    });

    // Get Server Info - A client wants to see the current server version.
    this.commands.put(NetworkCode.SERVER_INFO_REQUEST, new Command() {
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {

        // Write out server info response
        Serializers.INTEGER.write(out, NetworkCode.SERVER_INFO_RESPONSE);
        Uuid.SERIALIZER.write(out, view.getInfo().version);
      }
    });

    // Get Conversation Interests - A client wants to see their list of interested conversations
    this.commands.put(NetworkCode.GET_CONVERSATION_INTERESTS_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid id = Uuid.SERIALIZER.read(in);
        final Collection<Uuid> interests = view.getConversationInterests(id);

        Serializers.INTEGER.write(out, NetworkCode.GET_CONVERSATION_INTERESTS_RESPONSE);
        Serializers.collection(Uuid.SERIALIZER).write(out, interests);
      }
    });

    // Add Conversation Interest - A client wants to add a new conversation to their list of interests
    this.commands.put(NetworkCode.NEW_CONVERSATION_INTEREST_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Collection<Uuid> interests = controller.newConversationInterest(userId, convoId);

        Serializers.INTEGER.write(out, NetworkCode.NEW_CONVERSATION_INTEREST_RESPONSE);
        Serializers.collection(Uuid.SERIALIZER).write(out, interests);
      }
    });

    // Get User Interests - A client wants to see their list of interested conversations
    this.commands.put(NetworkCode.GET_USER_INTERESTS_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid id = Uuid.SERIALIZER.read(in);
        final Collection<Uuid> interests = view.getUserInterests(id);

        Serializers.INTEGER.write(out, NetworkCode.GET_USER_INTERESTS_RESPONSE);
        Serializers.collection(Uuid.SERIALIZER).write(out, interests);
      }
    });

    // Add User Interest - A client wants to add a new conversation to their list of interests
    this.commands.put(NetworkCode.NEW_USER_INTEREST_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid followedUserId = Uuid.SERIALIZER.read(in);
        final Collection<Uuid> interests = controller.newUserInterest(userId, followedUserId);

        Serializers.INTEGER.write(out, NetworkCode.NEW_USER_INTEREST_RESPONSE);
        Serializers.collection(Uuid.SERIALIZER).write(out, interests);
      }
    });

    // Remove User Interest - A client wants to add a new conversation to their list of interests
    this.commands.put(NetworkCode.REMOVE_USER_INTEREST_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid followedUserId = Uuid.SERIALIZER.read(in);
        final Collection<Uuid> interests = controller.removeUserInterest(userId, followedUserId);

        Serializers.INTEGER.write(out, NetworkCode.REMOVE_USER_INTEREST_RESPONSE);
        Serializers.collection(Uuid.SERIALIZER).write(out, interests);
      }
    });

    // Add Conversation Interest - A client wants to add a new conversation to their list of interests
    this.commands.put(NetworkCode.REMOVE_CONVERSATION_INTEREST_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Collection<Uuid> interests = controller.removeConversationInterest(userId, convoId);

        Serializers.INTEGER.write(out, NetworkCode.REMOVE_CONVERSATION_INTEREST_RESPONSE);
        Serializers.collection(Uuid.SERIALIZER).write(out, interests);
      }
    });

    // Add Updated Conversation - A client wants to add a conversation to their list of updated conversations
    this.commands.put(NetworkCode.NEW_UPDATED_CONVERSATION_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Time time = Time.SERIALIZER.read(in);
        final Map<Uuid, Time> updated = controller.newUpdatedConversation(userId, convoId, time);

        Serializers.INTEGER.write(out, NetworkCode.NEW_UPDATED_CONVERSATION_RESPONSE);
        Serializers.map(Uuid.SERIALIZER, Time.SERIALIZER).write(out, updated);
      }
    });

    // Get Updated Conversations - A client wants to view their updated conversations
    this.commands.put(NetworkCode.GET_UPDATED_CONVERSATIONS_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Map<Uuid, Time> updated = view.getUpdatedConversations(userId);

        Serializers.INTEGER.write(out, NetworkCode.GET_UPDATED_CONVERSATIONS_RESPONSE);
        Serializers.map(Uuid.SERIALIZER, Time.SERIALIZER).write(out, updated);
      }
    });

    // Update User's Last Status Update - A client wants to update their last status update time
    this.commands.put(NetworkCode.UPDATE_USER_LAST_STATUS_UPDATE_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Time update = Time.SERIALIZER.read(in);
        final Time lastUpdate = controller.updateUsersLastStatusUpdate(userId, update);

        Serializers.INTEGER.write(out, NetworkCode.UPDATE_USER_LAST_STATUS_UPDATE_RESPONSE);
        Time.SERIALIZER.write(out, lastUpdate);
      }
    });

    // Get User's Last Status Update - A client wants to get their last status update time
    this.commands.put(NetworkCode.GET_USER_LAST_STATUS_UPDATE_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Time update = view.getLastStatusUpdate(userId);

        Serializers.INTEGER.write(out, NetworkCode.GET_USER_LAST_STATUS_UPDATE_RESPONSE);
        Time.SERIALIZER.write(out, update);
      }
    });

    // Get User's Unseen Messages Count - A client wants to see the number of messages that they have not viewed
    this.commands.put(NetworkCode.GET_USER_MESSAGE_COUNT_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Integer count = view.getUnseenMessagesCount(userId, convoId);

        Serializers.INTEGER.write(out, NetworkCode.GET_USER_MESSAGE_COUNT_RESPONSE);
        Serializers.INTEGER.write(out, count);
      }
    });

    // Update User's Unseen Messages Count - A client wants to update the number of messages that they have not viewed
    this.commands.put(NetworkCode.UPDATE_USER_MESSAGE_COUNT_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Integer count = Serializers.INTEGER.read(in);
        final Integer newCount = controller.updateUsersUnseenMessagesCount(userId, convoId, count);

        Serializers.INTEGER.write(out, NetworkCode.UPDATE_USER_MESSAGE_COUNT_RESPONSE);
        Serializers.INTEGER.write(out, newCount);
      }
    });

    // Toggle User's Member Bit - A client wants to toggle the member bit of a user in a specific conversation
    this.commands.put(NetworkCode.TOGGLE_MEMBER_BIT_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Boolean flag = Serializers.BOOLEAN.read(in);
        final Integer access = controller.toggleMemberBit(convoId, userId, flag);

        Serializers.INTEGER.write(out, NetworkCode.TOGGLE_MEMBER_BIT_RESPONSE);
        Serializers.INTEGER.write(out, access);
      }
    });

    // Toggle User's Owner Bit - A client wants to toggle the owner bit of a user in a specific conversation
    this.commands.put(NetworkCode.TOGGLE_OWNER_BIT_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Boolean flag = Serializers.BOOLEAN.read(in);
        final Integer access = controller.toggleOwnerBit(convoId, userId, flag);

        Serializers.INTEGER.write(out, NetworkCode.TOGGLE_OWNER_BIT_RESPONSE);
        Serializers.INTEGER.write(out, access);
      }
    });

    // Toggle User's Owner Bit - A client wants to toggle the owner bit of a user in a specific conversation
    this.commands.put(NetworkCode.TOGGLE_CREATOR_BIT_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Boolean flag = Serializers.BOOLEAN.read(in);
        final Integer access = controller.toggleCreatorBit(convoId, userId, flag);

        Serializers.INTEGER.write(out, NetworkCode.TOGGLE_CREATOR_BIT_RESPONSE);
        Serializers.INTEGER.write(out, access);
      }
    });

    // Toggle User's Owner Bit - A client wants to toggle the owner bit of a user in a specific conversation
    this.commands.put(NetworkCode.TOGGLE_REMOVED_BIT_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Integer access = controller.toggleRemovedBit(convoId, userId);

        Serializers.INTEGER.write(out, NetworkCode.TOGGLE_REMOVED_BIT_RESPONSE);
        Serializers.INTEGER.write(out, access);
      }
    });

    // Get User's Access Control - A client wants to see the access control of a user
    this.commands.put(NetworkCode.GET_USER_ACCESS_CONTROL_REQUEST, new Command(){
      @Override
      public void onMessage(InputStream in, OutputStream out) throws IOException {
        final Uuid convoId = Uuid.SERIALIZER.read(in);
        final Uuid userId = Uuid.SERIALIZER.read(in);
        final Integer access = view.getUserAccessControl(convoId, userId);

        Serializers.INTEGER.write(out, NetworkCode.GET_USER_ACCESS_CONTROL_RESPONSE);
        Serializers.INTEGER.write(out, access);
      }
    });


    this.timeline.scheduleNow(new Runnable() {
      @Override
      public void run() {
        try {

          LOG.verbose("Reading update from relay...");

          for (final Relay.Bundle bundle : relay.read(id, secret, lastSeen, 32)) {
            onBundle(bundle);
            lastSeen = bundle.id();
          }

        } catch (Exception ex) {

          LOG.error(ex, "Failed to read update from relay.");

        }

        timeline.scheduleIn(RELAY_REFRESH_MS, this);
      }
    });
  }

  private void reloadOldData() throws IOException {
    // Open the transaction log file for reading
    FileReader fileReader = new FileReader("data/transaction_log.txt");
    BufferedReader bufferedReader = new BufferedReader(fileReader);

    // Read the header lines of each transaction log
    String line;

    while((line = bufferedReader.readLine()) != null) {

      // Instantiate a Tokenizer to parse through log's data
      Tokenizer logInfo = new Tokenizer(line);

      // Three pieces of data applicable to all log elements: it's command type, Uuid, and Time in milliseconds
      String commandType = logInfo.next();
      Uuid commandUuid = Uuid.parse(logInfo.next());

      // USER reload
      if (commandType.equals("ADD-USER")) {
        // For user-related commands 3rd element will be user's chosen name
        String userName = logInfo.next();
        Time commandCreation = Time.fromMs(Long.parseLong(logInfo.next()));

        // Create a new user based on it's unique contents, as well as it's username without quotes
        controller.newUser(commandUuid, userName, commandCreation);
      }

      // CONVERSATION reload
      else if (commandType.equals("ADD-CONVERSATION")) {
        // For convo/message commands 3rd element is creator's NUMERIC ID (UUID not name)
        Uuid ownerUuid = Uuid.parse(logInfo.next());

        // For convo commands 4th element is convo name, for message commands 4th element is message content
        String convoTitle = logInfo.next();
        Time commandCreation = Time.fromMs(Long.parseLong(logInfo.next()));

        controller.newConversation(commandUuid, convoTitle, ownerUuid, commandCreation);
      }

      // MESSAGE reload
      else if (commandType.equals("ADD-MESSAGE")) {
        // For convo/message commands 3rd element is creator's NUMERIC ID (UUID not name)
        Uuid ownerUuid = Uuid.parse(logInfo.next());
        Uuid convoUuid = Uuid.parse(logInfo.next());

        // For convo commands 5th element is convo name, for message commands 4th element is message content
        String messageContent = logInfo.next();
        Time commandCreation = Time.fromMs(Long.parseLong(logInfo.next()));

        controller.newMessage(commandUuid, ownerUuid, convoUuid, messageContent, commandCreation);
      }

      // INTEREST SYSTEM
      else if(commandType.equals("ADD-INTEREST-USER")){
        Uuid follow = Uuid.parse(logInfo.next());

        controller.newUserInterest(commandUuid, follow);
      }
      else if(commandType.equals("REMOVE-INTEREST-USER")){
        Uuid follow = Uuid.parse(logInfo.next());

        controller.removeUserInterest(commandUuid, follow);
      }
      else if(commandType.equals("ADD-INTEREST-CONVERSATION")){
        Uuid follow = Uuid.parse(logInfo.next());

        controller.newConversationInterest(commandUuid, follow);
      }
      else if(commandType.equals("REMOVE-INTEREST-CONVERSATION")){
        Uuid follow = Uuid.parse(logInfo.next());

        controller.removeConversationInterest(commandUuid, follow);
      }

      // ACCESS CONTROL
      if(commandType.equals("ADD-CONVO-CREATOR")){
        Uuid user = Uuid.parse(logInfo.next());

        controller.toggleCreatorBit(commandUuid, user, true);
      }
      else if(commandType.equals("ADD-CONVO-MEMBER")){
        Uuid user = Uuid.parse(logInfo.next());

        controller.toggleMemberBit(commandUuid, user, true);
      }
      else if(commandType.equals("REMOVE-CONVO-MEMBER")){
        Uuid user = Uuid.parse(logInfo.next());

        controller.toggleMemberBit(commandUuid, user, false);
      }
      else if(commandType.equals("REMOVE-CONVO-TOGGLE")){
        Uuid user = Uuid.parse(logInfo.next());

        controller.toggleRemovedBit(commandUuid, user);
      }
      else if(commandType.equals("ADD-CONVO-OWNER")){
        Uuid user = Uuid.parse(logInfo.next());

        controller.toggleOwnerBit(commandUuid, user, true);
      }
      else if(commandType.equals("REMOVE-CONVO-OWNER")){
        Uuid user = Uuid.parse(logInfo.next());

        controller.toggleOwnerBit(commandUuid, user, false);
      }
    }

    LOG.info("Successfully restored last logged server state.");

    fileReader.close();
    bufferedReader.close();
  }

  public void handleConnection(final Connection connection) {
    timeline.scheduleNow(new Runnable() {
      @Override
      public void run() {
        try {

          LOG.info("Handling connection...");

          final int type = Serializers.INTEGER.read(connection.in());
          final Command command = commands.get(type);

          if (command == null) {
            // The message type cannot be handled so return a dummy message.
            Serializers.INTEGER.write(connection.out(), NetworkCode.NO_MESSAGE);
            LOG.info("Connection rejected");
          } else {
            command.onMessage(connection.in(), connection.out());
            LOG.info("Connection accepted");
          }
        } catch (Exception ex) {

          LOG.error(ex, "Exception while handling connection.");

        }

        try {
          connection.close();
        } catch (Exception ex) {
          LOG.error(ex, "Exception while closing connection.");
        }
      }
    });
  }

  private void onBundle(Relay.Bundle bundle) {

    final Relay.Bundle.Component relayUser = bundle.user();
    final Relay.Bundle.Component relayConversation = bundle.conversation();
    final Relay.Bundle.Component relayMessage = bundle.user();

    User user = model.userById().first(relayUser.id());

    if (user == null) {
      user = controller.newUser(relayUser.id(), relayUser.text(), relayUser.time());
    }

    ConversationHeader conversation = model.conversationById().first(relayConversation.id());

    if (conversation == null) {

      // As the relay does not tell us who made the conversation - the first person who
      // has a message in the conversation will get ownership over this server's copy
      // of the conversation.
      conversation = controller.newConversation(relayConversation.id(),
                                                relayConversation.text(),
                                                user.id,
                                                relayConversation.time());
    }

    Message message = model.messageById().first(relayMessage.id());

    if (message == null) {
      message = controller.newMessage(relayMessage.id(),
                                      user.id,
                                      conversation.id,
                                      relayMessage.text(),
                                      relayMessage.time());
    }
  }

  private Runnable createSendToRelayEvent(final Uuid userId,
                                          final Uuid conversationId,
                                          final Uuid messageId) {
    return new Runnable() {
      @Override
      public void run() {
        final User user = view.findUser(userId);
        final ConversationHeader conversation = view.findConversation(conversationId);
        final Message message = view.findMessage(messageId);
        relay.write(id,
                    secret,
                    relay.pack(user.id, user.name, user.creation),
                    relay.pack(conversation.id, conversation.title, conversation.creation),
                    relay.pack(message.id, message.content, message.creation));
      }
    };
  }
}
