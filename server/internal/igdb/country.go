package igdb

// CountryCodeToName maps ISO 3166-1 numeric country codes used by IGDB
// to human-readable country names. Only the ~20 most common game
// development countries are included; unknown codes return "".
var CountryCodeToName = map[int]string{
	36:  "Australia",
	40:  "Austria",
	56:  "Belgium",
	76:  "Brazil",
	124: "Canada",
	156: "China",
	203: "Czech Republic",
	208: "Denmark",
	246: "Finland",
	250: "France",
	276: "Germany",
	344: "Hong Kong",
	356: "India",
	380: "Italy",
	392: "Japan",
	410: "South Korea",
	484: "Mexico",
	528: "Netherlands",
	554: "New Zealand",
	578: "Norway",
	616: "Poland",
	620: "Portugal",
	643: "Russia",
	702: "Singapore",
	710: "South Africa",
	724: "Spain",
	752: "Sweden",
	756: "Switzerland",
	158: "Taiwan",
	764: "Thailand",
	792: "Turkey",
	804: "Ukraine",
	826: "United Kingdom",
	840: "United States",
}

// CountryName returns the country name for an IGDB country code,
// or an empty string if the code is unknown or zero.
func CountryName(code int) string {
	if code == 0 {
		return ""
	}
	return CountryCodeToName[code]
}
