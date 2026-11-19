package com.example.payment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * 9장: 시험 JVM 과 Kafka 브로커 사이에 끼운 중계(TCP). 결제 서버·주문 서비스는 모두 이 중계를 거쳐 브로커와 이야기한다 —
 * 브로커가 알려 주는 주소(advertised listener)도 이 중계의 포트다.
 * 시험은 이 중계로 브로커를 끊거나(cut: 열린 연결을 모두 닫고 새 연결도 받자마자 닫음 — 요청이 브로커에 닿지 않는다),
 * 늦게 답하게 한다(slowDown: 브로커가 보낸 답을 정한 시간만큼 붙잡았다가 넘김). restore 로 되돌린다.
 * 컨테이너를 멈추는(docker pause) 방법은 쓰지 않았다 — 멈춘 브로커도 운영체제가 요청 바이트를 받아 두었다가, 다시 살아나면 처리한다.
 */
public class BrokerLink {

    private static final int BUFFER = 64 * 1024;

    private final ServerSocket serverSocket;
    private final IntSupplier brokerPort;
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    private volatile boolean cut;
    private volatile Duration answerDelay = Duration.ZERO;

    private BrokerLink(ServerSocket serverSocket, IntSupplier brokerPort) {
        this.serverSocket = serverSocket;
        this.brokerPort = brokerPort;
    }

    // 이 포트로 받은 연결을 brokerPort 로 잇는다. 브로커가 아직 뜨지 않았어도 포트를 먼저 정할 수 있게 브로커 포트는 연결할 때 읽는다
    public static BrokerLink open(int port, IntSupplier brokerPort) {
        try {
            ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
            BrokerLink link = new BrokerLink(serverSocket, brokerPort);
            Thread accepting = new Thread(link::accept, "broker-link-accept");
            accepting.setDaemon(true);
            accepting.start();
            return link;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    public static int freePort() {
        try (ServerSocket probe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    // 브로커를 끊는다. 열린 연결은 모두 닫고, 되돌릴 때까지 새 연결도 받자마자 닫는다
    public void cut() {
        cut = true;
        open.forEach(BrokerLink::closeQuietly);
        open.clear();
    }

    // 브로커가 보낸 답을 delay 만큼 붙잡았다가 넘긴다(브로커가 느린 것처럼)
    public void slowDown(Duration delay) {
        answerDelay = delay;
    }

    public void restore() {
        cut = false;
        answerDelay = Duration.ZERO;
    }

    private void accept() {
        while (serverSocket.isBound()) {
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
        Socket broker = new Socket(InetAddress.getLoopbackAddress(), brokerPort.getAsInt());
        client.setTcpNoDelay(true);
        broker.setTcpNoDelay(true);
        open.add(client);
        open.add(broker);
        pump(client, broker, false);
        pump(broker, client, true);
    }

    private void pump(Socket from, Socket to, boolean answers) {
        Thread pumping = new Thread(() -> copy(from, to, answers), "broker-link-pump");
        pumping.setDaemon(true);
        pumping.start();
    }

    private void copy(Socket from, Socket to, boolean answers) {
        byte[] buffer = new byte[BUFFER];
        try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
            int read = in.read(buffer);
            while (read >= 0) {
                delayIfSlow(answers);
                out.write(buffer, 0, read);
                out.flush();
                read = in.read(buffer);
            }
        } catch (IOException closed) {
            // 끊었거나 한쪽이 닫았다
        } finally {
            closeQuietly(from);
            closeQuietly(to);
            open.remove(from);
            open.remove(to);
        }
    }

    private void delayIfSlow(boolean answers) {
        Duration delay = answerDelay;
        if (answers && delay.toMillis() > 0) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 닫혔다
        }
    }
}
