package io.github.foursource.scoreline.net;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.annotations.State;

/** The client's side of the protocol logic: the check every tick, and one connection. */
@State(Scope.Thread)
public class RequestTimerBenchmark {
    /** Milliseconds per client tick (20 ticks per second). */
    private static final long TICK_MILLIS = 50;

    private RequestTimer tickTimer;
    private long now;
    private RequestTimer connectionTimer;

    @Setup
    public void setUp() {
        tickTimer = new RequestTimer();
        tickTimer.onHello(Protocol.VERSION, Protocol.REFRESH_SECONDS);
        connectionTimer = new RequestTimer();
    }

    /** TAB held, one tick later per call: most calls return false, one in 40 returns true, as in the game. */
    @Benchmark
    public boolean shouldSend() {
        now += TICK_MILLIS;
        return tickTimer.shouldSend(true, now);
    }

    /** One connection to a modded server: the hello arrives, then the player leaves. */
    @Benchmark
    public RequestTimer helloDisconnect(Blackhole blackhole) {
        connectionTimer.onHello(Protocol.VERSION, Protocol.REFRESH_SECONDS);
        // Hands the timer out between the calls, so the JIT cannot drop onHello's writes as overwritten by onDisconnect
        blackhole.consume(connectionTimer);
        connectionTimer.onDisconnect();
        return connectionTimer;
    }
}
