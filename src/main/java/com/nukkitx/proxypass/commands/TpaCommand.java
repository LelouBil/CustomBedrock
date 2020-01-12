package com.nukkitx.proxypass.commands;

import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Hashtable;

public class TpaCommand extends BaseCommand {

    @Getter
    protected static Hashtable<String,TPReq> TpRequests = new Hashtable<>();

    public TpaCommand(ProxyPass proxy) {
        super(proxy);
    }

    @Override
    public String getName() {
        return "tpa";
    }

    @Override
    public String onExecute(String[] args, ProxyPlayerSession player) {
        if(args.length != 1) return "Usage : /tpa <player>";
        if(!proxy.getPlayers().containsKey(player.getDisplayName())){
            return "Player is not online";
        }
        TpRequests.put(args[0],new TPReq(player.getDisplayName(),System.currentTimeMillis() + proxy.getConfiguration().getTpaTimeout() * 1000));
        return "Request sent, " + args[0] + " has " + proxy.getConfiguration().getTpaTimeout() + " seconds to accept";
    }

    @Getter @AllArgsConstructor
    protected static class TPReq {
        private String player;
        private long timestamp;
    }
}
