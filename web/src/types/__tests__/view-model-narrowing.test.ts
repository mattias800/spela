import { describe, it, expect } from "vitest";
import {
  asUserRole,
  asChallengeType,
  asChallengeDifficulty,
  asChallengeStatus,
  asAttemptStatus,
  asNetplaySessionStatus,
  asOptionalNetplayEndReason,
  asNetplayInviteStatus,
  asSharedSessionStatus,
  asSharedSessionMemberRole,
} from "../view-model-narrowing";

describe("view-model narrowing helpers", () => {
  describe("asUserRole", () => {
    it("accepts all UserRole values", () => {
      expect(asUserRole("owner")).toBe("owner");
      expect(asUserRole("admin")).toBe("admin");
      expect(asUserRole("user")).toBe("user");
    });

    it("throws on unknown role", () => {
      expect(() => asUserRole("superadmin")).toThrow(/Unexpected user role/);
      expect(() => asUserRole("")).toThrow(/Unexpected user role/);
    });
  });

  describe("asChallengeType", () => {
    it("accepts all challenge types", () => {
      expect(asChallengeType("completion")).toBe("completion");
      expect(asChallengeType("speedrun")).toBe("speedrun");
      expect(asChallengeType("survival")).toBe("survival");
    });

    it("throws on unknown type", () => {
      expect(() => asChallengeType("marathon")).toThrow(
        /Unexpected challenge type/,
      );
    });
  });

  describe("asChallengeDifficulty", () => {
    it("accepts easy, medium, hard", () => {
      expect(asChallengeDifficulty("easy")).toBe("easy");
      expect(asChallengeDifficulty("medium")).toBe("medium");
      expect(asChallengeDifficulty("hard")).toBe("hard");
    });

    it("throws on unknown difficulty", () => {
      expect(() => asChallengeDifficulty("insane")).toThrow(
        /Unexpected challenge difficulty/,
      );
    });
  });

  describe("asChallengeStatus", () => {
    it("accepts active, closed, expired", () => {
      expect(asChallengeStatus("active")).toBe("active");
      expect(asChallengeStatus("closed")).toBe("closed");
      expect(asChallengeStatus("expired")).toBe("expired");
    });

    it("throws on unknown status", () => {
      expect(() => asChallengeStatus("pending")).toThrow(
        /Unexpected challenge status/,
      );
    });
  });

  describe("asAttemptStatus", () => {
    it("accepts in_progress, completed, abandoned", () => {
      expect(asAttemptStatus("in_progress")).toBe("in_progress");
      expect(asAttemptStatus("completed")).toBe("completed");
      expect(asAttemptStatus("abandoned")).toBe("abandoned");
    });

    it("throws on unknown status", () => {
      expect(() => asAttemptStatus("queued")).toThrow(
        /Unexpected attempt status/,
      );
    });
  });

  describe("asNetplaySessionStatus", () => {
    it("accepts waiting, in_progress, ended", () => {
      expect(asNetplaySessionStatus("waiting")).toBe("waiting");
      expect(asNetplaySessionStatus("in_progress")).toBe("in_progress");
      expect(asNetplaySessionStatus("ended")).toBe("ended");
    });

    it("throws on unknown status", () => {
      expect(() => asNetplaySessionStatus("stalled")).toThrow(
        /Unexpected netplay session status/,
      );
    });
  });

  describe("asOptionalNetplayEndReason", () => {
    it("returns undefined for empty string", () => {
      expect(asOptionalNetplayEndReason("")).toBeUndefined();
    });

    it("returns undefined for null/undefined", () => {
      expect(asOptionalNetplayEndReason(null)).toBeUndefined();
      expect(asOptionalNetplayEndReason(undefined)).toBeUndefined();
    });

    it("accepts all end reason values", () => {
      expect(asOptionalNetplayEndReason("host_left")).toBe("host_left");
      expect(asOptionalNetplayEndReason("client_left")).toBe("client_left");
      expect(asOptionalNetplayEndReason("timeout")).toBe("timeout");
      expect(asOptionalNetplayEndReason("completed")).toBe("completed");
      expect(asOptionalNetplayEndReason("admin_deleted")).toBe("admin_deleted");
    });

    it("throws on unknown non-empty reason", () => {
      expect(() => asOptionalNetplayEndReason("crashed")).toThrow(
        /Unexpected netplay end reason/,
      );
    });
  });

  describe("asNetplayInviteStatus", () => {
    it("accepts pending, accepted, declined, expired", () => {
      expect(asNetplayInviteStatus("pending")).toBe("pending");
      expect(asNetplayInviteStatus("accepted")).toBe("accepted");
      expect(asNetplayInviteStatus("declined")).toBe("declined");
      expect(asNetplayInviteStatus("expired")).toBe("expired");
    });

    it("throws on unknown status", () => {
      expect(() => asNetplayInviteStatus("revoked")).toThrow(
        /Unexpected netplay invite status/,
      );
    });
  });

  describe("asSharedSessionStatus", () => {
    it("accepts active, paused, completed", () => {
      expect(asSharedSessionStatus("active")).toBe("active");
      expect(asSharedSessionStatus("paused")).toBe("paused");
      expect(asSharedSessionStatus("completed")).toBe("completed");
    });

    it("throws on unknown status", () => {
      expect(() => asSharedSessionStatus("archived")).toThrow(
        /Unexpected shared session status/,
      );
    });
  });

  describe("asSharedSessionMemberRole", () => {
    it("accepts owner and member", () => {
      expect(asSharedSessionMemberRole("owner")).toBe("owner");
      expect(asSharedSessionMemberRole("member")).toBe("member");
    });

    it("throws on unknown role", () => {
      expect(() => asSharedSessionMemberRole("admin")).toThrow(
        /Unexpected shared session member role/,
      );
    });
  });
});
