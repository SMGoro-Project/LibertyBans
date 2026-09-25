/*
 * LibertyBans
 * Copyright © 2023 Anand Beh
 *
 * LibertyBans is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * LibertyBans is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */

package space.arim.libertybans.env.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PostCommandInvocationEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.arim.libertybans.core.env.PlatformListener;
import space.arim.libertybans.core.selector.Guardian;
import space.arim.omnibus.util.ThisClass;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ConnectionListener implements PlatformListener {

	private final PluginContainer plugin;
	private final ProxyServer server;
	private final Guardian guardian;

	private static final Logger logger = LoggerFactory.getLogger(ThisClass.get());

	@Inject
	public ConnectionListener(PluginContainer plugin, ProxyServer server, Guardian guardian) {
		this.plugin = plugin;
		this.server = server;
		this.guardian = guardian;
	}

	@Override
	public void register() {
		server.getEventManager().register(plugin, this);
	}

	@Override
	public void unregister() {
		server.getEventManager().unregisterListener(plugin, this);
	}

	@Subscribe(order = PostOrder.EARLY)
	public EventTask onConnect(LoginEvent event) {
		if (!event.getResult().isAllowed()) {
			logger.trace("Event {} is already blocked", event);
			return null;
		}
		Player player = event.getPlayer();

		return EventTask.resumeWhenComplete(guardian.executeAndCheckConnection(
				player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress()
		).thenAccept((message) -> {
			if (message == null) {
				logger.trace("Event {} will be permitted", event);
			} else {
				if (event.getPlayer().getProtocolVersion().greaterThan(ProtocolVersion.MINECRAFT_1_21_5)) {
                    event.setResult(ComponentResult.denied(message));
                }
			}
		}));
	}

    @Subscribe(order = PostOrder.LATE)
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Optional<RegisteredServer> serverOpt = event.getInitialServer();
        if (serverOpt.isEmpty()) {
            return null;
        }
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(guardian.executeAndCheckConnection(
                player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress()
        ).thenAccept((message) -> {
            if (message == null) {
                logger.trace("Event {} will be permitted", event);
            } else {
                server.getScheduler().buildTask(plugin, () -> {
                    event.getPlayer().sendMessage(message);
                }).delay(5, TimeUnit.SECONDS).schedule();
                Set<String> servers = guardian.serversForBan();

                RegisteredServer defaultServer = null;
                for (String s : servers) {
                    if (server.getServer(s).isPresent()) {
                        defaultServer = server.getServer(s).get();
                        break;
                    }
                }
                if (defaultServer == null) {
                    event.getPlayer().disconnect(message);
                }
                event.setInitialServer(defaultServer);
            }
        }));
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onServerSwitchGlobalBan(ServerPreConnectEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        RegisteredServer destination = event.getResult().getServer().orElse(null);
        if (destination == null) {
            // Properly speaking, the API does not exclude this possibility
            return null;
        }
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(guardian.executeAndCheckConnection(
                player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress()
        ).thenAccept((message) -> {
            if (message == null) {
                logger.trace("Event {} will be permitted", event);
            } else {
                Set<String> servers = guardian.serversForBan();
                if (!servers.contains(destination.getServerInfo().getName())) {
                    event.getPlayer().sendMessage(message);
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                }
            }
        }));
    }

	@Subscribe(order = PostOrder.EARLY)
	public EventTask onServerSwitch(ServerPreConnectEvent event) {
		if (!event.getResult().isAllowed()) {
			logger.trace("Switch {} is already blocked", event);
			return null;
		}
		RegisteredServer destination = event.getResult().getServer().orElse(null);
		if (destination == null) {
			// Properly speaking, the API does not exclude this possibility. Javadocs do not document it
			logger.trace("Null destination server {}", event);
			return null;
		}
		Player player = event.getPlayer();
		return EventTask.resumeWhenComplete(guardian.checkServerSwitch(
				player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress(),
				destination.getServerInfo().getName()
		).thenAccept((message) -> {
			if (message != null) {
				event.setResult(ServerPreConnectEvent.ServerResult.denied());
				player.sendMessage(message);
			}
		}));
	}

    @Subscribe(order = PostOrder.LAST)
    public EventTask onKickFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(guardian.executeAndCheckConnection(
                player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress()
        ).thenAccept((message) -> {
            if (message == null) {
                logger.trace("Event {} will be permitted", event);
            } else {
                event.setResult(KickedFromServerEvent.DisconnectPlayer.create(message));
            }
        }));
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onCommand(CommandExecuteEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        if (!(event.getCommandSource() instanceof Player player)) {
            return null;
        }
        boolean isProxyCommand = server.getCommandManager().hasCommand(event.getCommand().split(" ")[0]);

        return EventTask.resumeWhenComplete(guardian.executeAndCheckConnection(
                player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress()
        ).thenAccept((message) -> {
            if (message == null) {
                logger.trace("Event {} will be permitted", event);
            } else {
                if (isProxyCommand) {
                    event.setResult(CommandExecuteEvent.CommandResult.denied());
                    player.sendMessage(message);
                }
            }
        }));
    }

}
