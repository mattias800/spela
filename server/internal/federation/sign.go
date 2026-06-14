package federation

import "crypto/ed25519"

// Sign signs msg with this identity's private key.
func (id Identity) Sign(msg []byte) []byte {
	return ed25519.Sign(id.PrivateKey, msg)
}

// Verify reports whether sig is a valid signature of msg under pub.
func Verify(pub ed25519.PublicKey, msg, sig []byte) bool {
	if len(pub) != ed25519.PublicKeySize {
		return false
	}
	return ed25519.Verify(pub, msg, sig)
}
