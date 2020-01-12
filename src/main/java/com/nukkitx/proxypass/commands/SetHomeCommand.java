package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ChatColor;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.HashMap;

public class SetHomeCommand extends BaseCommand {
    public SetHomeCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "sethome";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if (args.length != 1) return ChatColor.RED + "Usage : /sethome [homename]";

        String homeName = args[0];
        HashMap<String, HashMap<String, Vector3>> homeList = proxy.getConfiguration().getHomeList();
        if(!homeList.containsKey(player.getDisplayName())){
            homeList.put(player.getDisplayName(),new HashMap<>());
        }
        if(homeList.get(player.getDisplayName()).containsKey(homeName)){
            return ChatColor.RED + "A home named " + ChatColor.YELLOW + homeName +ChatColor.RED + " already exists !";
        }

        if(homeList.size() + 1 > proxy.getConfiguration().getMaxHomes()){
            return ChatColor.RED + "You already have the maximum amount of homes !";
        }

        if(player.getPosition(getName(),this::onPosition)){
            player.getPlayerCache().put(getName(),homeName);
            return "Right click a block to set your home";
        }
        else {
            return "You need to right click a block !";
        }
    }

    private void onPosition(Vector3 vector3, ProxyPlayerSession player) {
        String homename = (String) player.getPlayerCache().get(getName());
        proxy.getConfiguration().getHomeList().get(player.getDisplayName()).put(homename,vector3.up());
        proxy.saveConfig();
        player.sendMessage(ChatColor.GREEN + "Home " + homename + " defined !");
    }
}
