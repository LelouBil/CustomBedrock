package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

public class PosCommand extends BaseCommand {
    public PosCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "pos";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        player.getPosition(getName(),this::onPos);
        return "Right click any block to get it's position";
    }

    private void onPos(Vector3 vector3, ProxyPlayerSession proxyPlayerSession) {
        proxyPlayerSession.sendMessage(vector3.toString());
    }
}
