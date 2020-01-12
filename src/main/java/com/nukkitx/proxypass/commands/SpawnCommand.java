package com.nukkitx.proxypass.commands;

import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import com.nukkitx.proxypass.network.bedrock.session.UpstreamPacketHandler;

import java.util.Locale;
import java.util.UUID;

public class SpawnCommand extends BaseCommand {
    public SpawnCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        Vector3 spawn = player.getProxy().getConfiguration().getSpawnCoords();
        if(spawn == Vector3.ZERO) return "No spawn defined";
        player.sendMessage("Teleporting to spawn...");
        player. teleport(spawn);
        return "Done !";
    }
}
