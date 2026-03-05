package io.github.rhusar.jgroups.discovery;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.util.MessageBatch;

/**
 * Example application demonstrating JGroups cluster discovery on Kubernetes.
 * <p>
 * Joins a JGroups cluster using the configured discovery protocol, then broadcasts a hello message
 * to all cluster members every 5 seconds. Received messages and view changes are logged.
 * The application runs until the JVM is shut down, at which point the channel is closed cleanly.
 * <p>
 * Configuration is controlled via system properties:
 * <ul>
 *   <li>{@code jgroups.config} — classpath path to the JGroups stack XML (default: {@code /dns_ping.xml})</li>
 *   <li>{@code jgroups.cluster_name} — name of the cluster to join (default: {@code jgroups-discovery-examples})</li>
 * </ul>
 *
 * @author Radoslav Husar
 */
public class JGroupsDiscoveryExample {

    private static final String CLUSTER_NAME = System.getProperty("jgroups.cluster_name", "jgroups-discovery-examples");
    private static final String CONFIG_FILE = System.getProperty("jgroups.config", "/dns_ping.xml");
    private static final Logger LOGGER = Logger.getLogger(JGroupsDiscoveryExample.class.getName());

    /**
     * Application entry point.
     *
     * @param args command-line arguments (not used)
     * @throws Exception if the channel cannot be created or the cluster join fails
     */
    public static void main(String[] args) throws Exception {
        Logger rootLog = Logger.getLogger("");
        rootLog.setLevel(Level.INFO);
        rootLog.getHandlers()[0].setLevel(Level.INFO);

        InputStream configuration = JGroupsDiscoveryExample.class.getResourceAsStream(CONFIG_FILE);
        JChannel channel = new JChannel(Objects.requireNonNull(configuration));

        LOGGER.info("getProtocols(): " + channel.getProtocolStack().getProtocols().toString());

        channel.setReceiver(new Receiver() {
            @Override
            public void viewAccepted(View view) {
                LOGGER.info("viewAccepted(): " + view);
            }

            @Override
            public void receive(Message msg) {
                LOGGER.info("receive(): " + msg.getObject());
            }

            @Override
            public void receive(MessageBatch batch) {
                batch.forEach(msg -> LOGGER.info("receive(): " + msg.getObject()));
            }
        });

        var executor = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            channel.close();
        }));

        channel.connect(CLUSTER_NAME);
        LOGGER.info("connect(): " + CLUSTER_NAME);

        executor.scheduleAtFixedRate(() -> {
            try {
                String text = "Hello from " + channel.getAddress();
                channel.send(null, text);
                LOGGER.info("send(): " + text);
            } catch (Exception e) {
                LOGGER.warning("send() failed: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);

        Thread.currentThread().join();
    }
}
