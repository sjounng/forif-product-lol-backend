package com.scrim.lolscrim.domain.riot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotAccountRepository extends JpaRepository<RiotAccount, Long> {

	Optional<RiotAccount> findByPuuid(String puuid);

	Optional<RiotAccount> findByPlatformAndGameNameAndTagLine(RiotPlatform platform, String gameName, String tagLine);
}
