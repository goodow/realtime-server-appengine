/*
 * Copyright 2012 Goodow.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.goodow.realtime.server.presence;

import com.goodow.realtime.channel.constant.Platform;
import com.goodow.realtime.server.RealtimeApisModule;

import com.google.api.server.spi.config.AnnotationBoolean;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.appengine.api.channel.ChannelFailureException;
import com.google.appengine.api.channel.ChannelMessage;
import com.google.appengine.api.channel.ChannelService;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Named;

@Singleton
@Api(name = "presence", version = RealtimeApisModule.DEFAULT_VERSION, defaultVersion = AnnotationBoolean.TRUE, namespace = @ApiNamespace(ownerDomain = "goodow.com", ownerName = "Goodow", packagePath = "api.services"))
public class AppEngineChannelService implements MessageRouter {
  public static final Logger log = Logger.getLogger(AppEngineChannelService.class.getName());
  @Inject
  private ChannelService channelService;
  @Inject
  private PresenceEndpoint presence;

  @Override
  @ApiMethod(path = "pushToAppEngineChannel")
  public void push(@Named("subscribeId") String subscribeId,
      @Nullable @Named("messageType") String messageType, @Named("message") String msg) {
    try {
      channelService.sendMessage(new ChannelMessage(subscribeId, msg));
    } catch (ChannelFailureException e) {
      // Channel service is best-effort anyway, so it's safe to discard the
      // exception after taking note of it.
      log.log(Level.SEVERE, "Channel service failed for " + subscribeId, e);
    }
  }

  @Override
  @ApiMethod(path = "pushAllToAppEngineChannel")
  public void pushAll(@Named("documentId") String docId,
      @Nullable @Named("messageType") String messageType, @Named("message") String msg) {
    if (msg.length() > 8000) {
      // Channel API has a limit of 32767 UTF-8 bytes. It's OK for us not to
      // publish large messages; we can let clients poll. TODO(ohler): 8000 is
      // probably overly conservative, make a better estimate.
      log.warning(docId + ": Message too large (" + msg.length() + " chars), not publishing: "
          + msg);
      return;
    }
    Set<String> subscriptions = presence.listDocumentSubscriptions(docId, Platform.WEB.name());
    if (subscriptions == null || subscriptions.isEmpty()) {
      return;
    }
    for (String subscription : subscriptions) {
      push(subscription, messageType, msg);
    }
  }
}
