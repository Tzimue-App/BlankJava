package com.blankjava.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Refresh tokens, looked up by the hash of the cookie value. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * The token, row-locked until the end of the transaction ({@code SELECT … FOR UPDATE}): two
     * requests presenting the same cookie are serialised, so only one of them can rotate it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Deletes the tokens expired, or revoked, before {@code cutoff}; returns how many. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff or t.revokedAt < :cutoff")
    int deleteObsolete(@Param("cutoff") Instant cutoff);
}
