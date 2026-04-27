package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserAddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, Long> {
    List<UserAddressEntity> findByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByDefaultAddressDescUpdatedAtDescIdDesc(
            Long userId,
            String addressScope
    );

    Optional<UserAddressEntity> findByIdAndUserIdAndAddressScopeAndHiddenFalse(Long id, Long userId, String addressScope);

    long countByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalse(Long userId, String addressScope);

    Optional<UserAddressEntity> findTopByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByUpdatedAtDescIdDesc(
            Long userId,
            String addressScope
    );

    @Modifying
    @Query("""
            update UserAddressEntity address
            set address.defaultAddress = false
            where address.userId = :userId
              and address.addressScope = :addressScope
              and address.hidden = false
            """)
    int clearDefaultByUserIdAndAddressScope(@Param("userId") Long userId, @Param("addressScope") String addressScope);
}
