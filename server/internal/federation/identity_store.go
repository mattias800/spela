package federation

import (
	"crypto/ed25519"
	"encoding/base64"
	"errors"
	"fmt"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

const (
	settingKeyPrivateKey = "federation_identity_private"
	settingKeyPublicKey  = "federation_identity_public"
)

// LoadOrCreateIdentity returns the server's persisted federation identity,
// generating and storing a new one on first call. The private key is encrypted
// at rest with aesKey (the same key used for other secret settings).
func LoadOrCreateIdentity(database *gorm.DB, aesKey []byte) (Identity, error) {
	privEnc, err := readSetting(database, settingKeyPrivateKey)
	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		return Identity{}, fmt.Errorf("reading stored identity: %w", err)
	}
	if errors.Is(err, gorm.ErrRecordNotFound) || privEnc == "" {
		return createAndStoreIdentity(database, aesKey)
	}

	privB64, err := auth.Decrypt(privEnc, aesKey)
	if err != nil {
		return Identity{}, fmt.Errorf("decrypting identity private key: %w", err)
	}
	privBytes, err := base64.StdEncoding.DecodeString(privB64)
	if err != nil {
		return Identity{}, fmt.Errorf("decoding identity private key: %w", err)
	}
	if len(privBytes) != ed25519.PrivateKeySize {
		return Identity{}, fmt.Errorf("stored private key has wrong size %d", len(privBytes))
	}
	priv := ed25519.PrivateKey(privBytes)
	return Identity{PrivateKey: priv, PublicKey: priv.Public().(ed25519.PublicKey)}, nil
}

func createAndStoreIdentity(database *gorm.DB, aesKey []byte) (Identity, error) {
	id, err := GenerateIdentity()
	if err != nil {
		return Identity{}, err
	}
	privB64 := base64.StdEncoding.EncodeToString(id.PrivateKey)
	privEnc, err := auth.Encrypt(privB64, aesKey)
	if err != nil {
		return Identity{}, fmt.Errorf("encrypting identity private key: %w", err)
	}
	pubB64 := base64.StdEncoding.EncodeToString(id.PublicKey)

	if err := writeSetting(database, settingKeyPrivateKey, privEnc); err != nil {
		return Identity{}, err
	}
	if err := writeSetting(database, settingKeyPublicKey, pubB64); err != nil {
		return Identity{}, err
	}
	return id, nil
}

func readSetting(database *gorm.DB, key string) (string, error) {
	var s db.ServerSetting
	if err := database.Where("key = ?", key).First(&s).Error; err != nil {
		return "", err
	}
	return s.Value, nil
}

func writeSetting(database *gorm.DB, key, value string) error {
	return database.Save(&db.ServerSetting{Key: key, Value: value}).Error
}
