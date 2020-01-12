package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ChatColor;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

public class ReloadCommand extends BaseCommand {
    public ReloadCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        return  proxy.loadConfig() ? ChatColor.GREEN +  "Reload done" : ChatColor.RED + "Error during reload";
    }
}
