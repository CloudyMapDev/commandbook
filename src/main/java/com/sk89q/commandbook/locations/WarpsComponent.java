/*
 * CommandBook
 * Copyright (C) 2011 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.commandbook.locations;

import com.google.common.collect.Lists;
import com.sk89q.commandbook.CommandBook;
import com.sk89q.commandbook.commands.PaginatedResult;
import com.sk89q.commandbook.session.SessionComponent;
import com.sk89q.commandbook.session.UserSession;
import com.sk89q.commandbook.util.InputUtil;
import com.sk89q.commandbook.util.entity.player.PlayerUtil;
import com.sk89q.commandbook.util.entity.player.iterators.TeleportPlayerIterator;
import com.sk89q.minecraft.util.commands.*;
import com.zachsthings.libcomponents.ComponentInformation;
import com.zachsthings.libcomponents.InjectComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@ComponentInformation(friendlyName = "Warps", desc = "Provides warps functionality")
public class WarpsComponent extends LocationsComponent {

    @InjectComponent private SessionComponent sessions;
    public WarpsComponent() {
        super("Warp");
    }

    public void enable() {
        super.enable();
        registerCommands(Commands.class);
    }

    public class Commands {
        @Command(aliases = {"warp"},
                usage = "[world] [target] <warp>", desc = "Teleport to a warp",
                flags = "s", min = 1, max = 3)
        public void warp(CommandContext args, CommandSender sender) throws CommandException {
            Iterable<Player> targets = null;
            NamedLocation warp = null;
            Location loc;

            // Detect arguments based on the number of arguments provided
            if (args.argsLength() == 1) {
                Player player = PlayerUtil.checkPlayer(sender);
                targets = Lists.newArrayList(player);
                warp = getManager().get(player.getWorld(), args.getString(0));
            } else if (args.argsLength() == 2) {
                targets = InputUtil.PlayerParser.matchPlayers(sender, args.getString(0));
                if (getManager().isPerWorld()) {
                    Player player = PlayerUtil.checkPlayer(sender);
                    warp = getManager().get(player.getWorld(), args.getString(1));
                } else {
                    warp = getManager().get(null, args.getString(1));
                }
            } else if (args.argsLength() == 3) {
                targets = InputUtil.PlayerParser.matchPlayers(sender, args.getString(1));
                warp = getManager().get(
                        InputUtil.LocationParser.matchWorld(sender, args.getString(0)), args.getString(2));
            }

            // Check permissions!
            for (Player target : targets) {
                if (target != sender) {
                    CommandBook.inst().checkPermission(sender, "commandbook.warp.teleport.other");
                    break;
                }
            }

            if (warp != null) {
                try {
                    CommandBook.inst().checkPermission(sender, "commandbook.warp.teleport");
                } catch (CommandPermissionsException e) {
                    CommandBook.inst().checkPermission(sender, "commandbook.warp.warp." + warp.getName());
                }
                loc = warp.getLocation();
            } else {
                throw new CommandException("A warp by the given name does not exist.");
            }

            (new TeleportPlayerIterator(sender, loc, args.hasFlag('s'))).iterate(targets);
        }

        @Command(aliases = {"setwarp"}, usage = "<warp> [location]", desc = "Set a warp", min = 1, max = 2)
        @CommandPermissions({"commandbook.warp.set"})
        public void setWarp(CommandContext args, CommandSender sender) throws CommandException {
            String warpName = args.getString(0);
            Location loc;
            Player player = null;

            // Detect arguments based on the number of arguments provided
            if (args.argsLength() == 1) {
                player = PlayerUtil.checkPlayer(sender);
                loc = player.getLocation();
            } else {
                loc = InputUtil.LocationParser.matchLocation(sender, args.getString(1));
                if (sender instanceof Player) {
                    player = (Player) sender;
                }
            }
            NamedLocation existing = getManager().get(loc.getWorld(), warpName);
            if (existing != null) {
                if (!existing.getCreatorName().equals(sender.getName())) {
                    CommandBook.inst().checkPermission(sender, "commandbook.warp.set.override");
                }
                if (!sessions.getSession(UserSession.class, sender).checkOrQueueConfirmed(args.getCommand() + " " + args.getJoinedStrings(0))) {
                    throw new CommandException("Warp already exists! Type /confirm to confirm overwriting");
                }
            }

            try {
                getManager().create(warpName, loc, player);
            } catch (IllegalArgumentException ex) {
                throw new CommandException("Invalid warp name!");
            }

            sender.sendMessage(ChatColor.YELLOW + "Warp '" + warpName + "' created.");
        }

        @Command(aliases = {"warps"}, desc = "Warp management")
        @NestedCommand({ManagementCommands.class})
        public void warps(CommandContext args, CommandSender sender) throws CommandException {
        }
    }

    public class ManagementCommands {

        @Command(aliases = {"info", "inf"}, usage = "<warpname> [world]",
                desc = "Get information about a warp", min = 1, max = 2
        )
        @CommandPermissions({"commandbook.warp.info"})
        public void infoCmd(CommandContext args, CommandSender sender) throws CommandException {
            World world;
            String warpName = args.getString(0);
            if (args.argsLength() == 2) {
                world = InputUtil.LocationParser.matchWorld(sender, args.getString(1));
            } else {
                world = PlayerUtil.checkPlayer(sender).getWorld();
            }
            info(warpName, world, sender);
        }

        @Command(aliases = {"del", "delete", "remove", "rem"}, usage = "<warpname> [world]",
                desc = "Remove a warp", min = 1, max = 2 )
        @CommandPermissions({"commandbook.warp.remove"})
        public void removeCmd(CommandContext args, CommandSender sender) throws CommandException {
            World world;
            String warpName = args.getString(0);
            if (args.argsLength() == 2) {
                world = InputUtil.LocationParser.matchWorld(sender, args.getString(1));
            } else {
                world = PlayerUtil.checkPlayer(sender).getWorld();
            }
            remove(warpName, world, sender);
        }


        @Command(aliases = {"list", "show"}, usage = "[ -p owner] [-w world] [page]",
                desc = "List warps", flags = "p:w:", min = 0, max = 1 )
        @CommandPermissions({"commandbook.warp.list"})
        public void listCmd(CommandContext args, CommandSender sender) throws CommandException {
            list(args, sender);
        }
    }

    @Override
    public PaginatedResult<NamedLocation> getListResult() {
        final String defaultWorld = CommandBook.server().getWorlds().get(0).getName();
        return new PaginatedResult<NamedLocation>(ChatColor.GOLD + "Warps") {
            @Override
            public String format(NamedLocation entry) {
                return ChatColor.BLUE + entry.getName().toUpperCase() + ChatColor.YELLOW
                        + " (Owner: " + ChatColor.WHITE + entry.getCreatorName()
                        + ChatColor.YELLOW + ", World: "
                        + ChatColor.WHITE + (entry.getWorldName() == null ? defaultWorld : entry.getWorldName())
                        + ChatColor.YELLOW + ")";
            }
        };
    }
}
