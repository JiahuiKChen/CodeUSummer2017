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

package codeu.chat.client.core;

import java.util.*;

import codeu.chat.common.BasicController;
import codeu.chat.common.BasicView;
import codeu.chat.common.ConversationHeader;
import codeu.chat.common.User;
import codeu.chat.util.Uuid;

public final class UserContext {

  public final User user;
  private final BasicView view;
  private final BasicController controller;

  public Set<Uuid> newConversations;

  public UserContext(User user, BasicView view, BasicController controller) {
    this.user = user;
    this.view = view;
    this.controller = controller;

    newConversations = new HashSet<>();
  }

  public ConversationContext start(String name) {
    final ConversationHeader conversation = controller.newConversation(name, user.id);

    if(conversation != null){
      return new ConversationContext(user, conversation, view, controller);
    }
    return null;
  }

  public HashMap<Uuid, ConversationContext> conversations() {
    // Use all the ids to get all the conversations and convert them to
    // Conversation Contexts.
    final HashMap<Uuid, ConversationContext> conversations = new HashMap<>();
    for(final ConversationHeader c : view.getConversations()){
      ConversationContext convo = new ConversationContext(user, c, view, controller);
      conversations.put(convo.conversation.id, convo);
    }

    return conversations;
  }

}
