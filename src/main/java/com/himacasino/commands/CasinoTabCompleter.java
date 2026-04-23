package com.himacasino.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CasinoTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> sub = new ArrayList<>(Arrays.asList("roulette", "highlow", "horsewheel"));
            if (player.hasPermission("himacasino.admin")) {
                sub.addAll(Arrays.asList("setting", "setmachine", "delmachine"));
            }
            return StringUtil.copyPartialMatches(args[0], sub, new ArrayList<>());
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "roulette"    -> StringUtil.copyPartialMatches(args[1],
                        Arrays.asList("open"), new ArrayList<>());
                case "setmachine"  -> StringUtil.copyPartialMatches(args[1],
                        Arrays.asList("roulette"), new ArrayList<>());
                case "setting"     -> StringUtil.copyPartialMatches(args[1],
                        Arrays.asList("1","2","3","4","5","6"), new ArrayList<>());
                default            -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setmachine")) {
            return StringUtil.copyPartialMatches(args[2],
                    Arrays.asList("1","2","3","4","5","6"), new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
