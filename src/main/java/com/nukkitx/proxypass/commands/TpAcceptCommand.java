package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.concurrent.TimeUnit;

public class TpAcceptCommand extends BaseCommand {
    public TpAcceptCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "tpaccept";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if(!TpaCommand.getTpRequests().containsKey(player.getDisplayName())){
            return "You don't have any requests to accept";
        }
        TpaCommand.TPReq rq = TpaCommand.getTpRequests().get(player.getDisplayName());
        if(System.currentTimeMillis() > rq.getTimestamp()){
            TpaCommand.getTpRequests().remove(player.getDisplayName());
            return "You don't have any requests to accept";
        }
        if(!proxy.getPlayers().containsKey(rq.getPlayer())){
            TpaCommand.getTpRequests().remove(player.getDisplayName());
            return "Sender is not online";
        }
        ProxyPlayerSession session = proxy.getPlayers().get(rq.getPlayer());
        long time = proxy.getConfiguration().getTpTime();
        String msg = "Request accepted, teleporting in " + time + " seconds";
        player.sendMessage(msg);
        session.sendMessage(msg);
        try {
            TimeUnit.SECONDS.sleep(5);
            session.teleport(player);
            return "Teleported";
        } catch (InterruptedException e) {
            e.printStackTrace();
            return "Error";
        }
    }
}
