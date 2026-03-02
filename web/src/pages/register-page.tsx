import { useState, type FormEvent } from "react";
import { Link, useNavigate, Navigate } from "react-router-dom";
import { Button, Input } from "@/components/ui";
import { AuthFormLayout } from "@/components/auth-form-layout";
import { useAuth } from "@/hooks/use-auth";
import { validatePasswords } from "@/lib/password-validation";

export function RegisterPage() {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [pendingApproval, setPendingApproval] = useState(false);
  const { register, needsSetup } = useAuth();
  const navigate = useNavigate();

  if (needsSetup) return <Navigate to="/setup" replace />;

  if (pendingApproval) {
    return (
      <AuthFormLayout
        title="Account pending approval"
        subtitle="Your registration was received"
        footer={
          <p className="text-center text-sm text-surface-400">
            <Link
              to="/login"
              className="text-brand-400 hover:text-brand-300 font-medium transition-colors"
            >
              Back to sign in
            </Link>
          </p>
        }
      >
        <p className="text-sm text-surface-300 text-center">
          An admin needs to approve your account before you can sign in. You
          will be able to log in once your account has been activated.
        </p>
      </AuthFormLayout>
    );
  }

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
      const result = await register(username, email, password);
      if (result.pending) {
        setPendingApproval(true);
      } else {
        navigate("/");
      }
    } catch (err) {
      setError(
        err instanceof Error && err.message
          ? err.message
          : "Registration failed",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFormLayout
      title="Create account"
      subtitle="Get started with Spela"
      error={error}
      footer={
        <p className="text-center text-sm text-surface-400">
          Already have an account?{" "}
          <Link
            to="/login"
            className="text-brand-400 hover:text-brand-300 font-medium transition-colors"
          >
            Sign in
          </Link>
        </p>
      }
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
          Create account
        </Button>
      </form>
    </AuthFormLayout>
  );
}
