package com.nukkitx.proxypass.network.bedrock.session;

import com.nukkitx.network.util.DisconnectReason;
import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.handler.BatchHandler;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.TextPacket;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.Vector3;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Log4j2
@Getter
public class ProxyPlayerSession {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final BedrockServerSession upstream;
    private final BedrockClientSession downstream;
    private final ProxyPass proxy;
    private final AuthData authData;
    private final Path dataPath;
    private final Path logPath;
    private final long timestamp = System.currentTimeMillis();
    @Getter(AccessLevel.PACKAGE)
    private final KeyPair proxyKeyPair = EncryptionUtils.createKeyPair();
    private final Deque<String> logBuffer = new ArrayDeque<>();
    private volatile boolean closed = false;

    public ProxyPlayerSession(BedrockServerSession upstream, BedrockClientSession downstream, ProxyPass proxy, AuthData authData) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.proxy = proxy;
        this.authData = authData;
        this.dataPath = proxy.getSessionsDir().resolve(this.authData.getDisplayName() + '-' + timestamp);
        this.logPath = dataPath.resolve("packets.log");
        this.upstream.addDisconnectHandler(reason -> {
            if (reason != DisconnectReason.DISCONNECTED) {
                if(!this.downstream.isClosed())this.downstream.disconnect();
            }
        });
        try {
            Files.createDirectories(dataPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (proxy.getConfiguration().isLoggingPackets()) {
            executor.scheduleAtFixedRate(this::flushLogBuffer, 5, 5, TimeUnit.SECONDS);
        }
    }

    public void sendMessage(String msg) {
        TextPacket p = new TextPacket();
        p.setType(TextPacket.Type.SYSTEM);
        p.setSourceName("");
        p.setXuid("");
        p.setPlatformChatId("");
        p.setMessage(msg);
        getUpstream().sendPacketImmediately(p);
    }

    public BatchHandler getUpstreamBatchHandler() {
        return new ProxyBatchHandler(downstream, true);
    }

    public BatchHandler getDownstreamTailHandler() {
        return new ProxyBatchHandler(upstream, false);
    }

    private void log(Supplier<String> supplier) {
        if (proxy.getConfiguration().isLoggingPackets()) {
            synchronized (logBuffer) {
                logBuffer.addLast(supplier.get());
            }
        }
    }

    private void flushLogBuffer() {
        synchronized (logBuffer) {
            try {
                Files.write(logPath, logBuffer, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                logBuffer.clear();
            } catch (IOException e) {
                log.error("Unable to flush packet log", e);
            }
        }
    }

    public boolean hasPermission(String name) {
        HashMap<String, List<String>> permissions = proxy.getConfiguration().getPermissions();
        if(permissions.containsKey("@a") &&  permissions.get("@a").contains(name)) return true;
        if(!permissions.containsKey(this.getDisplayName())) return false;
        return permissions.get(this.getDisplayName()).contains(name);
    }

    public String getDisplayName() {
        return authData.getDisplayName();
    }

    public BiConsumer<Vector3, ProxyPlayerSession> popRequest() {
        BiConsumer<Vector3, ProxyPlayerSession> rq = requests.pop();
        waiting.pop();
        return rq;
    }

    public void teleport(Vector3 position) {
        String command  = String.format(Locale.US,"tp %1$s %2$f %3$f %4$f",getDisplayName(),position.getX(),position.getY(),position.getZ());
        getProxy().runCommand(command);
    }

    public void teleport(ProxyPlayerSession player){
        getProxy().runCommand("tp " + getDisplayName() + " " + player.getDisplayName());
    }

    private class ProxyBatchHandler implements BatchHandler {
        private final BedrockSession session;
        private final String logPrefix;

        private ProxyBatchHandler(BedrockSession session, boolean upstream) {
            this.session = session;
            this.logPrefix = upstream ? "[SERVER BOUND]  -  " : "[CLIENT BOUND]  -  ";
        }

        @Override
        public void handle(BedrockSession session, ByteBuf compressed, Collection<BedrockPacket> packets) {
            boolean wrapperHandled = !ProxyPlayerSession.this.proxy.getConfiguration().isPassingThrough();
            List<BedrockPacket> unhandled = new ArrayList<>();
            for (BedrockPacket packet : packets) {
                if (session.isLogging() && log.isTraceEnabled() && !proxy.isIgnoredPacket(packet.getClass())) {
                    log.trace(this.logPrefix + " {}: {}", session.getAddress(), packet);
                }
                ProxyPlayerSession.this.log(() -> logPrefix + packet.toString());

                BedrockPacketHandler handler = session.getPacketHandler();

                if (handler != null && packet.handle(handler)) {
                    wrapperHandled = true;
                } else {
                    unhandled.add(packet);
                }
            }

            if (!wrapperHandled) {
                compressed.resetReaderIndex();
                this.session.sendWrapped(compressed, true);
            } else if (!unhandled.isEmpty()) {
                this.session.sendWrapped(unhandled, true);
            }
        }
    }


    private Stack<BiConsumer<Vector3, ProxyPlayerSession>> requests = new Stack<BiConsumer<Vector3, ProxyPlayerSession>>();
    private Stack<String> waiting = new Stack<>();

    private HashMap<String,Object> playerCache = new HashMap<>();

    public boolean getPosition(String key, BiConsumer<Vector3,ProxyPlayerSession> posConsumer){
        if(waiting.contains(key)) return false;
        waiting.push(key);
        requests.push(posConsumer);
        return true;
    }


}
