package api

// CoreCompatibilityEntry is the API response for a single console's core compatibility.
type CoreCompatibilityEntry struct {
	ConsoleID   string `json:"consoleId"`
	ConsoleName string `json:"consoleName"`
	NativeCore  string `json:"nativeCore"`
	WebCore     string `json:"webCore"`
	Matched     bool   `json:"matched"`
}

// coreEquivalences maps libretro core names that are the same engine
// under different names. The beetle_* cores are repackaged Mednafen cores
// and produce compatible save states.
var coreEquivalences = map[string]string{
	"beetle_psx_hw": "mednafen_psx_hw",
	"beetle_pce":    "mednafen_pce",
	"beetle_ngp":    "mednafen_ngp",
	"beetle_wswan":  "mednafen_wswan",
	"beetle_pcfx":   "mednafen_pcfx",
	"beetle_vb":     "mednafen_vb",
	"beetle_saturn": "mednafen_saturn",
}

// coresCompatible checks whether two core names refer to the same engine
// (accounting for beetle/mednafen equivalences).
func coresCompatible(a, b string) bool {
	if a == b {
		return true
	}
	normalize := func(name string) string {
		if equiv, ok := coreEquivalences[name]; ok {
			return equiv
		}
		return name
	}
	return normalize(a) == normalize(b)
}
