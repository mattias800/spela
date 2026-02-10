import { useState, type FormEvent } from "react";
import { Link, useNavigate, Navigate } from "react-router-dom";
import { Button, Input } from "@/components/ui";
import { AuthFormLayout } from "@/components/auth-form-layout";
import { useAuth } from "@/hooks/use-auth";

export function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const { login, needsSetup, registrationEnabled } = useAuth();
  const navigate = useNavigate();

  if (needsSetup) return <Navigate to="/setup" replace />;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(username, password);
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFormLayout
      title="Welcome back"
      subtitle="Sign in to your Spela account"
      error={error}
      footer={
        registrationEnabled ? (
          <p className="text-center text-sm text-surface-400">
            Don&apos;t have an account?{" "}
            <Link
              to="/register"
              className="text-brand-400 hover:text-brand-300 font-medium transition-colors"
            >
              Create one
            </Link>
          </p>
        ) : undefined
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        <Input
          id="username"
          label="Username"
          placeholder="Enter your username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <Input
          id="password"
          label="Password"
          type="password"
          placeholder="Enter your password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />

        <Button
          type="submit"
          size="lg"
          loading={loading}
          className="w-full"
        >
          Sign in
        </Button>
      </form>
    </AuthFormLayout>
  );
}
