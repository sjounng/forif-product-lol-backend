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
public class DraftAssignmentTimeoutScheduler {

	private final DraftRepository draftRepository;
	private final DraftService draftService;
	private final Clock clock;

	@Scheduled(fixedDelay = 1_000)
	public void completeExpiredAssignments() {
		draftRepository.findAllByStatusAndAssignmentDeadlineAtLessThanEqual(
				DraftStatus.ASSIGNING,
				LocalDateTime.now(clock))
				.forEach(draft -> {
					try {
						draftService.autoCompleteExpiredAssignments(draft.getId());
					} catch (RuntimeException exception) {
						log.warn("Draft assignment timeout failed. draftId={}", draft.getId(), exception);
					}
				});
	}
}
