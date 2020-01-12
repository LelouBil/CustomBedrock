package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ChatColor;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.HashMap;

public class DelHome extends BaseCommand {
    public DelHome(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "delhome";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if (args.length != 1) return ChatColor.RED + "Usage : /delhome [homename]";

        String homeName = args[0];
        HashMap<String, HashMap<String, Vector3>> homeList = proxy.getConfiguration().getHomeList();
        if(!homeList.containsKey(player.getDisplayName())){
            homeList.put(player.getDisplayName(),new HashMap<>());
        }
        if(!homeList.get(player.getDisplayName()).containsKey(homeName)){
            return ChatColor.RED + "A home named " + ChatColor.YELLOW + homeName +ChatColor.RED + " doesn't exists !";
        }
        homeList.get(player.getDisplayName()).remove(homeName);
        proxy.saveConfig();
        return "Home removed";
    }
}
