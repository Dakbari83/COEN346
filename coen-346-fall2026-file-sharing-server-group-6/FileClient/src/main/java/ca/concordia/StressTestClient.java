package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stress test for the file sharing server.
 *
 * Starts many clients at the same moment. Every client opens its own connection and
 * sends a mix of READ and WRITE requests on one shared file, plus a LIST and two
 * malformed requests. The test then reports how many requests got a valid answer,
 * how long the clients waited, and whether any client ever read a file that was
 * only partially written (a sign of missing synchronization).
 *
 * Each client pauses briefly between requests, as a person would, so it keeps its
 * connection open for a while. A server that serves one client at a time makes the
 * other clients wait, and many of them will time out.
 *
 * Usage (all arguments are optional):
 *   java ca.concordia.StressTestClient [clients] [rounds] [writeRatio] [pauseMs] [host] [port]
 *   default:                            100       10       0.2          50        localhost 12345
 *
 * The test needs CREATE, WRITE, READ and LIST to be implemented. A correct
 * multithreaded server answers every request, with no torn reads.
 */
public class StressTestClient {

    // Content written to the shared file: one repeated letter, longer than one block
    // (128 bytes), so that a write always spans more than one data block.
    private static final int CONTENT_LENGTH = 200;
    private static final String SHARED_FILE = "shared";
    private static final int TIMEOUT_MS = 10_000;

    private static final AtomicInteger requests = new AtomicInteger();
    private static final AtomicInteger validReplies = new AtomicInteger();
    private static final AtomicInteger expectedErrors = new AtomicInteger();
    private static final AtomicInteger unexpectedErrors = new AtomicInteger();
    private static final AtomicInteger tornReads = new AtomicInteger();
    private static final AtomicInteger unexpectedReplies = new AtomicInteger();
    private static final AtomicInteger timeouts = new AtomicInteger();
    private static final AtomicInteger connectionFailures = new AtomicInteger();
    private static final AtomicInteger completedClients = new AtomicInteger();
    private static final AtomicLong totalLatencyNanos = new AtomicLong();
    private static final AtomicLong maxLatencyNanos = new AtomicLong();

    public static void main(String[] args) throws Exception {
        int clients = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        double writeRatio = args.length > 2 ? Double.parseDouble(args[2]) : 0.2;
        int pauseMs = args.length > 3 ? Integer.parseInt(args[3]) : 50;
        String host = args.length > 4 ? args[4] : "localhost";
        int port = args.length > 5 ? Integer.parseInt(args[5]) : 12345;

        System.out.printf("Stress test: %d clients x %d requests each (%.0f%% writes, %d ms pause) on %s:%d%n",
                clients, rounds, writeRatio * 100, pauseMs, host, port);

        // Set up the shared file with a single connection before the test starts.
        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            s.setSoTimeout(TIMEOUT_MS);
            out.println("CREATE " + SHARED_FILE);
            String created = in.readLine();
            System.out.println("Setup  CREATE " + SHARED_FILE + " -> " + created);
            out.println("WRITE " + SHARED_FILE + " " + "A".repeat(CONTENT_LENGTH));
            String written = in.readLine();
            System.out.println("Setup  WRITE  " + SHARED_FILE + " -> " + written);
            if (written == null || !written.startsWith("SUCCESS")) {
                System.out.println("Setup failed: the server must implement CREATE and WRITE before this test can run.");
                System.out.println("(If '" + SHARED_FILE + "' already exists from a previous run, restart the server with a fresh file system.)");
                return;
            }
        } catch (Exception e) {
            System.out.println("Setup failed (" + e + "). Is the server running?");
            return;
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            final int id = i;
            Thread t = new Thread(() -> runClient(id, rounds, writeRatio, pauseMs, host, port, start));
            threads.add(t);
            t.start();
        }

        long begin = System.nanoTime();
        start.countDown(); // all clients start at the same moment
        for (Thread t : threads) {
            t.join();
        }
        double seconds = (System.nanoTime() - begin) / 1e9;

