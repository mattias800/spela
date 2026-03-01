import { useState, type FormEvent } from "react";
import { Button, Input } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";
import { validatePasswords } from "@/lib/password-validation";

interface CreateAccountStepProps {
  onNext: () => void;
}

export function CreateAccountStep({ onNext }: CreateAccountStepProps) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const { setup } = useAuth();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");

    const validationError = validatePasswords(password, confirmPassword);
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    try {
      await setup(username, email, password);
      onNext();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Setup failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-surface-100">
          Create Owner Account
        </h2>
        <p className="mt-1 text-sm text-surface-400">
          This account will be the server administrator with full access.
        </p>
      </div>

      {error && (
        <div className="rounded-xl bg-danger-500/10 border border-danger-500/30 px-4 py-3 text-sm text-danger-500">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          id="username"
          label="Username"
          placeholder="Choose a username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />
        <Input
          id="email"
          label="Email"
          type="email"
          placeholder="Enter your email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          required
        />
        <Input
          id="password"
          label="Password"
          type="password"
          placeholder="Create a password (min. 8 characters)"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          required
        />
        <Input
          id="confirmPassword"
          label="Confirm Password"
          type="password"
          placeholder="Confirm your password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
          required
        />

        <div className="flex justify-end pt-2">
          <Button type="submit" loading={loading}>
            Create Account & Continue
          </Button>
        </div>
      </form>
    </div>
  );
}
