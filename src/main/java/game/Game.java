package game;

import game.data.Coordinate2D;
import game.data.Coordinate3D;
import game.data.Dimension;
import game.data.WorldManager;
import game.data.chunk.ChunkFactory;
import game.data.chunk.Palette;
import game.protocol.HandshakeProtocol;
import game.protocol.LoginProtocol;
import game.protocol.Protocol;
import game.protocol.StatusProtocol;
import gui.GuiManager;
import net.sourceforge.argparse4j.inf.Namespace;
import packets.DataReader;
import packets.builder.ClientBoundGamePacketBuilder;
import packets.builder.ClientBoundHandshakePacketBuilder;
import packets.builder.ClientBoundLoginPacketBuilder;
import packets.builder.ClientBoundStatusPacketBuilder;
import packets.builder.PacketBuilder;
import packets.builder.ServerBoundGamePacketBuilder;
import packets.builder.ServerBoundHandshakePacketBuilder;
import packets.builder.ServerBoundLoginPacketBuilder;
import packets.builder.ServerBoundStatusPacketBuilder;
import proxy.CompressionManager;
import proxy.EncryptionManager;
import proxy.ProxyServer;

import java.io.File;
import java.nio.file.Paths;

/**
 * Class the manage the central configuration and set up.
 */
public abstract class Game {
    private static final int DEFAULT_VERSION = 340;
    private static NetworkMode mode = NetworkMode.STATUS;
    private static Dimension dimension = Dimension.OVERWORLD;

    private static DataReader serverBoundDataReader;
    private static DataReader clientBoundDataReader;
    private static EncryptionManager encryptionManager;
    private static CompressionManager compressionManager;
    private static String host;
    private static String exportDir;
    private static long seed;
    private static int portRemote;
    private static int portLocal;
    private static Coordinate3D playerPosition;
    private static String gamePath;
    private static VersionHandler versionHandler;
    private static int protocolVersion = DEFAULT_VERSION;
    private static int dataVersion;
    private static String gameVersion;

    public static int getDataVersion() {
        return dataVersion;
    }

    public static String getGameVersion() {
        return gameVersion;
    }

    public static EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public static CompressionManager getCompressionManager() {
        return compressionManager;
    }

    public static Dimension getDimension() {
        return dimension;
    }

    public static void setDimension(Dimension dimension) {
        Game.dimension = dimension;
    }

    public static Coordinate3D getPlayerPosition() {
        return playerPosition;
    }

    public static void setPlayerPosition(Coordinate3D newPos) {
        playerPosition = newPos;
    }


    public static long getSeed() {
        return seed;
    }

    /**
     * Parse arguments from the commandline.
     */
    public static void init(Namespace args) {
        host = args.getString("server");
        portRemote = args.getInt("port");
        portLocal = args.getInt("local-port");
        exportDir = args.getString("output");
        seed = args.getLong("seed");
        gamePath = args.getString("minecraft");

        Coordinate2D.setOffset(-args.getInt("center-x"), -args.getInt("center-z"));
        Coordinate3D.setOffset(-args.getInt("center-x"), -args.getInt("center-z"));

        Palette.setMaskBedrock(args.getBoolean("mask-bedrock"));

        File dir = Paths.get(exportDir, "region").toFile();
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }

        ChunkFactory.startChunkParserService();
        WorldManager.startSaveService();
        if (args.getBoolean("gui")) {
            GuiManager.showGui();
        }

        versionHandler = VersionHandler.createVersionHandler();
    }

    public static String getHost() {
        return host;
    }

    public static int getPortRemote() {
        return portRemote;
    }

    public static void startProxy() {
        encryptionManager = new EncryptionManager();
        serverBoundDataReader = DataReader.serverBound(encryptionManager);
        clientBoundDataReader = DataReader.clientBound(encryptionManager);
        compressionManager = new CompressionManager();

        setMode(NetworkMode.HANDSHAKE);

        ProxyServer proxy = new ProxyServer(portRemote, portLocal, host);
        proxy.runServer(serverBoundDataReader, clientBoundDataReader);
    }

    public static NetworkMode getMode() {
        return mode;
    }

    public static void setMode(NetworkMode mode) {
        Game.mode = mode;

        switch (mode) {
            case STATUS:
                PacketBuilder.setProtocol(new StatusProtocol());
                serverBoundDataReader.setBuilder(new ServerBoundStatusPacketBuilder());
                clientBoundDataReader.setBuilder(new ClientBoundStatusPacketBuilder());
                break;
            case LOGIN:
                PacketBuilder.setProtocol(new LoginProtocol());
                serverBoundDataReader.setBuilder(new ServerBoundLoginPacketBuilder());
                clientBoundDataReader.setBuilder(new ClientBoundLoginPacketBuilder());
                break;
            case GAME:
                PacketBuilder.setProtocol(getGameProtocol());
                serverBoundDataReader.setBuilder(new ServerBoundGamePacketBuilder());
                clientBoundDataReader.setBuilder(new ClientBoundGamePacketBuilder());
                break;
            case HANDSHAKE:
                PacketBuilder.setProtocol(new HandshakeProtocol());
                serverBoundDataReader.setBuilder(new ServerBoundHandshakePacketBuilder());
                clientBoundDataReader.setBuilder(new ClientBoundHandshakePacketBuilder());
                break;
        }
    }

    /**
     * Reset the connection when its lost.
     */
    public static void reset() {
        encryptionManager.reset();
        compressionManager.reset();
        serverBoundDataReader.reset();
        clientBoundDataReader.reset();
        setMode(NetworkMode.HANDSHAKE);
    }

    public static String getExportDirectory() {
        return exportDir;
    }

    public static String getGamePath() {
        return gamePath;
    }

    public static int getProtocolVersion() {
        return protocolVersion;
    }

    public static void setProtocolVersion(int protocolVersion) {
        Game.protocolVersion = protocolVersion;
    }

    public static Protocol getGameProtocol() {
        Protocol p = versionHandler.getProtocol(protocolVersion);
        Game.dataVersion = p.getDataVersion();
        Game.gameVersion = p.getVersion();
        WorldManager.setGlobalPalette(p.getVersion());
        System.out.println("Using protocol of game version " + p.getVersion() + " ("  + protocolVersion + ")");
        return p;
    }
}
