package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

public class StopCommand extends BaseCommand {
    public StopCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        proxy.shutdown();
        return "Stopping server";
    }
}
