import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { api, typedApi, unwrap, ApiError } from "@/lib/api-client";
import { toUser } from "@/hooks/use-admin";
import type {
  AuthLoginResponse,
  AuthRegisterResponse,
} from "@/generated/schemas";
import type { User, AuthTokens } from "@/types/api";

interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  needsSetup: boolean | null;
  registrationEnabled: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (
    username: string,
    email: string,
    password: string,
  ) => Promise<{ pending: boolean }>;
  setup: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// Map the wire AuthLoginResponse to the view-model AuthTokens (nested user
// has its `role` field narrowed from plain `string` to the UserRole union).
function toAuthTokens(wire: AuthLoginResponse): AuthTokens {
  return {
    ...wire,
    user: toUser(wire.user),
  };
}

function errorStatus(err: unknown): number {
  if (err instanceof ApiError) return err.status;
  if (
    err !== null &&
    typeof err === "object" &&
    "status" in err &&
    typeof (err as { status: unknown }).status === "number"
  ) {
    return (err as { status: number }).status;
  }
  return 0;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [needsSetup, setNeedsSetup] = useState<boolean | null>(null);
  const [registrationEnabled, setRegistrationEnabled] = useState(true);

  useEffect(() => {
    // Fetch profile with one retry for transient failures (network blip,
    // server busy).  Only clear tokens on definitive auth failures (401/403).
    const fetchProfile = (retriesLeft: number): void => {
      unwrap(typedApi.GET("/api/user/profile"))
        .then((u) => {
          if (u) setUser(toUser(u));
          setIsLoading(false);
        })
        .catch((err: unknown) => {
          const status = errorStatus(err);
          if (status === 401 || status === 403) {
            api.clearTokens();
            setIsLoading(false);
            return;
          }
          if (retriesLeft > 0) {
            setTimeout(() => fetchProfile(retriesLeft - 1), 500);
            return;
          }
          setIsLoading(false);
        });
    };

    // Check setup status first
    unwrap(typedApi.GET("/api/auth/setup-status"))
      .then((data) => {
        if (!data) {
          setNeedsSetup(false);
          setIsLoading(false);
          return;
        }
        setNeedsSetup(data.needsSetup);
        setRegistrationEnabled(data.registrationEnabled);

        if (data.needsSetup) {
          setIsLoading(false);
          return;
        }

        // Only check token if setup is complete
        const token = api.getAccessToken();
        if (!token) {
          setIsLoading(false);
          return;
        }
        fetchProfile(1);
      })
      .catch(() => {
        setNeedsSetup(false);
        setIsLoading(false);
      });
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const wire = await unwrap(
      typedApi.POST("/api/auth/login", { body: { username, password } }),
    );
    if (!wire) throw new ApiError(500, "Login returned no body");
    const tokens = toAuthTokens(wire);
    api.setTokens(tokens.accessToken, tokens.refreshToken);
    setUser(tokens.user);
  }, []);

  const register = useCallback(
    async (
      username: string,
      email: string,
      password: string,
    ): Promise<{ pending: boolean }> => {
      const wire: AuthRegisterResponse | undefined = await unwrap(
        typedApi.POST("/api/auth/register", {
          body: { username, email, password },
        }),
      );
      if (!wire) throw new ApiError(500, "Registration returned no body");
      if (wire.pending) {
        return { pending: true };
      }
      // AuthRegisterResponse shape matches AuthLoginResponse when not pending.
      const tokens: AuthTokens = {
        accessToken: wire.accessToken,
        refreshToken: wire.refreshToken,
        user: toUser(wire.user),
      };
      api.setTokens(tokens.accessToken, tokens.refreshToken);
      setUser(tokens.user);
      return { pending: false };
    },
    [],
  );

  const setup = useCallback(
    async (username: string, email: string, password: string) => {
      const wire = await unwrap(
        typedApi.POST("/api/auth/setup", {
          body: { username, email, password },
        }),
      );
      if (!wire) throw new ApiError(500, "Setup returned no body");
      const tokens = toAuthTokens(wire);
      api.setTokens(tokens.accessToken, tokens.refreshToken);
      setUser(tokens.user);
      setNeedsSetup(false);
    },
    [],
  );

  const logout = useCallback(() => {
    api.clearTokens();
    setUser(null);
  }, []);

  return (
    <AuthContext
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        isAdmin: user?.role === "admin" || user?.role === "owner",
        needsSetup,
        registrationEnabled,
        login,
        register,
        setup,
        logout,
      }}
    >
      {children}
    </AuthContext>
  );
}
