package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ChatColor;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import com.nukkitx.proxypass.network.bedrock.session.UpstreamPacketHandler;

public class SetSpawnCommand extends BaseCommand {
    public SetSpawnCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "setspawn";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        player.getProxy().setEditSpawn(player.getAuthData().getIdentity());
        if(player.getPosition(getName(),this::onPosition)){
            return "Right click a block to define the spawnPoint";
        }else {
            return ChatColor.RED + "You still need to right click a block !";
        }
    }

    private void onPosition(Vector3 vector3,ProxyPlayerSession player) {
        proxy.getConfiguration().setSpawnCoords(vector3.up());
        proxy.saveConfig();
        player.sendMessage("Spawn defined at " + proxy.getConfiguration().getSpawnCoords().toString());
    }

}
