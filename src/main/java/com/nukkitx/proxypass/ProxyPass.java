package com.nukkitx.proxypass;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nukkitx.nbt.stream.NBTOutputStream;
import com.nukkitx.nbt.stream.NetworkDataOutputStream;
import com.nukkitx.nbt.tag.Tag;
import com.nukkitx.network.raknet.RakNetClient;
import com.nukkitx.network.raknet.RakNetServer;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.session.BedrockSession;
import com.nukkitx.protocol.bedrock.v354.Bedrock_v354;
import com.nukkitx.protocol.bedrock.wrapper.WrappedPacket;
import com.nukkitx.proxypass.network.NukkitSessionManager;
import com.nukkitx.proxypass.network.ProxyRakNetEventListener;
import com.nukkitx.proxypass.network.bedrock.session.DownstreamPacketHandler;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import com.nukkitx.proxypass.network.bedrock.session.UpstreamPacketHandler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
@Getter
public class ProxyPass {
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public static final YAMLMapper YAML_MAPPER = (YAMLMapper) new YAMLMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public static final String MINECRAFT_VERSION;
    public static final BedrockPacketCodec CODEC = Bedrock_v354.V354_CODEC;
    public static final int PROTOCOL_VERSION = CODEC.getProtocolVersion();
    private static final DefaultPrettyPrinter PRETTY_PRINTER = new DefaultPrettyPrinter();

    static {
        DefaultIndenter indenter = new DefaultIndenter("    ", "\n");
        PRETTY_PRINTER.indentArraysWith(indenter);
        PRETTY_PRINTER.indentObjectsWith(indenter);
        String minecraftVersion;

        Package mainPackage = ProxyPass.class.getPackage();
        try {
            minecraftVersion = mainPackage.getImplementationVersion().split("-")[0];
        } catch (NullPointerException e) {
            minecraftVersion = "0.0.0";
        }
        MINECRAFT_VERSION = minecraftVersion;
    }

    private final AtomicBoolean running = new AtomicBoolean(true);
    private NukkitSessionManager sessionManager = new NukkitSessionManager();
    private RakNetServer<BedrockSession<ProxyPlayerSession>> rakNetServer;
    private RakNetClient<BedrockSession<ProxyPlayerSession>> rakNetClient;
    private InetSocketAddress targetAddress;
    private InetSocketAddress proxyAddress;
    private Configuration configuration;
    private Path baseDir;
    private Path sessionsDir;
    private Path dataDir;

    public static void main(String[] args) {
        ProxyPass proxy = new ProxyPass();
        try {
            proxy.boot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void boot() throws IOException {
        log.info("Loading configuration...");
        Path configPath = Paths.get(".").resolve("config.yml");
        if (Files.notExists(configPath) || !Files.isRegularFile(configPath)) {
            Files.copy(ProxyPass.class.getClassLoader().getResourceAsStream("config.yml"), configPath, StandardCopyOption.REPLACE_EXISTING);
        }

        configuration = Configuration.load(configPath);

        proxyAddress = configuration.getProxy().getAddress();
        targetAddress = configuration.getDestination().getAddress();

        baseDir = Paths.get(".").toAbsolutePath();
        sessionsDir = baseDir.resolve("sessions");
        dataDir = baseDir.resolve("data");
        Files.createDirectories(sessionsDir);
        Files.createDirectories(dataDir);

        log.info("Loading server...");
        RakNetServer.Builder<BedrockSession<ProxyPlayerSession>> builder = RakNetServer.builder();
                builder.eventListener(new ProxyRakNetEventListener())
                .address(proxyAddress)
                .packet(WrappedPacket::new, 0xfe)
                .sessionManager(sessionManager)
                .sessionFactory(rakNetSession -> {
                    BedrockSession<ProxyPlayerSession> session = new BedrockSession<>(rakNetSession);
                    session.setHandler(new UpstreamPacketHandler(session, this));
                    return session;
                });
        rakNetServer = builder.build();
        if (rakNetServer.bind()) {
            log.info("RakNet server started on {}", proxyAddress);
        } else {
            log.fatal("RakNet server was not able to bind to {}", proxyAddress);
        }
        rakNetClient = new RakNetClient.Builder<BedrockSession<ProxyPlayerSession>>()
                .packet(WrappedPacket::new, 0xfe)
                .sessionFactory(rakNetSession -> {
                    BedrockSession<ProxyPlayerSession> session = new BedrockSession<>(rakNetSession, CODEC);
                    session.setHandler(new DownstreamPacketHandler(session, this));
                    return session;
                })
                .sessionManager(sessionManager)
                .build();
        log.info("RakNet client ready for connections to {}", targetAddress);
        loop();
    }

    private void loop() {
        Lock lock = new ReentrantLock();

        while (running.get()) {
            lock.lock();
            try {
                lock.newCondition().await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //Ignore
            }
            lock.unlock();
        }
    }

    public void shutdown() {
        running.compareAndSet(false, true);
        rakNetClient.close();
        rakNetServer.close();
    }

    public void saveData(String dataName, Tag<?> dataTag) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)){
            NBTOutputStream nbtOutputStream = new NBTOutputStream(new NetworkDataOutputStream(outputStream));
            nbtOutputStream.write(dataTag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveObject(String name, Object object) {
        Path outPath = dataDir.resolve(name);
        try {
            OutputStream outputStream = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            ProxyPass.JSON_MAPPER.writer(PRETTY_PRINTER).writeValue(outputStream, object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
