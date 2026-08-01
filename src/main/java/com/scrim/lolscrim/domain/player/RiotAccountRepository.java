package com.scrim.lolscrim.domain.player;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotAccountRepository extends JpaRepository<RiotAccount, Long> {

	Optional<RiotAccount> findByPuuid(String puuid);
}
