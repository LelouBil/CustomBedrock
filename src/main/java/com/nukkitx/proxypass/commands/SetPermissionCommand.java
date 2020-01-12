package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ChatColor;
import com.nukkitx.proxypass.Configuration;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.ArrayList;

public class SetPermissionCommand extends BaseCommand {
    public SetPermissionCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "setperm";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if(args.length != 3 || (!args[2].equals("true") && !args[2].equals("false"))) return "Usage : /setperm <Player> <Permission> <true|false>";
        String toSet = args[0];
        String perm = args[1];
        boolean add = Boolean.parseBoolean(args[2]);
        if (!player.getProxy().getCommandList().containsKey(perm)){
            StringBuilder sb = new StringBuilder();
            sb.append("Permission ").append(perm).append(" Doesn't exist !\n");
            sb.append("Valid permissions : \n");
            for (String k : player.getProxy().getCommandList().keySet()) {
                sb.append(k).append("\n");
            }
            return sb.toString();
        }
        Configuration configuration = player.getProxy().getConfiguration();
        boolean has = configuration.getPermissions().containsKey(toSet) && configuration.getPermissions().get(toSet).contains(perm);

        if((has && add) || (!has && !add)){
            return ChatColor.YELLOW + "Permission already set to " + add + " for " + toSet;
        }

        if(!configuration.getPermissions().containsKey(toSet)){
            configuration.getPermissions().put(toSet,new ArrayList<>());
        }
        if(add) {
            configuration.getPermissions().get(toSet).add(perm);
        }
        else {
            if(perm.equals(getName()) && toSet.equals(player.getDisplayName())){
                return ChatColor.RED + "Don't remove setperm permission from yourself !";
            }
            configuration.getPermissions().get(toSet).remove(perm);
        }
        player.getProxy().saveConfig();
        return ChatColor.WHITE + "Permission set to " +(add ? ChatColor.GREEN : ChatColor.RED) + add + ChatColor.WHITE + " for " + toSet;
    }
}
