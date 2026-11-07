package com.billassistant.session;

import com.billassistant.auth.AuthClient;

public interface SessionStore {

    record RefreshSession(long uid, long expiresAt, AuthClient client) {
    }

    enum Rotation { SUCCESS, CONFLICT, REUSED, INVALID }

    default void save(String sid, String tokenHash, long uid, long expiresAt) {
        save(sid, tokenHash, uid, expiresAt, AuthClient.WEB);
    }

    void save(String sid, String tokenHash, long uid, long expiresAt, AuthClient client);

    RefreshSession find(String sid);

    Rotation rotate(String sid, String oldHash, String newHash, long now, long graceSeconds);

    void revoke(String sid, String tokenHash);

    void delete(String sid);
}
