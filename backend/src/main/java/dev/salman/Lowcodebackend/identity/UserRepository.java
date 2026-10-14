package dev.salman.Lowcodebackend.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	/** Concurrent first sign-ins for one email must yield a single user. */
	@Modifying
	@Query(value = "insert into users (id, email) values (:id, :email) on conflict do nothing", nativeQuery = true)
	int insertIfAbsent(UUID id, String email);

}
