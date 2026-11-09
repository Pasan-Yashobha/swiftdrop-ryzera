package lk.swiftdrop.identity.repository;

import java.util.UUID;
import lk.swiftdrop.identity.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
}
