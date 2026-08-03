package com.scrim.lolscrim.domain.draft;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.scrim.lolscrim.domain.match.DraftRepository;
import com.scrim.lolscrim.domain.match.DraftStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftTurnTimeoutScheduler {

	private final DraftRepository draftRepository;
	private final DraftService draftService;
	private final Clock clock;

	@Scheduled(fixedDelay = 1_000)
	public void completeExpiredTurns() {
		draftRepository.findAllByStatusAndTurnDeadlineAtLessThanEqual(
				DraftStatus.IN_PROGRESS,
				LocalDateTime.now(clock))
				.forEach(draft -> {
					try {
						draftService.autoCompleteExpiredTurn(draft.getId());
					} catch (RuntimeException exception) {
						log.warn("Draft turn timeout failed. draftId={}", draft.getId(), exception);
					}
				});
	}
}
