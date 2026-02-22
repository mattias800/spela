package igdb

// TwitchTokenURLForTest returns the current Twitch token URL (for test setup).
func TwitchTokenURLForTest() string {
	return twitchTokenURL
}

// SetTwitchTokenURLForTest overrides the Twitch token URL (for test setup).
func SetTwitchTokenURLForTest(url string) {
	twitchTokenURL = url
}

// IGDBAPIBaseForTest returns the current IGDB API base URL (for test setup).
func IGDBAPIBaseForTest() string {
	return igdbAPIBase
}

// SetIGDBAPIBaseForTest overrides the IGDB API base URL (for test setup).
func SetIGDBAPIBaseForTest(url string) {
	igdbAPIBase = url
}

// ImageBaseForTest returns the current IGDB image base URL (for test setup).
func ImageBaseForTest() string {
	return igdbImageBase
}

// SetImageBaseForTest overrides the IGDB image base URL (for test setup).
func SetImageBaseForTest(url string) {
	igdbImageBase = url
}