        int answered = validReplies.get() + expectedErrors.get() + unexpectedErrors.get()
                + unexpectedReplies.get() + tornReads.get();
        System.out.println();
        System.out.println("=== Results ===");
        System.out.printf("Total time:              %.2f s%n", seconds);
        System.out.printf("Clients completed:       %d / %d%n", completedClients.get(), clients);
        System.out.printf("Requests sent:           %d%n", requests.get());
        System.out.printf("Replies received:        %d%n", answered);
        System.out.printf("  correct replies:       %d%n", validReplies.get());
        System.out.printf("  ERROR to bad requests: %d (expected: 2 per client)%n", expectedErrors.get());
        System.out.printf("  ERROR to good requests: %d%n", unexpectedErrors.get());
        System.out.printf("Average / max latency:   %.1f ms / %.1f ms%n",
                answered == 0 ? 0 : totalLatencyNanos.get() / 1e6 / answered,
                maxLatencyNanos.get() / 1e6);
        System.out.printf("Timeouts (no reply %ds): %d%n", TIMEOUT_MS / 1000, timeouts.get());
        System.out.printf("Connection failures:     %d%n", connectionFailures.get());
        System.out.printf("Unexpected replies:      %d%n", unexpectedReplies.get());
        System.out.printf("Torn reads:              %d%n", tornReads.get());
        System.out.println();
        if (completedClients.get() == clients && timeouts.get() == 0 && connectionFailures.get() == 0
                && tornReads.get() == 0 && unexpectedReplies.get() == 0 && unexpectedErrors.get() == 0
                && expectedErrors.get() == 2 * clients) {
            System.out.println("PASS: every client was served and every read was consistent.");
        } else {
            System.out.println("FAIL: see the counts above.");
        }
    }

    private static void runClient(int id, int rounds, double writeRatio, int pauseMs, String host, int port,
                                  CountDownLatch start) {
        Random random = new Random(id);
        try {
            start.await();
        } catch (InterruptedException e) {
            return;
        }
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            socket.setSoTimeout(TIMEOUT_MS);

            // Malformed requests: the server must answer with an ERROR and keep the connection open.
            expectError(out, in, "HELLO");
            expectError(out, in, "READ");

            for (int r = 0; r < rounds; r++) {
                Thread.sleep(pauseMs);
                if (random.nextDouble() < writeRatio) {
                    char letter = (char) ('A' + random.nextInt(26));
                    String reply = send(out, in, "WRITE " + SHARED_FILE + " " + String.valueOf(letter).repeat(CONTENT_LENGTH));
                    classify(reply, "SUCCESS");
                } else {
                    String reply = send(out, in, "READ " + SHARED_FILE);
                    checkRead(reply);
                }
            }

            String list = send(out, in, "LIST");
            if (list.startsWith("ERROR")) {
                unexpectedErrors.incrementAndGet();
            } else if (list.contains(SHARED_FILE)) {
                validReplies.incrementAndGet();
            } else {
                unexpectedReplies.incrementAndGet();
            }
            completedClients.incrementAndGet();
        } catch (SocketTimeoutException e) {
            timeouts.incrementAndGet();
        } catch (java.net.ConnectException e) {
            connectionFailures.incrementAndGet();
        } catch (Exception e) {
            // e.g. the server closed the connection after an exception
            connectionFailures.incrementAndGet();
        }
    }

    /** Sends one request and waits for the one-line reply. Throws on timeout or closed connection. */
    private static String send(PrintWriter out, BufferedReader in, String request) throws Exception {
        requests.incrementAndGet();
        long t0 = System.nanoTime();
        out.println(request);
        String reply = in.readLine();
        if (reply == null) {
            throw new java.io.EOFException("Server closed the connection after: " + request);
        }
        long latency = System.nanoTime() - t0;
        totalLatencyNanos.addAndGet(latency);
        maxLatencyNanos.accumulateAndGet(latency, Math::max);
        return reply;
    }

    private static void expectError(PrintWriter out, BufferedReader in, String request) throws Exception {
        String reply = send(out, in, request);
        if (reply.startsWith("ERROR")) {
            expectedErrors.incrementAndGet();
        } else {
            unexpectedReplies.incrementAndGet();
        }
    }

    private static void classify(String reply, String expectedPrefix) {
        if (reply.startsWith(expectedPrefix)) {
            validReplies.incrementAndGet();
        } else if (reply.startsWith("ERROR")) {
            unexpectedErrors.incrementAndGet();
        } else {
            unexpectedReplies.incrementAndGet();
        }
    }

    /** A consistent read is CONTENT_LENGTH copies of the same letter. Anything else is a torn read. */
    private static void checkRead(String reply) {
        if (reply.startsWith("ERROR")) {
            unexpectedErrors.incrementAndGet();
            return;
        }
        boolean uniform = reply.length() == CONTENT_LENGTH
                && reply.chars().allMatch(c -> c == reply.charAt(0));
        if (uniform) {
            validReplies.incrementAndGet();
        } else {
            tornReads.incrementAndGet();
        }
    }
}
