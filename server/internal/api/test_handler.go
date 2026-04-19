package api

import "gorm.io/gorm"

// TestHandler provides endpoints for E2E test state management.
// Only registered when SPELA_TEST_MODE=true. The Reset endpoint is
// implemented on huma (see HumaReset in huma_test_reset.go).
type TestHandler struct {
	DB *gorm.DB
}
