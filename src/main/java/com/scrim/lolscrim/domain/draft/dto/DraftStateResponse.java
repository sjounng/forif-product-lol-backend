package com.scrim.lolscrim.domain.draft.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.scrim.lolscrim.domain.draft.DraftActionType;
import com.scrim.lolscrim.domain.match.DraftStatus;
import com.scrim.lolscrim.domain.session.FearlessMode;
import com.scrim.lolscrim.domain.player.Lane;
import com.scrim.lolscrim.domain.session.TeamSide;

public record DraftStateResponse(
		Long draftId,
		Integer version,
		Integer lastEventSeq,
		LocalDateTime serverTime,
		DraftStatus status,
		SessionSummary session,
		Map<TeamSide, DraftTeamResponse> teams,
		List<DraftStepResponse> steps,
		int currentStep,
		DraftHoverResponse hover,
		LocalDateTime turnDeadlineAt,
		LocalDateTime assignmentDeadlineAt,
		int blueReserveMs,
		int redReserveMs,
		List<LockedChampionResponse> lockedChampions,
		List<Integer> bannedByFearless,
		List<DraftAssignmentResponse> assignments,
		Map<TeamSide, Boolean> assignmentConfirmed,
		ViewerResponse viewer) {

	public record SessionSummary(
			Long id,
			String name,
			int gameNo,
			FearlessMode fearlessMode) {
	}

	public record DraftTeamResponse(
			TeamSide side,
			String teamName,
			Long captainUserId,
			String captainDisplayName,
			boolean ready,
			List<DraftPlayerResponse> players) {
	}

	public record DraftPlayerResponse(
			Long playerId,
			String displayName,
			Lane lane) {
	}

	public record DraftChampionResponse(
			Integer id,
			String riotId,
			String nameKo,
			String imageUrl) {
	}

	public record DraftStepResponse(
			int stepNo,
			TeamSide side,
			DraftActionType actionType,
			int phase,
			DraftChampionResponse champion,
			Long playerId,
			boolean auto,
			LocalDateTime lockedAt) {
	}

	public record LockedChampionResponse(
			DraftChampionResponse champion,
			String source,
			Long sourceMatchId,
			TeamSide side) {
	}

	public record DraftHoverResponse(
			TeamSide side,
			int stepNo,
			DraftChampionResponse champion,
			LocalDateTime updatedAt) {
	}

	public record DraftAssignmentResponse(
			TeamSide side,
			Long playerId,
			String playerDisplayName,
			Lane lane,
			DraftChampionResponse champion,
			boolean auto) {
	}

	public record ViewerResponse(
			String role,
			TeamSide side,
			boolean canReady,
			boolean canLock,
			boolean canAssign,
			boolean canConfirmAssignment) {
	}
}
