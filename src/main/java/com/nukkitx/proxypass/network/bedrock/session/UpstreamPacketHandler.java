package com.nukkitx.proxypass.network.bedrock.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.common.base.Preconditions;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.data.*;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import com.nukkitx.proxypass.ChatColor;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import com.nukkitx.proxypass.commands.BaseCommand;
import com.nukkitx.proxypass.network.bedrock.util.ForgeryUtils;
import io.netty.util.AsciiString;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.minidev.json.JSONObject;

import java.io.IOException;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {

    private final BedrockServerSession session;
    private final ProxyPass proxy;
    private JSONObject skinData;
    private JSONObject extraData;
    private ArrayNode chainData;
    private AuthData authData;
    private ProxyPlayerSession player;

    private static boolean validateChainData(JsonNode data) throws Exception {
        ECPublicKey lastKey = null;
        boolean validChain = false;
        for (JsonNode node : data) {
            JWSObject jwt = JWSObject.parse(node.asText());

            if (!validChain) {
                validChain = verifyJwt(jwt, EncryptionUtils.getMojangPublicKey());
            }

            if (lastKey != null) {
                verifyJwt(jwt, lastKey);
            }

            JsonNode payloadNode = ProxyPass.JSON_MAPPER.readTree(jwt.getPayload().toString());
            JsonNode ipkNode = payloadNode.get("identityPublicKey");
            Preconditions.checkState(ipkNode != null && ipkNode.getNodeType() == JsonNodeType.STRING, "identityPublicKey node is missing in chain");
            lastKey = EncryptionUtils.generateKey(ipkNode.asText());
        }
        return validChain;
    }

    private static boolean verifyJwt(JWSObject jwt, ECPublicKey key) throws JOSEException {
        return jwt.verify(new DefaultJWSVerifierFactory().createJWSVerifier(jwt.getHeader(), key));
    }

    @Override
    public boolean handle(LoginPacket packet) {
        int protocolVersion = packet.getProtocolVersion();

        if (protocolVersion != ProxyPass.PROTOCOL_VERSION) {
            PlayStatusPacket status = new PlayStatusPacket();
            if (protocolVersion > ProxyPass.PROTOCOL_VERSION) {
                status.setStatus(PlayStatusPacket.Status.FAILED_SERVER);
            } else {
                status.setStatus(PlayStatusPacket.Status.FAILED_CLIENT);
            }
        }
        session.setPacketCodec(ProxyPass.CODEC);

        JsonNode certData;
        try {
            certData = ProxyPass.JSON_MAPPER.readTree(packet.getChainData().toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Certificate JSON can not be read.");
        }

        JsonNode certChainData = certData.get("chain");
        if (certChainData.getNodeType() != JsonNodeType.ARRAY) {
            throw new RuntimeException("Certificate data is not valid");
        }
        chainData = (ArrayNode) certChainData;

        boolean validChain;
        try {
            validChain = validateChainData(certChainData);

            log.debug("Is player data valid? {}", validChain);
            JWSObject jwt = JWSObject.parse(certChainData.get(certChainData.size() - 1).asText());
            JsonNode payload = ProxyPass.JSON_MAPPER.readTree(jwt.getPayload().toBytes());

            if (payload.get("extraData").getNodeType() != JsonNodeType.OBJECT) {
                throw new RuntimeException("AuthData was not found!");
            }

            extraData = (JSONObject) jwt.getPayload().toJSONObject().get("extraData");

            this.authData = new AuthData(extraData.getAsString("displayName"),
                    UUID.fromString(extraData.getAsString("identity")), extraData.getAsString("XUID"));

            if (payload.get("identityPublicKey").getNodeType() != JsonNodeType.STRING) {
                throw new RuntimeException("Identity Public Key was not found!");
            }
            ECPublicKey identityPublicKey = EncryptionUtils.generateKey(payload.get("identityPublicKey").textValue());

            JWSObject clientJwt = JWSObject.parse(packet.getSkinData().toString());
            verifyJwt(clientJwt, identityPublicKey);
            skinData = clientJwt.getPayload().toJSONObject();

            initializeProxySession();
        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            proxy.getPlayers().remove(player.getDisplayName());
            throw new RuntimeException("Unable to complete login", e);
        }
        return true;
    }

    @Override
    public boolean handle(InventoryTransactionPacket packet) {
        if(player.getRequests().empty()) return false;
        if(packet.getTransactionType() != InventoryTransactionPacket.Type.ITEM_USE) return false;
        BiConsumer<Vector3,ProxyPlayerSession> cons = player.popRequest();
        cons.accept(Vector3.from3I(packet.getBlockPosition()),player);
        return true;
    }


    private void initializeProxySession() {
        log.debug("Initializing proxy session");
        proxy.newClient().connect(proxy.getTargetAddress()).whenComplete((downstream, throwable) -> {
            if (throwable != null) {
                log.error("Unable to connect to downstream server " + proxy.getTargetAddress(), throwable);
                return;
            }
            downstream.setPacketCodec(ProxyPass.CODEC);
            ProxyPlayerSession proxySession = new ProxyPlayerSession(this.session, downstream, this.proxy, this.authData);
            this.player = proxySession;

            SignedJWT authData = ForgeryUtils.forgeAuthData(proxySession.getProxyKeyPair(), extraData);
            JWSObject skinData = ForgeryUtils.forgeSkinData(proxySession.getProxyKeyPair(), this.skinData);
            chainData.remove(chainData.size() - 1);
            chainData.add(authData.serialize());
            JsonNode json = ProxyPass.JSON_MAPPER.createObjectNode().set("chain", chainData);
            AsciiString chainData;
            try {
                chainData = new AsciiString(ProxyPass.JSON_MAPPER.writeValueAsBytes(json));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            LoginPacket login = new LoginPacket();
            login.setChainData(chainData);
            login.setSkinData(AsciiString.of(skinData.serialize()));
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.sendPacketImmediately(login);
            this.session.setBatchedHandler(proxySession.getUpstreamBatchHandler());
            downstream.setBatchedHandler(proxySession.getDownstreamTailHandler());
            downstream.setLogging(true);
            downstream.setPacketHandler(new DownstreamPacketHandler(downstream, proxySession, this.proxy));

            log.debug("Downstream connected");
            proxy.getPlayers().put(player.getDisplayName(),player);
            //SkinUtils.saveSkin(proxySession, this.skinData);
        });
    }

    @Override
    public boolean handle(TextPacket packet) {
        log.info(packet.getType().toString() + ":" +packet.getMessage());
        return false;
    }

    @Override
    public boolean handle(AvailableCommandsPacket packet) {
        packet.getCommands().add(new CommandData("spawn","sp", Collections.emptyList(), (byte) 0,new CommandEnumData("sl",new String[]{"slt"},true),new CommandParamData[][]{}));
        session.sendPacketImmediately(packet);
        return true;
    }


    @Override
    public boolean handle(CommandRequestPacket data){
        String cmd = data.getCommand();
        String[] dt = cmd.substring(1).split(" ");
        String strcmd = dt[0];
        if(proxy.getCommandList().containsKey(strcmd)){
            if (dt.length == 1){
                dt = new String[]{};
            }
            else{
                dt = Arrays.copyOfRange(dt,1,dt.length);
            }
            BaseCommand command = proxy.getCommandList().get(strcmd);
            if(!player.hasPermission(command.getName())){
                player.sendMessage(ChatColor.RED + "You do not have the permission to do this !");
                return true;
            }
            player.sendMessage(command.onExecute(dt,player));
            return true;
        }
        return false;
    }
}
