package federation

import (
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// PeerStore is the friend registry: CRUD over db.FederationPeer.
type PeerStore struct {
	DB *gorm.DB
}

// Upsert creates or updates a peer keyed by Fingerprint.
func (s PeerStore) Upsert(peer *db.FederationPeer) error {
	return s.DB.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "fingerprint"}},
		DoUpdates: clause.AssignmentColumns([]string{"public_key", "name", "base_url", "status", "share_policy", "consume_policy", "updated_at"}),
	}).Create(peer).Error
}

// GetByFingerprint returns the peer with the given fingerprint, or
// gorm.ErrRecordNotFound.
func (s PeerStore) GetByFingerprint(fp string) (*db.FederationPeer, error) {
	var peer db.FederationPeer
	if err := s.DB.Where("fingerprint = ?", fp).First(&peer).Error; err != nil {
		return nil, err
	}
	return &peer, nil
}

// List returns all peers ordered by name.
func (s PeerStore) List() ([]db.FederationPeer, error) {
	var peers []db.FederationPeer
	if err := s.DB.Order("name ASC").Find(&peers).Error; err != nil {
		return nil, err
	}
	return peers, nil
}

// SetStatus updates a peer's pairing status.
func (s PeerStore) SetStatus(fp, status string) error {
	return s.DB.Model(&db.FederationPeer{}).Where("fingerprint = ?", fp).
		Update("status", status).Error
}

// SetPolicies updates only a peer's share/consume policy JSON. Targeted update
// (not Upsert) so the rest of the peer row — identity, status, health — is
// untouched.
func (s PeerStore) SetPolicies(fp, sharePolicy, consumePolicy string) error {
	return s.DB.Model(&db.FederationPeer{}).Where("fingerprint = ?", fp).
		Updates(map[string]interface{}{
			"share_policy":   sharePolicy,
			"consume_policy": consumePolicy,
		}).Error
}

// Remove hard-deletes a peer (revocation). After this, signed requests from the
// peer no longer verify.
//
// The hard delete (Unscoped) is load-bearing: FederationPeer has a soft-delete
// column, and the unique index is on `fingerprint` alone. A soft delete would
// leave a row that (a) blocks re-pairing via Upsert's OnConflict and (b) could
// be silently resurrected as active. Do not switch this to a soft delete.
func (s PeerStore) Remove(fp string) error {
	return s.DB.Unscoped().Where("fingerprint = ?", fp).Delete(&db.FederationPeer{}).Error
}
