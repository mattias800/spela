export function validatePasswords(password: string, confirmPassword: string): string | null {
  if (password !== confirmPassword) {
    return "Passwords do not match";
  }
  if (password.length < 8) {
    return "Password must be at least 8 characters";
  }
  return null;
}
