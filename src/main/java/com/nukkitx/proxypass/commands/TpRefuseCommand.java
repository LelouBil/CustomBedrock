package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;

public class TpRefuseCommand extends BaseCommand {
    public TpRefuseCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "tprefuse";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if(!TpaCommand.getTpRequests().containsKey(player.getDisplayName())){
            return "You don't have any requests to refuse";
        }
        TpaCommand.TPReq rq = TpaCommand.getTpRequests().get(player.getDisplayName());
        TpaCommand.getTpRequests().remove(player.getDisplayName());
        if(System.currentTimeMillis() > rq.getTimestamp()){
            return "You don't have any requests to refuse";
        }
        if(proxy.getPlayers().containsKey(rq.getPlayer())){
            ProxyPlayerSession other = proxy.getPlayers().get(rq.getPlayer());
            other.sendMessage(player.getDisplayName() + " refused your teleport request");
        }
        return "Refused request";
    }
}
