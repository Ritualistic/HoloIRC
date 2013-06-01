/*
    LightIRC - an IRC client for Android

    Copyright 2013 Lalit Maganti

    This file is part of LightIRC.

    LightIRC is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    LightIRC is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with LightIRC. If not, see <http://www.gnu.org/licenses/>.
 */

package com.fusionx.lightirc.parser;

import com.fusionx.lightirc.services.IRCService;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.lightirc.PrivateActionEvent;

public class MessageParser {
    private IRCService mService;

    private IRCService getService() {
        return mService;
    }

    public void setService(IRCService service) {
        mService = service;
    }

    public void channelMessageToParse(final String serverName, final String channelName,
                                      final String message) {
        final PircBotX bot = getService().getBot(serverName);
        if(message != null) {
            final String parsedArray[] = message.split("\\s+");

            if (parsedArray[0].startsWith("/")) {
                // TODO parse this string fully
                if (parsedArray[0].startsWith("/join")) {
                    final String channel = parsedArray[1];
                    // TODO - input validation
                    bot.sendIRC().joinChannel(channel);
                } else if (parsedArray[0].startsWith("/me")) {
                    final String action = parsedArray[1];
                    // TODO - input validation
                    bot.sendIRC().action(channelName, action);
                    bot.getConfiguration().getListenerManager()
                            .dispatchEvent(new ActionEvent(bot, bot.getUserBot(),
                                    bot.getUserChannelDao().getChannel(channelName), action));
                } else if (message.startsWith("/nick")) {
                    final String newNick = parsedArray[1];
                    bot.sendIRC().changeNick(newNick);
                } else if (message.startsWith("/msg")) {
                    final String newNick = parsedArray[1];
                    String pm = parsedArray[2];
                    if(pm == null) {
                        pm = "";
                    } else {
                        bot.sendIRC().message(newNick, pm);
                    }
                    bot.getConfiguration().getListenerManager().dispatchEvent
                            (new PrivateActionEvent(bot, bot.getUserChannelDao()
                                    .getUser(newNick), pm));
                } else {
                    //Dispatch event here
                }
            } else {
                bot.sendIRC().message(channelName, message);
                bot.getConfiguration().getListenerManager().dispatchEvent
                        (new MessageEvent(bot, bot.getUserChannelDao().getChannel(channelName),
                                bot.getUserBot(), message));
            }
        }
    }

    public void serverMessageToParse(String serverName, String message) {
        final PircBotX bot = getService().getBot(serverName);

        if (message.startsWith("/")) {
            // TODO parse this string fully
            if (message.startsWith("/join")) {
                String channel = message.replace("/join ", "");
                // TODO - input validation
                bot.sendIRC().joinChannel(channel);
            } else {
                //String bufferMessage = "Unknown command";
                //bot.appendToBuffer(bufferMessage);
            }
        } else {
            //String bufferMessage = "Invalid message";
            //bot.appendToBuffer(bufferMessage);
        }
    }

    public void userMessageToParse(String serverName, String userNick, String message) {
        final PircBotX bot = getService().getBot(serverName);

        if (message.startsWith("/")) {
            // TODO parse this string fully
            if (message.startsWith("/me")) {
                final User user = bot.getUserChannelDao().getUser(userNick);
                String action = message.replace("/me ", "");
                // TODO - input validation
                user.send().action(action);
                bot.getConfiguration().getListenerManager().dispatchEvent
                        (new PrivateActionEvent(bot, user, message));
            } else {
            }
        } else {
            final User user = bot.getUserChannelDao().getUser(userNick);
            user.send().message(message);
            bot.getConfiguration().getListenerManager().dispatchEvent
                    (new PrivateMessageEvent(bot, user, message));
        }
    }
}