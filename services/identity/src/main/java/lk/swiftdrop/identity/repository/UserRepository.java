package lk.swiftdrop.identity.repository;

import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleIdAndDeletedAtIsNull(String googleId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    Page<User> findAllByRoleAndDeletedAtIsNull(Role role, Pageable pageable);

    long countByRoleAndDeletedAtIsNull(Role role);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:query IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchUsers(@Param("role") Role role, @Param("query") String query, Pageable pageable);
}
