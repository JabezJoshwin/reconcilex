package com.reconcilex.auth;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record UserIdentity(UUID id, String displayName, String email) implements Principal, Serializable {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override public String getName() { return id.toString(); }
}
