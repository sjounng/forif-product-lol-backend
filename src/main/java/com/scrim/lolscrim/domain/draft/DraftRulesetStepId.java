package com.scrim.lolscrim.domain.draft;

import java.io.Serializable;

public record DraftRulesetStepId(String rulesetId, Byte stepNo) implements Serializable {
}
