package com.nukkitx.proxypass;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nukkitx.math.vector.Vector3f;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Getter
@ToString
public class Configuration {

    private Address proxy;
    private Address destination;


    @JsonProperty("max-homes")
    private int maxHomes = 2;

    @JsonProperty("pass-through")
    private boolean passingThrough = true;
    @JsonProperty("log-packets")
    private boolean loggingPackets = false;

    @JsonProperty("spawn-coords")
    @Setter
    private Vector3 spawnCoords = Vector3.ZERO;

    @JsonProperty("permissions")
    private HashMap<String,List<String>> permissions = new HashMap<>();

    @JsonProperty("home-list")
    private HashMap<String, HashMap<String,Vector3>> homeList = new HashMap<>();

    @JsonProperty("ignored-packets")
    private Set<String> ignoredPackets = Collections.emptySet();

    @JsonProperty("tpa-timeout")
    private long tpaTimeout = 30;

    @JsonProperty("tp-time")
    private long tpTime = 5;

    public static Configuration load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return ProxyPass.YAML_MAPPER.readValue(reader, Configuration.class);
        }
    }

    public static Configuration load(InputStream stream) throws IOException {
        return ProxyPass.YAML_MAPPER.readValue(stream, Configuration.class);
    }

    public static void save(Path path, Configuration configuration) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ProxyPass.YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, configuration);
        }
    }


    @Getter
    @ToString
    public static class Address {
        private String host;
        private int port;

        InetSocketAddress getAddress() {
            return new InetSocketAddress(host, port);
        }
    }

}
