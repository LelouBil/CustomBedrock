package com.nukkitx.proxypass;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.nbt.stream.NBTInputStream;
import com.nukkitx.nbt.stream.NBTOutputStream;
import com.nukkitx.nbt.tag.Tag;
import com.nukkitx.protocol.bedrock.BedrockClient;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.BedrockServer;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.v389.Bedrock_v389;
import com.nukkitx.proxypass.commands.BaseCommand;
import com.nukkitx.proxypass.network.ProxyBedrockEventHandler;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
@Getter
public class ProxyPass {
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public static final YAMLMapper YAML_MAPPER = (YAMLMapper) new YAMLMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public static final String MINECRAFT_VERSION;
    public static final BedrockPacketCodec CODEC = Bedrock_v389.V389_CODEC;
    public static final int PROTOCOL_VERSION = CODEC.getProtocolVersion();
    private static final DefaultPrettyPrinter PRETTY_PRINTER = new DefaultPrettyPrinter();
    private static InputStream stdout;
    private static BufferedReader outputReader;

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
    private BedrockServer bedrockServer;
    private final Set<BedrockClient> clients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    @Getter(AccessLevel.NONE)
    private final Set<Class<?>> ignoredPackets = Collections.newSetFromMap(new IdentityHashMap<>());
    private InetSocketAddress targetAddress;
    private InetSocketAddress proxyAddress;
    private Configuration configuration;
    private Path baseDir;
    private Path sessionsDir;
    private Path dataDir;
    private HashMap<String, BaseCommand> commandList = new HashMap<String, BaseCommand>();
    @Setter
    private UUID editSpawn = null;
    private Path configPath;
    private Hashtable<String,ProxyPlayerSession> players = new Hashtable<>();
    private static Process vanillaServer;
    private static OutputStream stdin;
    private static BufferedWriter inputWriter;

    public static void main(String[] args) {
        log.info("Launching default server");
        executorService = Executors.newFixedThreadPool(10);
        ProxyPass proxy = new ProxyPass();
        Thread serverThread = new Thread(() -> {
            ProcessBuilder pb = new ProcessBuilder("/home/container/server/bedrock_server");
            pb.directory(new File("/home/container/server"));


            try {
                vanillaServer = pb.start();
                stdin = vanillaServer.getOutputStream();
                stdout = vanillaServer.getInputStream();
                outputReader = new BufferedReader(new InputStreamReader(stdout));
                inputWriter = new BufferedWriter(new OutputStreamWriter(stdin));
                inputWriter.flush();
                System.out.println("got there");
                String line;
                while ((line = outputReader.readLine()) != null && proxy.running.get()){
                    System.out.println(line);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                try {
                    inputWriter.flush();
                    inputWriter.write("stop");
                    inputWriter.newLine();
                    stdin.close();
                } catch (IOException ignored) {
                }
            }
        });
        //serverThread.setDaemon(true);
        executorService.execute(serverThread);
        try {
            proxy.boot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void boot() throws IOException {
        loadConfig();

        proxyAddress = configuration.getProxy().getAddress();
        targetAddress = configuration.getDestination().getAddress();

        configuration.getIgnoredPackets().forEach(s -> {
            try {
                ignoredPackets.add(Class.forName("com.nukkitx.protocol.bedrock.packet." + s));
            } catch (ClassNotFoundException e) {
                log.warn("No packet with name {}", s);
            }
        });

        baseDir = Paths.get(".").toAbsolutePath();
        sessionsDir = baseDir.resolve("sessions");
        dataDir = baseDir.resolve("data");
        Files.createDirectories(sessionsDir);
        Files.createDirectories(dataDir);

        log.info("Getting commands");
        Reflections r = new Reflections("com.nukkitx.proxypass");
        Set<Class<? extends BaseCommand>> commands = r.getSubTypesOf(BaseCommand.class);
        commands.forEach(c -> {
            try {
                log.debug("Discovered " + c.getCanonicalName());
                BaseCommand cmd = c.getDeclaredConstructor(ProxyPass.class).newInstance(this);
                commandList.put(cmd.getName(),cmd);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }

        });
        log.info("Commands done");
        saveConfig();

        log.info("Loading server...");
        this.bedrockServer = new BedrockServer(this.proxyAddress);
        this.bedrockServer.setHandler(new ProxyBedrockEventHandler(this));
        this.bedrockServer.bind().join();
        log.info("RakNet server started on {}", proxyAddress);

        loop();
    }

    public boolean loadConfig() {
        log.info("Loading configuration...");
        configPath = Paths.get(".").resolve("config.yml");
        try {
        if (Files.notExists(configPath) || !Files.isRegularFile(configPath)) {
                Files.copy(ProxyPass.class.getClassLoader().getResourceAsStream("config.yml"), configPath, StandardCopyOption.REPLACE_EXISTING);
        }
        configuration = Configuration.load(configPath);
        return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public BedrockClient newClient() {
        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", ThreadLocalRandom.current().nextInt(20000, 60000));
        BedrockClient client = new BedrockClient(bindAddress);
        this.clients.add(client);
        client.bind().join();
        return client;
    }

    private static ExecutorService executorService;

    private void loop() {

        Thread input = new Thread(() -> {
            Scanner s = new Scanner(System.in);
            while (s.hasNextLine() && running.get()) {
                String c = s.nextLine();
                ProxyPass.this.runCommand(c);
                if(c.equals("stop")){
                    shutdown();
                    return;
                }
            }
        });
        executorService.execute(input);
        while (running.get()) {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        executorService.shutdownNow();
        DisconnectPacket p = new DisconnectPacket();
        p.setKickMessage("Server closed");

        // Shutdown
        this.clients.forEach(c -> {
            c.getSession().sendPacketImmediately(p);
        });
        this.bedrockServer.close();

    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            synchronized (this) {
                this.notify();
            }
        }
    }

    public void saveNBT(String dataName, Tag<?> dataTag) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             NBTOutputStream nbtOutputStream = NbtUtils.createNetworkWriter(outputStream)){
            nbtOutputStream.write(dataTag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Tag<?> loadNBT(String dataName) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (InputStream inputStream = Files.newInputStream(path);
             NBTInputStream nbtInputStream = NbtUtils.createNetworkReader(inputStream)){
            return nbtInputStream.readTag();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveJson(String name, Object object) {
        Path outPath = dataDir.resolve(name);
        try (OutputStream outputStream = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            ProxyPass.JSON_MAPPER.writer(PRETTY_PRINTER).writeValue(outputStream, object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T loadJson(String name, TypeReference<T> reference) {
        Path path = dataDir.resolve(name);
        try (InputStream inputStream = Files.newInputStream(path, StandardOpenOption.READ)) {
            return ProxyPass.JSON_MAPPER.readValue(inputStream, reference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isIgnoredPacket(Class<?> clazz) {
        return this.ignoredPackets.contains(clazz);
    }

    @SneakyThrows
    public void runCommand(String command) {
        inputWriter.write(command);
        inputWriter.newLine();
        inputWriter.flush();
        //inputWriter.close();
    }

    public void saveConfig() {
        try {
            Configuration.save(configPath,configuration);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
