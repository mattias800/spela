import { useState, type FormEvent } from "react";
import { useNavigate, Navigate } from "react-router-dom";
import { Button, Input } from "@/components/ui";
import { AuthFormLayout } from "@/components/auth-form-layout";
import { useAuth } from "@/hooks/use-auth";
import { validatePasswords } from "@/lib/password-validation";

export function SetupPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const { setup, needsSetup, isLoading } = useAuth();
  const navigate = useNavigate();

  if (isLoading) return null;
  if (needsSetup === false) return <Navigate to="/login" replace />;

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
      await setup(username, password);
      navigate("/");
    } catch (err) {
      setError(
        err instanceof Error && err.message ? err.message : "Setup failed",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFormLayout
      title="Welcome to Spela"
      subtitle="Create your owner account to get started"
      error={error}
    >
      <form onSubmit={handleSubmit} className="space-y-5">
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
          id="password"
          label="Password"
          type="password"
          placeholder="Create a password"
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

        <Button type="submit" size="lg" loading={loading} className="w-full">
          Create Owner Account
        </Button>
      </form>
    </AuthFormLayout>
  );
}

export default SetupPage;
