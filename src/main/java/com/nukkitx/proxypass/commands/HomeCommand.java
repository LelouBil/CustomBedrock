package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.HashMap;

public class HomeCommand extends BaseCommand {
    public HomeCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "home";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if(args.length != 1) return "Usage : /home [homename]";
        HashMap<String, HashMap<String, Vector3>> homeList = proxy.getConfiguration().getHomeList();
        if(!homeList.containsKey(player.getDisplayName())){
            homeList.put(player.getDisplayName(),new HashMap<>());
        }
        if(!homeList.get(player.getDisplayName()).containsKey(args[0])){
            return "No home named " + args[0];
        }
        Vector3 home = homeList.get(player.getDisplayName()).get(args[0]);
        player.teleport(home);
        return "Teleported to " + args[0];
    }
}
