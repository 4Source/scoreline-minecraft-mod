package io.github.foursource.scoreline.net;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.NotImplementedException;

public class RequestGate {
    public RequestGate(Set<String> knownStatIds, int capacity, long refillIntervalMillis) {
        throw new NotImplementedException();
    }

    public void onJoin(UUID player) {
        throw new NotImplementedException();
    }

    public void onLeave(UUID player) {
        throw new NotImplementedException();
    }

    public Optional<List<String>> admit(UUID player, List<String> requested, long nowMillis) {
        throw new NotImplementedException();
    }
}
