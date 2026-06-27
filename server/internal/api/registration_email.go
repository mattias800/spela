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
	return trimmed, nil
}

func generatedRegistrationEmail(username string) string {
	return fmt.Sprintf("%s@%s", username, generatedRegistrationEmailDomain)
}

func publicUserEmail(email string) string {
	if strings.HasSuffix(email, "@"+generatedRegistrationEmailDomain) {
		return ""
	}
	return email
}

func isValidPlainEmail(email string) bool {
	addr, err := mail.ParseAddress(email)
	return err == nil && addr.Address == email
}
