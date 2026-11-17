package io.github.foursource.scoreline.net;

import org.apache.commons.lang3.NotImplementedException;

public class RequestTimer {
    public void onHello(int protocolVersion, int refreshSeconds) {
        throw new NotImplementedException();
    }

    public void onDisconnect() {
        throw new NotImplementedException();
    }

    public boolean shouldSend(boolean tabHeld, long nowMillis) {
        throw new NotImplementedException();
    }
}
