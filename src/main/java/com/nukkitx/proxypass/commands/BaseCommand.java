package com.nukkitx.proxypass.commands;

import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
public abstract class BaseCommand {

    protected ProxyPass proxy;

    public abstract String getName();

    public abstract String onExecute(String[] args, ProxyPlayerSession player);
}
