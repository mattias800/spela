package api

import (
	"fmt"
	"net/mail"
	"strings"
)

const generatedRegistrationEmailDomain = "users.spela.invalid"

func normalizeRegistrationEmail(username string, email string) (string, error) {
	trimmed := strings.TrimSpace(email)
	if trimmed == "" {
		return generatedRegistrationEmail(username), nil
	}
	if !isValidPlainEmail(trimmed) {
		return "", fmt.Errorf("invalid email")
	}
	if isGeneratedRegistrationEmailDomain(trimmed) {
		return "", fmt.Errorf("reserved email domain")
	}
	return trimmed, nil
}

// isGeneratedRegistrationEmailDomain reports whether email is in the reserved
// internal placeholder domain (case-insensitive). Only generatedRegistrationEmail
// mints addresses here; user- and admin-supplied input must be rejected so nobody
// can squat a placeholder (blocking a real user's no-email signup) or store a
// reserved-namespace address that publicUserEmail would silently hide. (#1516)
func isGeneratedRegistrationEmailDomain(email string) bool {
	return strings.HasSuffix(strings.ToLower(strings.TrimSpace(email)), "@"+generatedRegistrationEmailDomain)
}

func generatedRegistrationEmail(username string) string {
	return fmt.Sprintf("%s@%s", username, generatedRegistrationEmailDomain)
}

func publicUserEmail(email string) string {
	if isGeneratedRegistrationEmailDomain(email) {
		return ""
	}
	return email
}

func isValidPlainEmail(email string) bool {
	addr, err := mail.ParseAddress(email)
	return err == nil && addr.Address == email
}
