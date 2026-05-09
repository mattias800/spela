package api

import (
	"strings"
)

// commonPasswords is a static, offline blocklist of the most common /
// most-leaked passwords. Issue #1131(A): registration and password change
// previously enforced only minLength:8 maxLength:72; combined with the
// username enumeration in #1132, an attacker who identified a valid
// username could password-spray "Password1" / "qwerty12" / "summer2025"
// against many accounts without ever tripping the per-account lockout.
//
// The list is intentionally small and offline — no network call to a
// breach API. It catches the password-spray hit-list without imposing
// HIBP latency or making a DNS lookup that could leak password hashes.
// Operators wanting stricter enforcement can layer a HIBP k-anonymity
// integration on top.
var commonPasswords = func() map[string]struct{} {
	list := []string{
		"password", "password1", "password12", "password123",
		"qwerty", "qwerty1", "qwerty12", "qwerty123",
		"123456", "1234567", "12345678", "123456789", "1234567890",
		"letmein", "welcome", "welcome1", "admin", "admin123", "administrator",
		"changeme", "change-me", "default", "iloveyou", "monkey",
		"dragon", "master", "abc12345", "abcdef", "abc123",
		"football", "baseball", "trustno1", "sunshine", "princess",
		"superman", "batman", "michael", "jennifer", "michelle",
		"summer2024", "summer2025", "spring2024", "winter2024", "autumn2024",
		"passw0rd", "p@ssword", "p@ssw0rd",
		"00000000", "11111111", "22222222", "88888888", "99999999",
		"qwertyuiop", "asdfghjkl", "zxcvbnm", "qazwsx", "qwer1234",
	}
	out := make(map[string]struct{}, len(list))
	for _, p := range list {
		out[strings.ToLower(p)] = struct{}{}
	}
	return out
}()

// isCommonPassword reports whether a password is on the static
// common-password blocklist (case-insensitive). Length and other
// schema-level rules are enforced elsewhere — this catches passwords
// that pass the length check but are still trivial to spray.
func isCommonPassword(password string) bool {
	_, ok := commonPasswords[strings.ToLower(password)]
	return ok
}
