package lk.swiftdrop.merchant.merchant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MerchantRepository extends JpaRepository<Merchant, UUID>, JpaSpecificationExecutor<Merchant> {

    Optional<Merchant> findByOwnerUserId(String ownerUserId);

    boolean existsByOwnerUserId(String ownerUserId);
}
