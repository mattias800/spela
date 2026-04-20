// View-model narrowing helpers.
//
// The huma-generated wire types (see src/generated/api.d.ts) type enum-like
// fields as plain `string`, because huma flattens Go iota / string-enum
// types to their JSON representation without emitting JSON Schema enums.
// The frontend narrows these fields to literal unions so switch statements
// and Record<Status, _> mappings stay exhaustiveness-checked.
//
// We used to bridge the wire type and view-model type with `as` casts —
// but that hides latent bugs: if the server ever emits a value outside the
// claimed union, the cast lies and downstream code breaks silently. These
// helpers replace those casts with runtime-validated narrowings so unknown
// server values become loud errors.
//
// Each helper takes a `string` and returns the narrowed literal type, or
// throws. The error is intentionally noisy — if it fires, either the server
// introduced a new value the frontend hasn't handled, or the transport
// layer lied.

import type {
  AttemptStatus,
  ChallengeDifficulty,
  ChallengeStatus,
  ChallengeType,
  NetplayEndReason,
  NetplayInviteStatus,
  NetplaySessionStatus,
  SharedSessionMemberRole,
  SharedSessionStatus,
  UserRole,
} from "@/types/api";

export function asUserRole(s: string): UserRole {
  if (s === "owner" || s === "admin" || s === "user") return s;
  throw new Error(`Unexpected user role from server: ${s}`);
}

export function asChallengeType(s: string): ChallengeType {
  if (s === "completion" || s === "speedrun" || s === "survival") return s;
  throw new Error(`Unexpected challenge type from server: ${s}`);
}

export function asChallengeDifficulty(s: string): ChallengeDifficulty {
  if (s === "easy" || s === "medium" || s === "hard") return s;
  throw new Error(`Unexpected challenge difficulty from server: ${s}`);
}

export function asChallengeStatus(s: string): ChallengeStatus {
  if (s === "active" || s === "closed" || s === "expired") return s;
  throw new Error(`Unexpected challenge status from server: ${s}`);
}

export function asAttemptStatus(s: string): AttemptStatus {
  if (s === "in_progress" || s === "completed" || s === "abandoned") return s;
  throw new Error(`Unexpected attempt status from server: ${s}`);
}

export function asNetplaySessionStatus(s: string): NetplaySessionStatus {
  if (s === "waiting" || s === "in_progress" || s === "ended") return s;
  throw new Error(`Unexpected netplay session status from server: ${s}`);
}

// endReason is optional on the view-model: the server emits an empty string
// on the wire until a session has actually ended. We map "" to undefined so
// consumers can use truthiness to check whether an end reason exists, and
// narrow any non-empty value.
export function asOptionalNetplayEndReason(
  s: string | null | undefined,
): NetplayEndReason | undefined {
  if (!s) return undefined;
  if (
    s === "host_left" ||
    s === "client_left" ||
    s === "timeout" ||
    s === "completed" ||
    s === "admin_deleted"
  ) {
    return s;
  }
  throw new Error(`Unexpected netplay end reason from server: ${s}`);
}

export function asNetplayInviteStatus(s: string): NetplayInviteStatus {
  if (
    s === "pending" ||
    s === "accepted" ||
    s === "declined" ||
    s === "expired"
  ) {
    return s;
  }
  throw new Error(`Unexpected netplay invite status from server: ${s}`);
}

export function asSharedSessionStatus(s: string): SharedSessionStatus {
  if (s === "active" || s === "paused" || s === "completed") return s;
  throw new Error(`Unexpected shared session status from server: ${s}`);
}

export function asSharedSessionMemberRole(s: string): SharedSessionMemberRole {
  if (s === "owner" || s === "member") return s;
  throw new Error(`Unexpected shared session member role from server: ${s}`);
}
