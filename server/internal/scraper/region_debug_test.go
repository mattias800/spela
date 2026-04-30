package scraper
import "testing"
func TestExtractRegionDebug(t *testing.T) {
    r := ExtractRegion("Super Smash Bros. Melee (USA) (En,Ja).rvz")
    t.Logf("Region: %q", r)
    np := hasNonPreferredRegion("Super Smash Bros. Melee (USA) (En,Ja).rvz")
    t.Logf("hasNonPreferredRegion: %v", np)
}
