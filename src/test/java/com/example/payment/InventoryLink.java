package com.example.payment;

import static org.awaitility.Awaitility.await;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 10장: 결제 서버와 재고 서비스 사이에 끼운 중계(TCP, 9장 BrokerLink 와 같은 생각). 결제 서버는 이 중계의 주소로 재고 서비스를 부른다.
 * - 끊기(cut): 열린 연결을 모두 닫고, 되돌릴 때까지 새 연결도 받자마자 닫는다(재고 서비스가 멈춘 것처럼 요청이 닿지 않는다).
 * - 늦추기(slowAnswers): 재고 서비스가 보낸 답을 정한 시간만큼 붙잡았다가 넘긴다 — 재고 서비스는 이미 처리를 끝냈다.
 * - 요청 붙잡기(holdRequests): 결제 서버가 보낸 요청을 재고 서비스에 넘기지 않고 쥐고 있다가, releaseRequests 때 넘긴다.
 *   그사이 결제 서버가 기다리다 그만두고 연결을 닫아도 쥔 요청은 나중에 재고 서비스에 닿는다(늦게 도착한 요청).
 */
public class InventoryLink {

    private static final int BUFFER = 64 * 1024;

    private final ServerSocket serverSocket;
    private final int targetPort;
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    private final List<Hop> held = new ArrayList<>();
    // 붙잡았다 넘긴 요청 가운데 재고 서비스의 답이 아직 오지 않은 것
    private final AtomicInteger delivering = new AtomicInteger();
    private volatile boolean cut;
    private volatile boolean holding;
    private volatile Duration answerDelay = Duration.ZERO;

    private InventoryLink(ServerSocket serverSocket, int targetPort) {
        this.serverSocket = serverSocket;
        this.targetPort = targetPort;
    }

    public static InventoryLink open(int targetPort) {
        try {
            ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            InventoryLink link = new InventoryLink(serverSocket, targetPort);
            Thread accepting = new Thread(link::accept, "inventory-link-accept");
            accepting.setDaemon(true);
            accepting.start();
            return link;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    public String url() {
        return "http://localhost:" + serverSocket.getLocalPort();
    }

    public void cut() {
        cut = true;
        open.forEach(InventoryLink::closeQuietly);
        open.clear();
    }

    public void slowAnswers(Duration delay) {
        answerDelay = delay;
    }

    public synchronized void holdRequests() {
        holding = true;
    }

    // 쥐고 있던 요청을 받은 순서대로 재고 서비스에 넘긴다
    public synchronized void releaseRequests() {
        holding = false;
        held.forEach(Hop::deliver);
        held.clear();
    }

    // 넘긴 요청에 재고 서비스가 답할 때까지(그 요청을 처리할 때까지) 기다린다
    public void awaitDelivered() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(() -> delivering.get() == 0);
    }

    public void restore() {
        releaseRequests();
        cut = false;
        answerDelay = Duration.ZERO;
    }

    public void close() {
        cut();
        closeQuietly(serverSocket);
    }

    private void accept() {
        while (!serverSocket.isClosed()) {
            try {
                link(serverSocket.accept());
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void link(Socket client) throws IOException {
        if (cut) {
            closeQuietly(client);
            return;
        }
        Socket target = new Socket(InetAddress.getLoopbackAddress(), targetPort);
        client.setTcpNoDelay(true);
        target.setTcpNoDelay(true);
        open.add(client);
        open.add(target);
        Hop hop = new Hop(client, target);
        start(hop::copyRequests);
        start(hop::copyAnswers);
    }

    private static void start(Runnable pumping) {
        Thread thread = new Thread(pumping, "inventory-link-pump");
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized boolean keepIfHolding(Hop hop, byte[] chunk) {
        if (holding) {
            if (hop.isEmpty()) {
                held.add(hop);
            }
            hop.keep(chunk);
            return true;
        }
        return false;
    }

    private void sleepBeforeAnswer() {
        Duration delay = answerDelay;
        if (delay.toMillis() > 0) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void close(Socket socket) {
        closeQuietly(socket);
        open.remove(socket);
    }

    private static void closeQuietly(Closeable socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 닫혔다
        }
    }

    /** 결제 서버 연결 하나와 그에 짝지은 재고 서비스 연결 하나 */
    private class Hop {

        private final Socket client;
        private final Socket target;
        private final List<byte[]> kept = new ArrayList<>();
        private volatile boolean late;

        Hop(Socket client, Socket target) {
            this.client = client;
            this.target = target;
        }

        boolean isEmpty() {
            return kept.isEmpty();
        }

        void keep(byte[] chunk) {
            kept.add(chunk);
        }

        // 쥐고 있던 요청을 재고 서비스에 넘긴다. 답이 오면 넘긴 요청이 처리된 것으로 본다
        void deliver() {
            late = true;
            delivering.incrementAndGet();
            try {
                OutputStream out = target.getOutputStream();
                for (byte[] chunk : kept) {
                    out.write(chunk);
                }
                out.flush();
            } catch (IOException failed) {
                delivering.decrementAndGet();
                close(target);
            }
            kept.clear();
        }

        void copyRequests() {
            byte[] buffer = new byte[BUFFER];
            try {
                InputStream in = client.getInputStream();
                OutputStream out = target.getOutputStream();
                int read = in.read(buffer);
                while (read >= 0) {
                    byte[] chunk = Arrays.copyOf(buffer, read);
                    if (!keepIfHolding(this, chunk)) {
                        out.write(chunk);
                        out.flush();
                    }
                    read = in.read(buffer);
                }
            } catch (IOException closed) {
                // 끊었거나 결제 서버가 연결을 닫았다
            }
            close(client);
            closeTargetUnlessHeld();
        }

        // 결제 서버가 연결을 닫아도 쥐고 있던 요청이 있으면 재고 서비스 쪽 연결은 넘길 때까지 둔다
        private void closeTargetUnlessHeld() {
            synchronized (InventoryLink.this) {
                if (held.contains(this) || late) {
                    return;
                }
            }
            close(target);
        }

        void copyAnswers() {
            byte[] buffer = new byte[BUFFER];
            try {
                InputStream in = target.getInputStream();
                OutputStream out = client.getOutputStream();
                int read = in.read(buffer);
                while (read >= 0) {
                    answered();
                    sleepBeforeAnswer();
                    out.write(buffer, 0, read);
                    out.flush();
                    read = in.read(buffer);
                }
            } catch (IOException closed) {
                // 끊었거나 결제 서버가 이미 연결을 닫았다
            } finally {
                answered();
                close(client);
                close(target);
            }
        }

        private void answered() {
            if (late) {
                late = false;
                delivering.decrementAndGet();
            }
        }
    }
}
