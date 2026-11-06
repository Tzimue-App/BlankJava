package com.blankjava.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accounts, looked up by their normalised e-mail. */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    boolean existsByEmail(String email);
}
